package com.offloadhpc.broker.scheduler;

import com.offloadhpc.broker.model.HashCrackSubTask;
import com.offloadhpc.broker.model.MatMulSubTask;
import com.offloadhpc.broker.model.SubTask;
import com.offloadhpc.broker.registry.NodeRegistry;
import com.offloadhpc.broker.registry.WorkerInfo;
import com.offloadhpc.contract.WorkerService;

import org.json.JSONObject;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dispatches sub-tasks to workers via RMI in parallel and pushes
 * progress updates back to the Android client over TCP.
 */
public class AsyncScheduler {

    private final NodeRegistry registry;

    public AsyncScheduler(NodeRegistry registry) {
        this.registry = registry;
    }

    /**
     * Dispatch a MatMul job: partition → parallel RMI calls → aggregate → push
     * result.
     */
    public void dispatchMatMul(String jobId, List<SubTask> subTasks, int matrixSize,
            PrintWriter androidWriter) {
        int total = subTasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(total);
        CompletionService<ResultAggregator.MatMulSlice> cs = new ExecutorCompletionService<>(pool);

        // Submit all sub-tasks in parallel
        for (SubTask st : subTasks) {
            MatMulSubTask mt = (MatMulSubTask) st;
            cs.submit(new Callable<ResultAggregator.MatMulSlice>() {
                @Override
                public ResultAggregator.MatMulSlice call() throws Exception {
                    // Mark worker BUSY
                    registry.setStatus(mt.getWorkerId(), WorkerInfo.Status.BUSY);
                    try {
                        WorkerService worker = registry.getRmiStub(mt.getWorkerId());
                        System.out.println("[Scheduler] Dispatching MatMul sub-task " +
                                mt.getSubTaskId() + " to " + mt.getWorkerId() +
                                " [rows " + mt.getStartRow() + "-" + mt.getEndRow() + ")");
                        double[][] result = worker.executeMatMul(
                                mt.getSubTaskId(),
                                mt.getMatrixA(), mt.getMatrixB(),
                                mt.getStartRow(), mt.getEndRow());
                        return new ResultAggregator.MatMulSlice(mt.getStartRow(), result);
                    } finally {
                        registry.setStatus(mt.getWorkerId(), WorkerInfo.Status.AVAILABLE);
                    }
                }
            });
        }

        // Collect results and push progress
        ResultAggregator.MatMulSlice[] slices = new ResultAggregator.MatMulSlice[total];
        try {
            for (int i = 0; i < total; i++) {
                slices[i] = cs.take().get(); // blocks until next completes
                pushProgressUpdate(androidWriter, jobId, i + 1, total);
            }

            // Aggregate and send result
            double[][] fullResult = ResultAggregator.mergeMatMul(matrixSize, slices);

            JSONObject result = new JSONObject();
            result.put("type", "JOB_RESULT");
            result.put("jobId", jobId);
            result.put("status", "SUCCESS");
            JSONObject resultPayload = new JSONObject();
            resultPayload.put("matrixC", ResultAggregator.matrixToJson(fullResult));
            result.put("result", resultPayload);

            androidWriter.println(result.toString());
            androidWriter.flush();
            System.out.println("[Scheduler] MatMul job " + jobId + " completed successfully");

        } catch (Exception e) {
            System.err.println("[Scheduler] MatMul job " + jobId + " failed: " + e.getMessage());
            e.printStackTrace();
            sendError(androidWriter, jobId, e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Dispatch a Hash Crack job: partition → parallel RMI calls → aggregate → push
     * result.
     */
    public void dispatchHashCrack(String jobId, List<SubTask> subTasks, String algorithm,
            PrintWriter androidWriter) {
        int total = subTasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(total);
        CompletionService<String> cs = new ExecutorCompletionService<>(pool);

        for (SubTask st : subTasks) {
            HashCrackSubTask ht = (HashCrackSubTask) st;
            cs.submit(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    registry.setStatus(ht.getWorkerId(), WorkerInfo.Status.BUSY);
                    try {
                        WorkerService worker = registry.getRmiStub(ht.getWorkerId());
                        System.out.println("[Scheduler] Dispatching HashCrack sub-task " +
                                ht.getSubTaskId() + " to " + ht.getWorkerId() +
                                " [index " + ht.getStartIndex() + "-" + ht.getEndIndex() + ")");
                        return worker.executeHashCrack(
                                ht.getSubTaskId(),
                                ht.getTargetHash(), ht.getAlgorithm(),
                                ht.getCharset(), ht.getMaxLength(),
                                ht.getStartIndex(), ht.getEndIndex());
                    } finally {
                        registry.setStatus(ht.getWorkerId(), WorkerInfo.Status.AVAILABLE);
                    }
                }
            });
        }

        // Collect results and push progress
        String[] results = new String[total];
        try {
            for (int i = 0; i < total; i++) {
                results[i] = cs.take().get();
                pushProgressUpdate(androidWriter, jobId, i + 1, total);
            }

            String crackedValue = ResultAggregator.aggregateHashCrack(results);

            JSONObject result = new JSONObject();
            result.put("type", "JOB_RESULT");
            result.put("jobId", jobId);
            result.put("status", crackedValue != null ? "SUCCESS" : "FAILED");
            JSONObject resultPayload = new JSONObject();
            resultPayload.put("crackedValue", crackedValue != null ? crackedValue : JSONObject.NULL);
            resultPayload.put("algorithm", algorithm);
            result.put("result", resultPayload);

            androidWriter.println(result.toString());
            androidWriter.flush();
            System.out.println("[Scheduler] HashCrack job " + jobId + " completed. " +
                    (crackedValue != null ? "Cracked: " + crackedValue : "Not found"));

        } catch (Exception e) {
            System.err.println("[Scheduler] HashCrack job " + jobId + " failed: " + e.getMessage());
            e.printStackTrace();
            sendError(androidWriter, jobId, e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Push a PROGRESS_UPDATE message to the Android client.
     */
    private void pushProgressUpdate(PrintWriter writer, String jobId,
            int completed, int total) {
        JSONObject progress = new JSONObject();
        progress.put("type", "PROGRESS_UPDATE");
        progress.put("jobId", jobId);
        progress.put("completedSubTasks", completed);
        progress.put("totalSubTasks", total);
        writer.println(progress.toString());
        writer.flush();
        System.out.println("[Scheduler] Progress: " + completed + "/" + total +
                " for job " + jobId);
    }

    /**
     * Send an error result to the Android client.
     */
    private void sendError(PrintWriter writer, String jobId, String errorMsg) {
        JSONObject error = new JSONObject();
        error.put("type", "JOB_RESULT");
        error.put("jobId", jobId);
        error.put("status", "FAILED");
        error.put("error", errorMsg);
        writer.println(error.toString());
        writer.flush();
    }
}
