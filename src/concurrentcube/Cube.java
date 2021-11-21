package concurrentcube;

import java.util.function.BiConsumer;

public class Cube {
    private int[][][] cubeSquares;
    private final int size;
    private BiConsumer<Integer, Integer> beforeRotation;
    private BiConsumer<Integer, Integer> afterRotation;
    private Runnable beforeShowing;
    private Runnable afterShowing;

    public Cube(int size,
                BiConsumer<Integer, Integer> beforeRotation,
                BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing,
                Runnable afterShowing) {
        this.cubeSquares = new int[6][size][size];
        this.size = size;

        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    cubeSquares[i][j][k] = i;
                }
            }
        }
    }

    public void rotate(int side, int layer) throws InterruptedException {
        RingSquaresCollection ring = determineRing(side, layer);

        for (int i = 0; i < size; i++) {
            Coordinate currCoord = ring.calculateCoordinate(0, i);
            int currColor = cubeSquares[currCoord.getSideIdx()][currCoord.getRowIdx()][currCoord.getColumnIdx()];
            for (int j = 0; j < 4; j++) {
                Coordinate nextCoord = ring.calculateCoordinate((j + 1) % 4, i);
                int nextColor = cubeSquares[nextCoord.getSideIdx()][nextCoord.getRowIdx()][nextCoord.getColumnIdx()];
                cubeSquares[nextCoord.getSideIdx()][nextCoord.getRowIdx()][nextCoord.getColumnIdx()] = currColor;
                currColor = nextColor;
            }
        }
    }

//    public String show() throws InterruptedException {
//    }

    public static void main(String[] args) throws InterruptedException {
        Cube c = new Cube(4, null, null, null, null);
        c.rotate(2, 0);
        c.rotate(5, 1);

        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 4; j++) {
                String a = "";
                for (int k = 0; k < 4; k++) {
                    a += c.cubeSquares[i][j][k] + " ";
                }
                System.out.println(a);
            }
            System.out.println("---");
        }
    }

    /**
     * Pair of side and layer determine 4 blocks of squares, which are called a ring. The function
     * determines such a ring.
     * @param side - id of side, in range <0, 5>
     * @param layer - id of layer, which determines the depth of the ring, in range <0, size-1>
     * @return - RingSquaresCollection which helps iterating over the ring
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
                initialCols = new int[]{size - 1, layer, size - 1, size - layer - 1};
                rowShifts = new int[]{0, 1, 0, -1};
                colShifts = new int[]{-1, 0, -1, 0};
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
        for (int i = 0; i < 4; i++) {
            coords[i] = new Coordinate(sides[i], initialRows[i], initialCols[i]);
        }
        return new RingSquaresCollection(coords, rowShifts, colShifts);
    }

    /**
     * Pair of side and layer determine 4 blocks of squares. This class helps iterating over them.
     */
    private static class RingSquaresCollection {
        private final Coordinate[] coords;
        private final int[] rowShifts;
        private final int[] colShifts;

        public RingSquaresCollection(Coordinate[] coords, int[] rowShifts, int[] colShifts) {
            this.coords = coords;
            this.rowShifts = rowShifts;
            this.colShifts = colShifts;
        }

        public Coordinate calculateCoordinate(int pos, int stepNum) {
            int newRowIdx = coords[pos].getRowIdx() + stepNum * rowShifts[pos];
            int newColIdx = coords[pos].getColumnIdx() + stepNum * colShifts[pos];
            return new Coordinate(coords[pos].sideIdx, newRowIdx, newColIdx);
        }
    }

    /**
     *  Represents a position of a square in a cube
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

        @Override
        public String toString() {
            return "Coordinate{" +
                    "sideIdx=" + sideIdx +
                    ", rowIdx=" + rowIdx +
                    ", columnIdx=" + columnIdx +
                    '}';
        }
    }
}
