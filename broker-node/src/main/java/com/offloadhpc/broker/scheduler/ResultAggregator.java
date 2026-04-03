package com.offloadhpc.broker.scheduler;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Collects partial results from workers and assembles the final answer.
 */
public class ResultAggregator {

    /**
     * Merge MatMul row slices into a full result matrix.
     *
     * @param matrixSize total N (rows and cols of the result)
     * @param slices     array of {startRow, double[][] slice} collected from
     *                   workers
     * @return the full N×N result matrix C
     */
    public static double[][] mergeMatMul(int matrixSize, MatMulSlice[] slices) {
        double[][] result = new double[matrixSize][matrixSize];
        for (MatMulSlice slice : slices) {
            for (int i = 0; i < slice.data.length; i++) {
                result[slice.startRow + i] = slice.data[i];
            }
        }
        return result;
    }

    /**
     * Convert a double[][] matrix to a JSONArray of JSONArrays.
     */
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

    /**
     * Aggregate Hash Crack results — return the first non-null cracked value.
     *
     * @param results array of String results from workers (null = not found)
     * @return the cracked plaintext, or null if none found
     */
    public static String aggregateHashCrack(String[] results) {
        for (String r : results) {
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    /**
     * Helper class to hold a row slice result.
     */
    public static class MatMulSlice {
        public final int startRow;
        public final double[][] data;

        public MatMulSlice(int startRow, double[][] data) {
            this.startRow = startRow;
            this.data = data;
        }
    }
}
