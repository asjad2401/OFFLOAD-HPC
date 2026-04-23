package com.offloadhpc.worker.compute;

/**
 * MatMulEngine — computes a row slice of matrix multiplication C = A × B.
 *
 * Stateless: all inputs as parameters, returns the partial result.
 */
public class MatMulEngine {

    /**
     * Compute rows [startRow, endRow) of C = A × B.
     *
     * @param A        full matrix A (N × K)
     * @param B        full matrix B (K × M)
     * @param startRow first row to compute (inclusive)
     * @param endRow   last row to compute (exclusive)
     * @return partial result matrix with (endRow - startRow) rows and M columns
     */
    public static double[][] compute(double[][] A, double[][] B, int startRow, int endRow) {
        int K = A[0].length; // columns of A = rows of B
        int M = B[0].length; // columns of B = columns of result
        int resultRows = endRow - startRow;

        double[][] C = new double[resultRows][M];

        for (int i = 0; i < resultRows; i++) {
            for (int j = 0; j < M; j++) {
                double sum = 0.0;
                for (int k = 0; k < K; k++) {
                    sum += A[startRow + i][k] * B[k][j];
                }
                C[i][j] = sum;
            }
        }

        return C;
    }
}
