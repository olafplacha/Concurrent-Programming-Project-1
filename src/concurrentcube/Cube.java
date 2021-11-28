package concurrentcube;

import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

public class Cube {
    private final int[][][] cubeSquares;
    private final int size;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;
    private static final int NUM_SIDES = 6;
    private static final int NUM_PLANES = 3;

    // variables used for synchronization
    // for every plane and for every depth within the plane, there is one semaphore
    private final Semaphore[][] modificationSemaphores;
    // semaphore used for stopping reading threads from entering critical section when they are not allowed to
    private final Semaphore readingSemaphore;
    // for every plane there is a counter of modifying threads which are waiting for the access to the critical section
    private final int[] waitingModifiersCounterPerPlane;
    // for every depth within each plane there is a counter of modifying threads which are waiting to access the critical section
    private  final int[][] waitingModifiersCounterPerDepth;
    // counts the number of modifying threads which are in the critical section
    private int activeModifiersCounter;
    // counts the number of reading threads that are waiting for the access to the critical section
    private int waitingReadersCounter;
    // counts the number of reading threads that are in the cricital section
    private int activeReadersCounter;
    // guards access to shared variables
    private final Semaphore lock;
    // number of plane of which modifying threads were last let in to the critical section. This variable helps to
    // mitigate the problem of starvation
    private int lastModifyingGroupNumber = -1;

    public Cube(int size, BiConsumer<Integer, Integer> beforeRotation, BiConsumer<Integer, Integer> afterRotation, Runnable beforeShowing, Runnable afterShowing) {
        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;

        // initializing cube
        this.size = size;
        this.cubeSquares = new int[NUM_SIDES][size][size];

        for (int i = 0; i < NUM_SIDES; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    cubeSquares[i][j][k] = i;
                }
            }
        }

        // initializing variables for synchronization
        modificationSemaphores = new Semaphore[NUM_PLANES][size];
        for (int i = 0; i < NUM_PLANES; i++) {
            for (int j = 0; j < size; j++) {
                modificationSemaphores[i][j] = new Semaphore(0, true);
            }
        }
        readingSemaphore = new Semaphore(0, true);
        waitingModifiersCounterPerPlane = new int[]{0, 0, 0};
        waitingModifiersCounterPerDepth = new int[3][size];
        activeModifiersCounter = 0;
        waitingReadersCounter = 0;
        activeReadersCounter = 0;
        lock = new Semaphore(1, true);
    }

    public int getSize() {
        return size;
    }

    public static int getNumSides() { return NUM_SIDES; }

    public String show() throws InterruptedException {

        entrySectionShow();
        String cubeRepresentation = criticalSectionShow();
        exitSectionShow();

        return cubeRepresentation;
    }

    public void rotate(int side, int layer) throws InterruptedException {
        // map rotation to plane and depth, which will be used for synchronization
        Pair<Integer> plane = getRotationToPairMapping(side, layer);

        entrySectionRotate(plane.first(), plane.second());
        criticalSectionRotate(side, layer);
        exitSectionRotate();
    }

    private void entrySectionShow() throws InterruptedException {
        // if the thread is interrupted when waiting for the lock, then no clean up is needed
        lock.acquire();
        // if there is any modifying thread that is waiting to enter the critical section, or that is working, then the
        // reading thread must wait
        if (activeModifiersCounter > 0 || isAnyModifierWaiting()) {
            waitingReadersCounter++;
            lock.release();
            // wait on a semaphore
            readingSemaphore.acquireUninterruptibly();
            // at this point the reading thread is waken up, ready to perform reading
            waitingReadersCounter--;
        }
        activeReadersCounter++;
        // modifying thread wakes up one reading thread. If there are more than 1 waiting reading threads, they are
        // waken by other reading threads in a cascading manner
        if (waitingReadersCounter > 0) {
            readingSemaphore.release();
        }
        else {
            // no more reading threads are waiting, give back the lock
            lock.release();
        }
        // If current thread was interrupted, then do a clean up and throw an exception.
        if (Thread.currentThread().isInterrupted()) {
            exitSectionShow();
            throw new InterruptedException();
        }
    }

    private String criticalSectionShow() {
        beforeShowing.run();
        String cubeRepresentation = getCubeRepresentation();
        afterShowing.run();
        return cubeRepresentation;
    }

    private void exitSectionShow() {
        // if current thread is interrupted, then it must ignore the interruption in this section, since it must clean
        // up after its execution
        lock.acquireUninterruptibly();
        activeReadersCounter--;
        // if this is the last reading thread to exit the critical section and there is any waiting modifying thread,
        // then wake up the modifying thread (selected with fairness)
        if (activeReadersCounter == 0 && isAnyModifierWaiting()) {
            int modifiersGroupToWakeUp = updateAndGetLastModifyingGroupNumber();
            // at this point we know that there is at least 1 modifying thread from modifiersGroupToWakeUp group that
            // is waiting to enter the critical section. This thread inherits the lock
            wakeUpThreadFromGivenGroup(modifiersGroupToWakeUp, 0);
        }
        else {
            lock.release();
        }
    }

    private void entrySectionRotate(int planeNumber, int depth) throws InterruptedException {
        // if the thread is interrupted when waiting for the lock, then no clean up is needed
        lock.acquire();
        // if there is any active reading or modifying thread, then the thread must wait on a group semaphore
        if (activeReadersCounter + activeModifiersCounter > 0) {
            waitingModifiersCounterPerPlane[planeNumber]++;
            waitingModifiersCounterPerDepth[planeNumber][depth]++;
            lock.release();
            // wait on the group semaphore (on proper depth)
            modificationSemaphores[planeNumber][depth].acquireUninterruptibly();
            // at this point the modifying thread was waken up
            waitingModifiersCounterPerPlane[planeNumber]--;
            waitingModifiersCounterPerDepth[planeNumber][depth]--;
            activeModifiersCounter++;
            // try to wake up some thread from the same group, but with greater depth
            boolean hasWakenGroupThread = wakeUpThreadFromGivenGroup(planeNumber, depth + 1);
            if (!hasWakenGroupThread) {
                // current thread was the last one to be waken up, give back the lock
                lock.release();
            }
        }
        else {
            // no other thread is performing any operation on the cube, safe to enter the critical section
            activeModifiersCounter++;
            lock.release();
        }
        // If current thread was interrupted, then do a clean up and throw an exception.
        if (Thread.currentThread().isInterrupted()) {
            exitSectionRotate();
            throw new InterruptedException();
        }
    }

    private void criticalSectionRotate(int side, int layer) {
        beforeRotation.accept(side, layer);
        performRotation(side, layer);
        afterRotation.accept(side, layer);
    }

    private void exitSectionRotate() {
        // if current thread is interrupted, then it must ignore the interruption in this section, since it must clean
        // up after its execution
        lock.acquireUninterruptibly();
        activeModifiersCounter--;
        if (activeModifiersCounter == 0)
        {
            // current thread is the last modifying thread from its group that is leaving the critical section. If any
            // reading thread is waiting, then wake it up (other reading threads will be waken up is a cascading manner)
            if (waitingReadersCounter > 0) {
                readingSemaphore.release(); // the lock is inherited
            }
            else if (isAnyModifierWaiting()) {
                int modifiersGroupToWakeUp = updateAndGetLastModifyingGroupNumber();
                // at this point we know that there is at least 1 modifying thread from modifiersGroupToWakeUp group that
                // is waiting to enter the critical section. This thread inherits the lock
                wakeUpThreadFromGivenGroup(modifiersGroupToWakeUp, 0);
            }
            else {
                // no thread wants to enter the critical section
                lock.release();
            }
        }
        else {
            lock.release();
        }
    }

    private boolean wakeUpThreadFromGivenGroup(int groupNumber, int start) {
        // this functions scans modifying threads waiting on the group semaphore and wakes up the first thread, whose
        // depth (layer which the thread wants to modify) is equal to or greater than start parameter.
        int currentDepth = start;
        while (currentDepth < size && waitingModifiersCounterPerDepth[groupNumber][currentDepth] == 0) {
            currentDepth++;
        }
        // if the loop terminated because no other modifying threads were waiting at the semaphores, do nothing.
        // Otherwise wake up another waiting modifying thread
        if (currentDepth < size) {
            modificationSemaphores[groupNumber][currentDepth].release();
            // return true as the method has waken up some thread
            return true;
        }
        // return false as the method hasn't waken anyone up
        return false;
    }

    private int updateAndGetLastModifyingGroupNumber() {
        // when this function is invoked, it is guaranteed that at least 1 modifying thread is waiting to enter the
        // critical section
        lastModifyingGroupNumber = (lastModifyingGroupNumber + 1) % waitingModifiersCounterPerPlane.length;
        // iterate until the first waiting group is found
        while (waitingModifiersCounterPerPlane[lastModifyingGroupNumber] == 0) {
            lastModifyingGroupNumber = (lastModifyingGroupNumber + 1) % waitingModifiersCounterPerPlane.length;
        }
        return lastModifyingGroupNumber;
    }

    private boolean isAnyModifierWaiting() {
        int numActive = 0;
        for (int i = 0; i < this.waitingModifiersCounterPerPlane.length; i++) {
            numActive += this.waitingModifiersCounterPerPlane[i];
        }
        return numActive > 0;
    }

    /**
     * Rotations along two different sides may be modifying the cube in the same plane. The function maps side and layer
     * into plane number as long as depth within the plane.
     *
     * @param side side of the cube along which the rotation is performed
     * @param layer layer (depth) of the cube from the given side, along which the rotation is performed
     * @return
     */
    private Pair<Integer> getRotationToPairMapping(int side, int layer) {
        int plane;
        int depth;

        switch (side) {
            case 0:
                plane = 0;
                depth = layer;
                break;
            case 1:
                plane = 1;
                depth = layer;
                break;
            case 2:
                plane = 2;
                depth = layer;
                break;
            case 3:
                plane = 1;
                depth = this.size - layer - 1;
                break;
            case 4:
                plane = 2;
                depth = this.size - layer - 1;
                break;
            default:
                plane = 0;
                depth = this.size - layer - 1;
                break;
        }
        return new Pair<>(plane, depth);
    }

    /**
     * @return String representation of the cube's state
     */
    private String getCubeRepresentation() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < NUM_SIDES; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    builder.append(cubeSquares[i][j][k]);
                }
            }
        }
        return builder.toString();
    }

    /**
     * Performs rotation within given ring.
     *
     * @param ring ring along which rotation is performed
     */
    private void performRingRotation(RingSquaresCollection ring) {
        // perform swaps for the ring
        for (int i = 0; i < ring.getComponentSize(); i++) {
            // get coordinates of the first square to swap
            Coordinate currCoord = ring.calculateCoordinate(0, i);
            int currColor = cubeSquares[currCoord.getSideIdx()][currCoord.getRowIdx()][currCoord.getColumnIdx()];
            for (int j = 0; j < RingSquaresCollection.NUM_RING_COMPONENTS; j++) {
                // get coordinates of the next square to swap
                Coordinate nextCoord = ring.calculateCoordinate((j + 1) % RingSquaresCollection.NUM_RING_COMPONENTS, i);
                int nextColor = cubeSquares[nextCoord.getSideIdx()][nextCoord.getRowIdx()][nextCoord.getColumnIdx()];
                // perform the swap
                cubeSquares[nextCoord.getSideIdx()][nextCoord.getRowIdx()][nextCoord.getColumnIdx()] = currColor;
                currColor = nextColor;
            }
        }
    }

    /**
     * Performs one plane (one side) rotation using rings.
     *
     * @param side side of the cube
     */
    private void performSideRotation(int side) {
        // the most outer layer's ring components contain (size - 1) elements
        int ringComponentSize = size - 1;
        // denotes how next ring component's element can be derived from the previous one
        int[] ringRowShifts = new int[]{0, 1, 0, -1};
        int[] ringColShifts = new int[]{1, 0, -1, 0};
        // denotes the first elements in each ring's component
        int[] currentRows = new int[]{0, 0, size - 1, size - 1};
        int[] currentCols = new int[]{0, size - 1, size - 1, 0};
        // denotes how the next layer ring first component can be derived from the previous one
        int[] entryRowShifts = new int[]{1, 1, -1, -1};
        int[] entryColshifts = new int[]{1, -1, -1, 1};

        int layer = 0;
        while (ringComponentSize > 0) {
            Coordinate[] coords = new Coordinate[4];
            for (int i = 0; i < RingSquaresCollection.NUM_RING_COMPONENTS; i++) {
                int rowIdx = currentRows[i] + layer * entryRowShifts[i];
                int colIdx = currentCols[i] + layer * entryColshifts[i];
                coords[i] = new Coordinate(side, rowIdx, colIdx);
            }
            // create a ring and rotate the ring
            RingSquaresCollection ring = new RingSquaresCollection(coords, ringRowShifts, ringColShifts, ringComponentSize);
            performRingRotation(ring);
            // next layer's ring components contain 2 elements less than previuos layer's ring components
            ringComponentSize -= 2;
            layer++;
        }
    }

    /**
     * Performs the rotation along given side and layer
     *
     * @param side  side of the cube along which the rotation is performed
     * @param layer layer (depth) of the cube from the given side, along which the rotation is performed
     */
    private void performRotation(int side, int layer) {
        // find the ring of the outer rotation
        RingSquaresCollection ring = determineRing(side, layer);
        performRingRotation(ring);

        // if the rotation layer is the first or the last one, side of the cube must also be rotated
        if (layer == 0) {
            performSideRotation(side);
        } else if (layer == this.size - 1) {
            // 3 clockwise operations are equivalent to 1 counterclockwise
            int oppositeSide = getOppositeSide(side);
            performSideRotation(oppositeSide);
            performSideRotation(oppositeSide);
            performSideRotation(oppositeSide);
        }
    }

    /**
     * Returns the number of the opposite side
     *
     * @param side side of the cube, in range <0, 5>
     * @return number of the opposite side
     */
    private int getOppositeSide(int side) {
        switch (side) {
            case 0:
                return 5;
            case 1:
                return 3;
            case 2:
                return 4;
            case 3:
                return 1;
            case 4:
                return 2;
            default:
                return 0;
        }
    }

    /**
     * Pair of side and layer determine a few blocks of squares, which are called a ring. The function
     * determines such a ring.
     *
     * @param side  id of side, in range <0, 5>
     * @param layer id of layer, which determines the depth of the ring, in range <0, size-1>
     * @return RingSquaresCollection which helps iterating over the ring
     */
    private RingSquaresCollection determineRing(int side, int layer) {
        int[] sides, initialRows, initialCols, rowShifts, colShifts;

        switch (side) {
            case 0:
                sides = new int[]{4, 3, 2, 1};
                initialRows = new int[]{layer, layer, layer, layer};
                initialCols = new int[]{size - 1, size - 1, size - 1, size - 1};
                rowShifts = new int[]{0, 0, 0, 0};
                colShifts = new int[]{-1, -1, -1, -1};
                break;
            case 1:
                sides = new int[]{0, 2, 5, 4};
                initialRows = new int[]{0, 0, 0, size - 1};
                initialCols = new int[]{layer, layer, layer, size - layer - 1};
                rowShifts = new int[]{1, 1, 1, -1};
                colShifts = new int[]{0, 0, 0, 0};
                break;
            case 2:
                sides = new int[]{0, 3, 5, 1};
                initialRows = new int[]{size - layer - 1, 0, layer, size - 1};
                initialCols = new int[]{0, layer, size - 1, size - layer - 1};
                rowShifts = new int[]{0, 1, 0, -1};
                colShifts = new int[]{1, 0, -1, 0};
                break;
            case 3:
                sides = new int[]{0, 4, 5, 2};
                initialRows = new int[]{size - 1, 0, size - 1, size - 1};
                initialCols = new int[]{size - layer - 1, layer, size - layer - 1, size - layer - 1};
                rowShifts = new int[]{-1, 1, -1, -1};
                colShifts = new int[]{0, 0, 0, 0};
                break;
            case 4:
                sides = new int[]{0, 1, 5, 3};
                initialRows = new int[]{layer, 0, size - layer - 1, size - 1};
                initialCols = new int[]{size - 1, layer, 0, size - layer - 1};
                rowShifts = new int[]{0, 1, 0, -1};
                colShifts = new int[]{-1, 0, 1, 0};
                break;
            default:
                sides = new int[]{2, 3, 4, 1};
                initialRows = new int[]{size - layer - 1, size - layer - 1, size - layer - 1, size - layer - 1};
                initialCols = new int[]{0, 0, 0, 0};
                rowShifts = new int[]{0, 0, 0, 0};
                colShifts = new int[]{1, 1, 1, 1};
                break;
        }
        Coordinate[] coords = new Coordinate[4];
        for (int i = 0; i < RingSquaresCollection.NUM_RING_COMPONENTS; i++) {
            coords[i] = new Coordinate(sides[i], initialRows[i], initialCols[i]);
        }
        return new RingSquaresCollection(coords, rowShifts, colShifts, size);
    }

    /**
     * Pair of side and layer determine 4 blocks of squares - the ring. This class helps iterating over the ring.
     */
    private static class RingSquaresCollection {
        static final int NUM_RING_COMPONENTS = 4;
        private final Coordinate[] coords;
        private final int[] rowShifts;
        private final int[] colShifts;
        private final int componentSize;

        public RingSquaresCollection(Coordinate[] coords, int[] rowShifts, int[] colShifts, int componentSize) {
            this.coords = coords;
            this.rowShifts = rowShifts;
            this.colShifts = colShifts;
            this.componentSize = componentSize;
        }

        public Coordinate calculateCoordinate(int pos, int stepNum) {
            int newRowIdx = coords[pos].getRowIdx() + stepNum * rowShifts[pos];
            int newColIdx = coords[pos].getColumnIdx() + stepNum * colShifts[pos];
            return new Coordinate(coords[pos].sideIdx, newRowIdx, newColIdx);
        }

        public int getComponentSize() {
            return this.componentSize;
        }
    }

    /**
     * Represents a position of a square in a cube
     */
    private static class Coordinate {
        private final int sideIdx;
        private final int rowIdx;
        private final int columnIdx;

        public Coordinate(int sideIdx, int rowIdx, int columnIdx) {
            this.sideIdx = sideIdx;
            this.rowIdx = rowIdx;
            this.columnIdx = columnIdx;
        }

        public int getSideIdx() {
            return sideIdx;
        }

        public int getRowIdx() {
            return rowIdx;
        }

        public int getColumnIdx() {
            return columnIdx;
        }
    }

    // to be deleted!
    public void rotate(String rot) throws InterruptedException {
        int faceno = 0;
        int layer = 0;

        switch (rot.charAt(0)) {
            case 'F': faceno = 2; break;
            case 'B': faceno = 4; break;
            case 'U': faceno = 0; break;
            case 'D': faceno = 5; break;
            case 'L': faceno = 1; break;
            case 'R': faceno = 3; break;
            case 'M': faceno = 1; layer = 1; break;
            case 'E': faceno = 5; layer = 1; break;
            case 'S': faceno = 2; layer = 1; break;
            default: { System.err.println("baaaad ;("); break; }
        }

        if (rot.length() > 1 && rot.charAt(1) == '\'') {
            // System.out.println("prim detected: " + rot);
            faceno = new int[] {5, 3, 4, 1, 2, 0}[faceno];
            layer = size - 1 - layer;
        }

        rotate(faceno, layer);
    }

    // to be deleted!
    public int[] count() {
        int[] res = new int[6];
        for (int i = 0; i < 6; ++i) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    res[cubeSquares[i][j][k]]++;
                }
            }
        }
        return res;
    }
}
