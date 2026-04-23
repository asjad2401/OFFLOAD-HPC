package com.offloadhpc.worker.compute;

/**
 * KMeansEngine — computes a partial K-Means iteration on a data slice.
 *
 * Given data points and current centroids, for each point in
 * [startIdx, endIdx), finds the nearest centroid and accumulates
 * partial sums for centroid recomputation.
 *
 * Stateless: all inputs as parameters, returns partial result.
 */
public class KMeansEngine {

    /**
     * Compute partial K-Means assignments for data points [startIdx, endIdx).
     *
     * @param dataPoints all data points, N x dims
     * @param centroids  current centroids, K x dims
     * @param startIdx   first data point to process (inclusive)
     * @param endIdx     last data point to process (exclusive)
     * @return K x (dims+1) array where each row is:
     *         [sum_dim0, sum_dim1, ..., sum_dimD, count]
     *         representing the partial centroid sums for points in this slice
     */
    public static double[][] computePartial(double[][] dataPoints, double[][] centroids,
            int startIdx, int endIdx) {
        int K = centroids.length;
        int dims = centroids[0].length;

        // result[k] = [sum_dim0, sum_dim1, ..., sum_dimD, count]
        double[][] result = new double[K][dims + 1];

        for (int i = startIdx; i < endIdx; i++) {
            // Find nearest centroid
            int nearestK = 0;
            double nearestDist = Double.MAX_VALUE;

            for (int k = 0; k < K; k++) {
                double dist = squaredEuclidean(dataPoints[i], centroids[k]);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestK = k;
                }
            }

            // Accumulate partial sum
            for (int d = 0; d < dims; d++) {
                result[nearestK][d] += dataPoints[i][d];
            }
            result[nearestK][dims] += 1.0; // count
        }

        return result;
    }

    /**
     * Compute new centroids from aggregated partial results.
     * Used by the broker after merging all worker results.
     *
     * @param aggregated   K x (dims+1) aggregated sums from all workers
     * @param oldCentroids fallback centroids if a cluster has 0 members
     * @return K x dims new centroids
     */
    public static double[][] computeNewCentroids(double[][] aggregated, double[][] oldCentroids) {
        int K = aggregated.length;
        int dims = aggregated[0].length - 1;
        double[][] newCentroids = new double[K][dims];

        for (int k = 0; k < K; k++) {
            double count = aggregated[k][dims];
            if (count > 0) {
                for (int d = 0; d < dims; d++) {
                    newCentroids[k][d] = aggregated[k][d] / count;
                }
            } else {
                // No points assigned — keep old centroid
                System.arraycopy(oldCentroids[k], 0, newCentroids[k], 0, dims);
            }
        }

        return newCentroids;
    }

    /**
     * Squared Euclidean distance between two vectors.
     */
    private static double squaredEuclidean(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }
}
