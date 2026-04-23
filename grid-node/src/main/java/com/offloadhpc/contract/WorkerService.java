package com.offloadhpc.contract;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI interface for Worker Nodes in the OFFLOAD-HPC-MOBILE grid.
 * v2.0 — extended with Image Processing, K-Means, and capability reporting.
 */
public interface WorkerService extends Remote {

        /**
         * Called at worker startup to announce availability.
         */
        void register(String workerId, String brokerHost, int brokerPort)
                        throws RemoteException;

        /**
         * Execute a MatMul sub-task on rows [startRow, endRow).
         */
        double[][] executeMatMul(String subTaskId,
                        double[][] matrixA, double[][] matrixB,
                        int startRow, int endRow) throws RemoteException;

        /**
         * Execute a hash crack on keyspace indices [startIndex, endIndex).
         * Returns the cracked plaintext or null if not found in this range.
         */
        String executeHashCrack(String subTaskId,
                        String targetHash, String algorithm,
                        String charset, int maxLength,
                        long startIndex, long endIndex) throws RemoteException;

        /**
         * Execute image processing on a pixel strip [startRow, endRow).
         * Operations: GRAYSCALE, EDGE_DETECT, BLUR
         *
         * @param subTaskId unique sub-task identifier
         * @param pixelData flat ARGB pixel array for the full image
         * @param width     image width in pixels
         * @param height    image height in pixels
         * @param startRow  first row to process (inclusive)
         * @param endRow    last row to process (exclusive)
         * @param operation one of GRAYSCALE, EDGE_DETECT, BLUR
         * @return processed pixel strip as flat int array
         */
        int[] executeImageProc(String subTaskId,
                        int[] pixelData, int width, int height,
                        int startRow, int endRow, String operation) throws RemoteException;

        /**
         * Execute a partial K-Means iteration on data points [startIdx, endIdx).
         * For each point, find nearest centroid and accumulate partial sums.
         *
         * Returns a packed 2D array:
         * result[0..K-1] = partial centroid sums (each row has dims+1 values:
         * sum_dim0, sum_dim1, ..., sum_dimD, count)
         *
         * @param subTaskId  unique sub-task identifier
         * @param dataPoints all data points (N x dims)
         * @param centroids  current centroids (K x dims)
         * @param startIdx   first data point index (inclusive)
         * @param endIdx     last data point index (exclusive)
         * @return partial result: K rows of (dims+1) columns
         */
        double[][] executeKMeans(String subTaskId,
                        double[][] dataPoints, double[][] centroids,
                        int startIdx, int endIdx) throws RemoteException;

        /**
         * Return capability info as JSON string.
         * Example: {"cpuCores":4,"availableMemoryMB":2048}
         */
        String getCapabilities() throws RemoteException;

        /**
         * Health check — returns true if worker is alive.
         */
        boolean ping() throws RemoteException;
}
