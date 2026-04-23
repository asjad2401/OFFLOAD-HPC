package com.offloadhpc.broker.scheduler;

import org.json.JSONArray;

/**
 * Collects partial results from workers and assembles the final answer.
 * v2.0 — extended with Image Processing and K-Means aggregation.
 */
public class ResultAggregator {

    // ── MatMul ──────────────────────────────────────────────────────

    public static double[][] mergeMatMul(int matrixSize, MatMulSlice[] slices) {
        double[][] result = new double[matrixSize][matrixSize];
        for (MatMulSlice slice : slices) {
            for (int i = 0; i < slice.data.length; i++) {
                result[slice.startRow + i] = slice.data[i];
            }
        }
        return result;
    }

    public static JSONArray matrixToJson(double[][] matrix) {
        JSONArray outer = new JSONArray();
        for (double[] row : matrix) {
            JSONArray jsonRow = new JSONArray();
            for (double val : row) {
                jsonRow.put(val);
            }
            outer.put(jsonRow);
        }
        return outer;
    }

    public static class MatMulSlice {
        public final int startRow;
        public final double[][] data;

        public MatMulSlice(int startRow, double[][] data) {
            this.startRow = startRow;
            this.data = data;
        }
    }

    // ── Hash Crack ──────────────────────────────────────────────────

    public static String aggregateHashCrack(String[] results) {
        for (String r : results) {
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    // ── Image Processing ────────────────────────────────────────────

    /**
     * Merge processed pixel strips into a full image.
     */
    public static int[] mergeImageProc(int width, int height, ImageProcSlice[] slices) {
        int[] result = new int[width * height];
        for (ImageProcSlice slice : slices) {
            int stripLen = slice.data.length;
            System.arraycopy(slice.data, 0, result, slice.startRow * width, stripLen);
        }
        return result;
    }

    public static class ImageProcSlice {
        public final int startRow;
        public final int[] data;

        public ImageProcSlice(int startRow, int[] data) {
            this.startRow = startRow;
            this.data = data;
        }
    }

    // ── K-Means ─────────────────────────────────────────────────────

    /**
     * Merge partial K-Means results from all workers.
     * Each partial result is K x (dims+1): [sum_dim0, ..., sum_dimD, count]
     * Returns the aggregated K x (dims+1) array.
     */
    public static double[][] aggregateKMeansPartial(double[][][] partials) {
        int K = partials[0].length;
        int colsPerCluster = partials[0][0].length; // dims + 1

        double[][] aggregated = new double[K][colsPerCluster];
        for (double[][] partial : partials) {
            for (int k = 0; k < K; k++) {
                for (int c = 0; c < colsPerCluster; c++) {
                    aggregated[k][c] += partial[k][c];
                }
            }
        }
        return aggregated;
    }

    /**
     * Convert a flat int[] pixel array to a JSONArray.
     */
    public static JSONArray pixelArrayToJson(int[] pixels) {
        JSONArray arr = new JSONArray();
        for (int p : pixels) {
            arr.put(p);
        }
        return arr;
    }
}
