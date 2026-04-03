package com.offloadhpc.broker.model;

/**
 * Sub-task representing a row-range slice of a MatMul job.
 */
public class MatMulSubTask extends SubTask {

    private final double[][] matrixA;
    private final double[][] matrixB;
    private final int startRow;
    private final int endRow;

    public MatMulSubTask(String subTaskId, double[][] matrixA, double[][] matrixB,
            int startRow, int endRow) {
        super(subTaskId);
        this.matrixA = matrixA;
        this.matrixB = matrixB;
        this.startRow = startRow;
        this.endRow = endRow;
    }

    public double[][] getMatrixA() {
        return matrixA;
    }

    public double[][] getMatrixB() {
        return matrixB;
    }

    public int getStartRow() {
        return startRow;
    }

    public int getEndRow() {
        return endRow;
    }
}
