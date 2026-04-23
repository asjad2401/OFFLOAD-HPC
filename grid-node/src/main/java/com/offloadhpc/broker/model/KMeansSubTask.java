package com.offloadhpc.broker.model;

/**
 * Sub-task for K-Means Clustering jobs.
 * Carries full data points + centroids, plus the index range this worker
 * processes.
 */
public class KMeansSubTask extends SubTask {

    private final double[][] dataPoints;
    private final double[][] centroids;
    private final int startIdx;
    private final int endIdx;

    public KMeansSubTask(String subTaskId, double[][] dataPoints, double[][] centroids,
            int startIdx, int endIdx) {
        super(subTaskId);
        this.dataPoints = dataPoints;
        this.centroids = centroids;
        this.startIdx = startIdx;
        this.endIdx = endIdx;
    }

    public double[][] getDataPoints() {
        return dataPoints;
    }

    public double[][] getCentroids() {
        return centroids;
    }

    public int getStartIdx() {
        return startIdx;
    }

    public int getEndIdx() {
        return endIdx;
    }
}
