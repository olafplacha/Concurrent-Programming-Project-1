package utils;

/**
 * Pair of side and layer determine 4 blocks of squares - the ring. This class helps iterating over the ring.
 */
public class RingSquaresCollection {
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
        return new Coordinate(coords[pos].getSideIdx(), newRowIdx, newColIdx);
    }

    public int getComponentSize() {
        return this.componentSize;
    }

    public static int getNumberOfRingComponents() {
        return NUM_RING_COMPONENTS;
    }
}