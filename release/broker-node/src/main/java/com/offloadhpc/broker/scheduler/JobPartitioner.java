package com.offloadhpc.broker.scheduler;

import com.offloadhpc.broker.model.HashCrackSubTask;
import com.offloadhpc.broker.model.MatMulSubTask;
import com.offloadhpc.broker.model.SubTask;
import com.offloadhpc.broker.registry.NodeRegistry;
import com.offloadhpc.broker.registry.WorkerInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Takes a full job description and produces a list of SubTask objects.
 * Pure logic — no networking.
 */
public class JobPartitioner {

    private final NodeRegistry registry;

    public JobPartitioner(NodeRegistry registry) {
        this.registry = registry;
    }

    /**
     * Partition a MatMul job into row-range sub-tasks.
     */
    public List<SubTask> partitionMatMul(JSONObject payload) {
        int matrixSize = payload.getInt("matrixSize");
        double[][] matrixA = parseMatrix(payload.getJSONArray("matrixA"));
        double[][] matrixB = parseMatrix(payload.getJSONArray("matrixB"));

        List<WorkerInfo> workers = registry.getAvailableWorkers();
        int w = workers.size();
        if (w == 0) {
            throw new IllegalStateException("No available workers to partition job");
        }

        int rowsPerWorker = matrixSize / w;
        List<SubTask> subTasks = new ArrayList<>();

        for (int i = 0; i < w; i++) {
            int startRow = i * rowsPerWorker;
            int endRow = (i == w - 1) ? matrixSize : startRow + rowsPerWorker;
            String subTaskId = UUID.randomUUID().toString();

            MatMulSubTask st = new MatMulSubTask(subTaskId, matrixA, matrixB, startRow, endRow);
            st.setWorkerId(workers.get(i).getWorkerId());
            subTasks.add(st);
        }

        System.out.println("[Partitioner] MatMul (" + matrixSize + "x" + matrixSize +
                ") split into " + subTasks.size() + " sub-tasks");
        return subTasks;
    }

    /**
     * Partition a Hash Crack job into index-range sub-tasks.
     */
    public List<SubTask> partitionHashCrack(JSONObject payload) {
        String targetHash = payload.getString("targetHash");
        String algorithm = payload.getString("algorithm");
        String charset = payload.getString("charset");
        int maxLength = payload.getInt("maxLength");

        // Compute total keyspace: K = sum(charset.length^i for i in 1..maxLength)
        int base = charset.length();
        long totalKeyspace = 0;
        for (int i = 1; i <= maxLength; i++) {
            totalKeyspace += (long) Math.pow(base, i);
        }

        List<WorkerInfo> workers = registry.getAvailableWorkers();
        int w = workers.size();
        if (w == 0) {
            throw new IllegalStateException("No available workers to partition job");
        }

        long rangePerWorker = totalKeyspace / w;
        List<SubTask> subTasks = new ArrayList<>();

        for (int i = 0; i < w; i++) {
            long startIndex = i * rangePerWorker;
            long endIndex = (i == w - 1) ? totalKeyspace : startIndex + rangePerWorker;
            String subTaskId = UUID.randomUUID().toString();

            HashCrackSubTask st = new HashCrackSubTask(subTaskId, targetHash, algorithm,
                    charset, maxLength, startIndex, endIndex);
            st.setWorkerId(workers.get(i).getWorkerId());
            subTasks.add(st);
        }

        System.out.println("[Partitioner] HashCrack (keyspace=" + totalKeyspace +
                ") split into " + subTasks.size() + " sub-tasks");
        return subTasks;
    }

    /**
     * Parse a JSONArray of JSONArrays into a double[][].
     */
    private double[][] parseMatrix(JSONArray jsonMatrix) {
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
