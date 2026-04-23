package com.offloadhpc.broker.scheduler;

import com.offloadhpc.broker.model.HashCrackSubTask;
import com.offloadhpc.broker.model.ImageProcSubTask;
import com.offloadhpc.broker.model.KMeansSubTask;
import com.offloadhpc.broker.model.MatMulSubTask;
import com.offloadhpc.broker.model.SubTask;
import com.offloadhpc.broker.registry.NodeRegistry;
import com.offloadhpc.broker.registry.WorkerInfo;
import com.offloadhpc.contract.WorkerService;
import com.offloadhpc.worker.compute.KMeansEngine;

import org.json.JSONArray;
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
 * v2.0 — extended with Image Processing and K-Means dispatch.
 */
public class AsyncScheduler {

    private final NodeRegistry registry;

    public AsyncScheduler(NodeRegistry registry) {
        this.registry = registry;
    }

    // ── MatMul ──────────────────────────────────────────────────────

    public void dispatchMatMul(String jobId, List<SubTask> subTasks, int matrixSize,
            PrintWriter androidWriter) {
        int total = subTasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(total);
        CompletionService<ResultAggregator.MatMulSlice> cs = new ExecutorCompletionService<>(pool);

        for (SubTask st : subTasks) {
            MatMulSubTask mt = (MatMulSubTask) st;
            cs.submit(() -> {
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
            });
        }

        ResultAggregator.MatMulSlice[] slices = new ResultAggregator.MatMulSlice[total];
        try {
            for (int i = 0; i < total; i++) {
                slices[i] = cs.take().get();
                pushProgressUpdate(androidWriter, jobId, i + 1, total);
            }

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

    // ── Hash Crack ──────────────────────────────────────────────────

    public void dispatchHashCrack(String jobId, List<SubTask> subTasks, String algorithm,
            PrintWriter androidWriter) {
        int total = subTasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(total);
        CompletionService<String> cs = new ExecutorCompletionService<>(pool);

        for (SubTask st : subTasks) {
            HashCrackSubTask ht = (HashCrackSubTask) st;
            cs.submit(() -> {
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
            });
        }

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

    // ── Image Processing ────────────────────────────────────────────

    public void dispatchImageProc(String jobId, List<SubTask> subTasks,
            int width, int height,
            PrintWriter androidWriter) {
        int total = subTasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(total);
        CompletionService<ResultAggregator.ImageProcSlice> cs = new ExecutorCompletionService<>(pool);

        for (SubTask st : subTasks) {
            ImageProcSubTask it = (ImageProcSubTask) st;
            cs.submit(() -> {
                registry.setStatus(it.getWorkerId(), WorkerInfo.Status.BUSY);
                try {
                    WorkerService worker = registry.getRmiStub(it.getWorkerId());
                    System.out.println("[Scheduler] Dispatching ImageProc sub-task " +
                            it.getSubTaskId() + " to " + it.getWorkerId() +
                            " [rows " + it.getStartRow() + "-" + it.getEndRow() + ")");
                    int[] result = worker.executeImageProc(
                            it.getSubTaskId(),
                            it.getPixelData(), it.getWidth(), it.getHeight(),
                            it.getStartRow(), it.getEndRow(), it.getOperation());
                    return new ResultAggregator.ImageProcSlice(it.getStartRow(), result);
                } finally {
                    registry.setStatus(it.getWorkerId(), WorkerInfo.Status.AVAILABLE);
                }
            });
        }

        ResultAggregator.ImageProcSlice[] slices = new ResultAggregator.ImageProcSlice[total];
        try {
            for (int i = 0; i < total; i++) {
                slices[i] = cs.take().get();
                pushProgressUpdate(androidWriter, jobId, i + 1, total);
            }

            int[] fullResult = ResultAggregator.mergeImageProc(width, height, slices);

            JSONObject result = new JSONObject();
            result.put("type", "JOB_RESULT");
            result.put("jobId", jobId);
            result.put("status", "SUCCESS");
            JSONObject resultPayload = new JSONObject();
            resultPayload.put("processedPixels", ResultAggregator.pixelArrayToJson(fullResult));
            resultPayload.put("width", width);
            resultPayload.put("height", height);
            result.put("result", resultPayload);

            androidWriter.println(result.toString());
            androidWriter.flush();
            System.out.println("[Scheduler] ImageProc job " + jobId + " completed successfully");

        } catch (Exception e) {
            System.err.println("[Scheduler] ImageProc job " + jobId + " failed: " + e.getMessage());
            e.printStackTrace();
            sendError(androidWriter, jobId, e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    // ── K-Means Clustering ──────────────────────────────────────────

    public void dispatchKMeans(String jobId, List<SubTask> subTasks,
            int iterations, int K, int dims,
            double[][] initialCentroids,
            JobPartitioner partitioner, JSONObject payload,
            PrintWriter androidWriter) {
        double[][] centroids = initialCentroids;

        try {
            for (int iter = 0; iter < iterations; iter++) {
                System.out.println("[Scheduler] KMeans job " + jobId +
                        " — iteration " + (iter + 1) + "/" + iterations);

                // Re-partition with updated centroids for each iteration
                List<SubTask> iterSubTasks;
                if (iter == 0) {
                    iterSubTasks = subTasks;
                } else {
                    iterSubTasks = partitioner.partitionKMeans(payload, centroids);
                }

                int total = iterSubTasks.size();
                ExecutorService pool = Executors.newFixedThreadPool(total);
                CompletionService<double[][]> cs = new ExecutorCompletionService<>(pool);

                final double[][] currentCentroids = centroids;

                for (SubTask st : iterSubTasks) {
                    KMeansSubTask kt = (KMeansSubTask) st;
                    cs.submit(() -> {
                        registry.setStatus(kt.getWorkerId(), WorkerInfo.Status.BUSY);
                        try {
                            WorkerService worker = registry.getRmiStub(kt.getWorkerId());
                            return worker.executeKMeans(
                                    kt.getSubTaskId(),
                                    kt.getDataPoints(), currentCentroids,
                                    kt.getStartIdx(), kt.getEndIdx());
                        } finally {
                            registry.setStatus(kt.getWorkerId(), WorkerInfo.Status.AVAILABLE);
                        }
                    });
                }

                // Collect partial results
                double[][][] partials = new double[total][][];
                for (int i = 0; i < total; i++) {
                    partials[i] = cs.take().get();
                }
                pool.shutdown();

                // Aggregate and compute new centroids
                double[][] aggregated = ResultAggregator.aggregateKMeansPartial(partials);
                centroids = KMeansEngine.computeNewCentroids(aggregated, centroids);

                // Push progress for this iteration
                pushProgressUpdate(androidWriter, jobId, iter + 1, iterations);
            }

            // Send final result
            JSONObject result = new JSONObject();
            result.put("type", "JOB_RESULT");
            result.put("jobId", jobId);
            result.put("status", "SUCCESS");
            JSONObject resultPayload = new JSONObject();
            resultPayload.put("centroids", ResultAggregator.matrixToJson(centroids));
            resultPayload.put("K", K);
            resultPayload.put("iterations", iterations);
            result.put("result", resultPayload);

            androidWriter.println(result.toString());
            androidWriter.flush();
            System.out.println("[Scheduler] KMeans job " + jobId + " completed successfully");

        } catch (Exception e) {
            System.err.println("[Scheduler] KMeans job " + jobId + " failed: " + e.getMessage());
            e.printStackTrace();
            sendError(androidWriter, jobId, e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

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
