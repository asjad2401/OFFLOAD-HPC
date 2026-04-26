package com.offloadhpc.worker.rmi;

import com.offloadhpc.worker.compute.MatMulEngine;
import com.offloadhpc.worker.compute.HashCrackEngine;
import com.offloadhpc.worker.compute.ImageProcEngine;
import com.offloadhpc.worker.compute.KMeansEngine;

import com.offloadhpc.contract.WorkerService;
import com.offloadhpc.ui.GridNodeEventListener;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * WorkerServiceImpl -- RMI implementation of the WorkerService contract.
 * v2.1 -- fires onWorkerJobAssigned events for UI tracking.
 */
public class WorkerServiceImpl extends UnicastRemoteObject implements WorkerService {

    private final String workerId;
    private GridNodeEventListener eventListener;

    public WorkerServiceImpl(String workerId) throws RemoteException {
        super();
        this.workerId = workerId;
    }

    public void setEventListener(GridNodeEventListener listener) {
        this.eventListener = listener;
    }

    @Override
    public void register(String workerId, String brokerHost, int brokerPort) throws RemoteException {
        System.out.println("[Worker " + this.workerId + "] register() called via RMI -- "
                + "workerId=" + workerId + ", brokerHost=" + brokerHost + ", brokerPort=" + brokerPort);
    }

    @Override
    public double[][] executeMatMul(String subTaskId,
            double[][] matrixA, double[][] matrixB,
            int startRow, int endRow) throws RemoteException {

        System.out.println("[Worker " + workerId + "] MatMul sub-task " + subTaskId
                + " rows [" + startRow + ", " + endRow + ")");

        long start = System.currentTimeMillis();
        double[][] result = MatMulEngine.compute(matrixA, matrixB, startRow, endRow);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("[Worker " + workerId + "] MatMul sub-task " + subTaskId
                + " completed in " + elapsed + " ms");
        fireJobAssigned(subTaskId, "MATMUL", elapsed);
        return result;
    }

    @Override
    public String executeHashCrack(String subTaskId,
            String targetHash, String algorithm,
            String charset, int maxLength,
            long startIndex, long endIndex) throws RemoteException {

        System.out.println("[Worker " + workerId + "] HashCrack sub-task " + subTaskId
                + " range [" + startIndex + ", " + endIndex + ")");

        long start = System.currentTimeMillis();
        String result = HashCrackEngine.crack(targetHash, algorithm, charset, maxLength, startIndex, endIndex);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("[Worker " + workerId + "] HashCrack sub-task " + subTaskId
                + " completed in " + elapsed + " ms -- "
                + (result != null ? "FOUND: " + result : "not found in range"));
        fireJobAssigned(subTaskId, "HASH_CRACK", elapsed);
        return result;
    }

    @Override
    public int[] executeImageProc(String subTaskId,
            int[] pixelData, int width, int height,
            int startRow, int endRow, String operation) throws RemoteException {

        System.out.println("[Worker " + workerId + "] ImageProc sub-task " + subTaskId
                + " [rows " + startRow + "-" + endRow + ") op=" + operation);

        long start = System.currentTimeMillis();
        int[] result = ImageProcEngine.process(pixelData, width, height, startRow, endRow, operation);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("[Worker " + workerId + "] ImageProc sub-task " + subTaskId
                + " completed in " + elapsed + " ms");
        fireJobAssigned(subTaskId, "IMAGE_PROC", elapsed);
        return result;
    }

    @Override
    public double[][] executeKMeans(String subTaskId,
            double[][] dataPoints, double[][] centroids,
            int startIdx, int endIdx) throws RemoteException {

        System.out.println("[Worker " + workerId + "] KMeans sub-task " + subTaskId
                + " [points " + startIdx + "-" + endIdx + ")");

        long start = System.currentTimeMillis();
        double[][] result = KMeansEngine.computePartial(dataPoints, centroids, startIdx, endIdx);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("[Worker " + workerId + "] KMeans sub-task " + subTaskId
                + " completed in " + elapsed + " ms");
        fireJobAssigned(subTaskId, "KMEANS", elapsed);
        return result;
    }

    @Override
    public String getCapabilities() throws RemoteException {
        Runtime rt = Runtime.getRuntime();
        int cores = rt.availableProcessors();
        long memMB = rt.maxMemory() / (1024 * 1024);

        String json = "{\"cpuCores\":" + cores + ",\"availableMemoryMB\":" + memMB + "}";
        System.out.println("[Worker " + workerId + "] getCapabilities() -> " + json);
        return json;
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    private void fireJobAssigned(String subTaskId, String jobType, long durationMs) {
        if (eventListener != null) {
            try {
                eventListener.onWorkerJobAssigned(subTaskId, jobType, durationMs);
            } catch (Exception e) {
                // Don't let UI errors break RMI calls
            }
        }
    }
}
