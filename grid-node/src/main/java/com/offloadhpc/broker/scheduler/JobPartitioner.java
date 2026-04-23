package com.offloadhpc.broker.scheduler;

import com.offloadhpc.broker.model.HashCrackSubTask;
import com.offloadhpc.broker.model.ImageProcSubTask;
import com.offloadhpc.broker.model.KMeansSubTask;
import com.offloadhpc.broker.model.MatMulSubTask;
import com.offloadhpc.broker.model.SubTask;
import com.offloadhpc.broker.registry.NodeRegistry;
import com.offloadhpc.broker.registry.WorkerInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Takes a full job description and produces a list of SubTask objects.
 * v2.0 — weighted partitioning based on worker capabilities, plus new job
 * types.
 */
public class JobPartitioner {

    private final NodeRegistry registry;

    public JobPartitioner(NodeRegistry registry) {
        this.registry = registry;
    }

    /**
     * Partition a MatMul job into row-range sub-tasks.
     * Uses weighted distribution based on worker capabilities.
     */
    public List<SubTask> partitionMatMul(JSONObject payload) {
        int matrixSize = payload.getInt("matrixSize");
        double[][] matrixA = parseMatrix(payload.getJSONArray("matrixA"));
        double[][] matrixB = parseMatrix(payload.getJSONArray("matrixB"));

        List<WorkerInfo> workers = registry.getAvailableWorkers();
        if (workers.isEmpty()) {
            throw new IllegalStateException("No available workers to partition job");
        }

        Map<String, Double> weights = registry.getCapabilityWeights();
        List<SubTask> subTasks = new ArrayList<>();

        int assignedRows = 0;
        for (int i = 0; i < workers.size(); i++) {
            WorkerInfo w = workers.get(i);
            int rowsForWorker;
            if (i == workers.size() - 1) {
                rowsForWorker = matrixSize - assignedRows; // last worker gets remainder
            } else {
                rowsForWorker = (int) Math.round(matrixSize * weights.get(w.getWorkerId()));
                rowsForWorker = Math.max(1, rowsForWorker); // at least 1 row
            }

            int startRow = assignedRows;
            int endRow = startRow + rowsForWorker;
            endRow = Math.min(endRow, matrixSize);

            if (startRow >= matrixSize)
                break;

            String subTaskId = UUID.randomUUID().toString();
            MatMulSubTask st = new MatMulSubTask(subTaskId, matrixA, matrixB, startRow, endRow);
            st.setWorkerId(w.getWorkerId());
            subTasks.add(st);

            assignedRows = endRow;
        }

        System.out.println("[Partitioner] MatMul (" + matrixSize + "x" + matrixSize +
                ") split into " + subTasks.size() + " sub-tasks (weighted)");
        return subTasks;
    }

    /**
     * Partition a Hash Crack job into index-range sub-tasks.
     * Uses weighted distribution based on worker capabilities.
     */
    public List<SubTask> partitionHashCrack(JSONObject payload) {
        String targetHash = payload.getString("targetHash");
        String algorithm = payload.getString("algorithm");
        String charset = payload.getString("charset");
        int maxLength = payload.getInt("maxLength");

        int base = charset.length();
        long totalKeyspace = 0;
        for (int i = 1; i <= maxLength; i++) {
            totalKeyspace += (long) Math.pow(base, i);
        }

        List<WorkerInfo> workers = registry.getAvailableWorkers();
        if (workers.isEmpty()) {
            throw new IllegalStateException("No available workers to partition job");
        }

        Map<String, Double> weights = registry.getCapabilityWeights();
        List<SubTask> subTasks = new ArrayList<>();

        long assignedKeys = 0;
        for (int i = 0; i < workers.size(); i++) {
            WorkerInfo w = workers.get(i);
            long keysForWorker;
            if (i == workers.size() - 1) {
                keysForWorker = totalKeyspace - assignedKeys;
            } else {
                keysForWorker = Math.round(totalKeyspace * weights.get(w.getWorkerId()));
                keysForWorker = Math.max(1, keysForWorker);
            }

            long startIndex = assignedKeys;
            long endIndex = startIndex + keysForWorker;
            endIndex = Math.min(endIndex, totalKeyspace);

            if (startIndex >= totalKeyspace)
                break;

            String subTaskId = UUID.randomUUID().toString();
            HashCrackSubTask st = new HashCrackSubTask(subTaskId, targetHash, algorithm,
                    charset, maxLength, startIndex, endIndex);
            st.setWorkerId(w.getWorkerId());
            subTasks.add(st);

            assignedKeys = endIndex;
        }

        System.out.println("[Partitioner] HashCrack (keyspace=" + totalKeyspace +
                ") split into " + subTasks.size() + " sub-tasks (weighted)");
        return subTasks;
    }

    /**
     * Partition an Image Processing job into row-strip sub-tasks.
     */
    public List<SubTask> partitionImageProc(JSONObject payload) {
        int width = payload.getInt("width");
        int height = payload.getInt("height");
        String operation = payload.getString("operation");
        JSONArray pixelArray = payload.getJSONArray("pixelData");

        int[] pixelData = new int[pixelArray.length()];
        for (int i = 0; i < pixelArray.length(); i++) {
            pixelData[i] = pixelArray.getInt(i);
        }

        List<WorkerInfo> workers = registry.getAvailableWorkers();
        if (workers.isEmpty()) {
            throw new IllegalStateException("No available workers to partition job");
        }

        Map<String, Double> weights = registry.getCapabilityWeights();
        List<SubTask> subTasks = new ArrayList<>();

        int assignedRows = 0;
        for (int i = 0; i < workers.size(); i++) {
            WorkerInfo w = workers.get(i);
            int rowsForWorker;
            if (i == workers.size() - 1) {
                rowsForWorker = height - assignedRows;
            } else {
                rowsForWorker = (int) Math.round(height * weights.get(w.getWorkerId()));
                rowsForWorker = Math.max(1, rowsForWorker);
            }

            int startRow = assignedRows;
            int endRow = Math.min(startRow + rowsForWorker, height);

            if (startRow >= height)
                break;

            String subTaskId = UUID.randomUUID().toString();
            ImageProcSubTask st = new ImageProcSubTask(subTaskId, pixelData, width, height,
                    startRow, endRow, operation);
            st.setWorkerId(w.getWorkerId());
            subTasks.add(st);

            assignedRows = endRow;
        }

        System.out.println("[Partitioner] ImageProc (" + width + "x" + height + " " + operation +
                ") split into " + subTasks.size() + " sub-tasks (weighted)");
        return subTasks;
    }

    /**
     * Partition a K-Means job into data-point range sub-tasks.
     */
    public List<SubTask> partitionKMeans(JSONObject payload, double[][] centroids) {
        JSONArray dataArray = payload.getJSONArray("dataPoints");
        int N = dataArray.length();
        int dims = dataArray.getJSONArray(0).length();

        double[][] dataPoints = new double[N][dims];
        for (int i = 0; i < N; i++) {
            JSONArray row = dataArray.getJSONArray(i);
            for (int j = 0; j < dims; j++) {
                dataPoints[i][j] = row.getDouble(j);
            }
        }

        List<WorkerInfo> workers = registry.getAvailableWorkers();
        if (workers.isEmpty()) {
            throw new IllegalStateException("No available workers to partition job");
        }

        Map<String, Double> weights = registry.getCapabilityWeights();
        List<SubTask> subTasks = new ArrayList<>();

        int assignedPoints = 0;
        for (int i = 0; i < workers.size(); i++) {
            WorkerInfo w = workers.get(i);
            int pointsForWorker;
            if (i == workers.size() - 1) {
                pointsForWorker = N - assignedPoints;
            } else {
                pointsForWorker = (int) Math.round(N * weights.get(w.getWorkerId()));
                pointsForWorker = Math.max(1, pointsForWorker);
            }

            int startIdx = assignedPoints;
            int endIdx = Math.min(startIdx + pointsForWorker, N);

            if (startIdx >= N)
                break;

            String subTaskId = UUID.randomUUID().toString();
            KMeansSubTask st = new KMeansSubTask(subTaskId, dataPoints, centroids, startIdx, endIdx);
            st.setWorkerId(w.getWorkerId());
            subTasks.add(st);

            assignedPoints = endIdx;
        }

        System.out.println("[Partitioner] KMeans (N=" + N + ", dims=" + dims +
                ") split into " + subTasks.size() + " sub-tasks (weighted)");
        return subTasks;
    }

    /**
     * Parse a JSONArray of JSONArrays into a double[][].
     */
    public static double[][] parseMatrix(JSONArray jsonMatrix) {
        int rows = jsonMatrix.length();
        int cols = jsonMatrix.getJSONArray(0).length();
        double[][] matrix = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            JSONArray row = jsonMatrix.getJSONArray(i);
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = row.getDouble(j);
            }
        }
        return matrix;
    }
}
