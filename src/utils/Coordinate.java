package utils;

/**
 * This is a helper class which represents coordinates within a cube.
 */
public class Coordinate {
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