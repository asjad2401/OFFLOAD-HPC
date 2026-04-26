package com.offloadhpc.broker.server;

import com.offloadhpc.broker.model.SubTask;
import com.offloadhpc.broker.registry.NodeRegistry;
import com.offloadhpc.broker.scheduler.AsyncScheduler;
import com.offloadhpc.broker.scheduler.JobPartitioner;
import com.offloadhpc.ui.GridNodeEventListener;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Random;

/**
 * Handles a single TCP connection — could be a Worker registering
 * or an Android client submitting jobs.
 * v2.0 — supports WORKER_HEARTBEAT, capabilities, and 4 job types.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final NodeRegistry registry;
    private final JobPartitioner partitioner;
    private final AsyncScheduler scheduler;
    private final GridNodeEventListener eventListener;

    public ClientHandler(Socket socket, NodeRegistry registry,
            JobPartitioner partitioner, AsyncScheduler scheduler,
            GridNodeEventListener eventListener) {
        this.socket = socket;
        this.registry = registry;
        this.partitioner = partitioner;
        this.scheduler = scheduler;
        this.eventListener = eventListener;
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                System.out.println("[ClientHandler] Received: " +
                        line.substring(0, Math.min(line.length(), 200)));

                try {
                    JSONObject message = new JSONObject(line);
                    String type = message.getString("type");

                    switch (type) {
                        case "WORKER_REGISTER":
                            handleWorkerRegister(message, writer);
                            break;
                        case "WORKER_HEARTBEAT":
                            handleWorkerHeartbeat(message);
                            break;
                        case "JOB_SUBMIT":
                            handleJobSubmit(message, writer);
                            break;
                        default:
                            System.err.println("[ClientHandler] Unknown message type: " + type);
                    }
                } catch (Exception e) {
                    System.err.println("[ClientHandler] Error processing message: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("[ClientHandler] Connection error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            System.out.println("[ClientHandler] Connection closed");
        }
    }

    /**
     * Handle WORKER_REGISTER: add worker to registry (with capabilities) and send
     * WORKER_ACK.
     */
    private void handleWorkerRegister(JSONObject message, PrintWriter writer) {
        String workerId = message.getString("workerId");
        String ip = message.getString("ip");
        int rmiPort = message.getInt("rmiPort");

        // v2.0: read capabilities if present
        int cpuCores = message.optInt("cpuCores", 1);
        long availableMemoryMB = message.optLong("availableMemoryMB", 512);

        registry.addWorker(workerId, ip, rmiPort, cpuCores, availableMemoryMB);
        registry.printAll();

        // Send WORKER_ACK
        JSONObject ack = new JSONObject();
        ack.put("type", "WORKER_ACK");
        ack.put("workerId", workerId);
        ack.put("status", "REGISTERED");
        writer.println(ack.toString());
        writer.flush();

        System.out.println("[ClientHandler] Sent WORKER_ACK for " + workerId +
                " (cores=" + cpuCores + ", memMB=" + availableMemoryMB + ")");
    }

    /**
     * Handle WORKER_HEARTBEAT: update heartbeat timestamp.
     */
    private void handleWorkerHeartbeat(JSONObject message) {
        String workerId = message.getString("workerId");
        registry.updateHeartbeat(workerId);
    }

    /**
     * Handle JOB_SUBMIT: partition, send ACK, dispatch, and push results.
     */
    private void handleJobSubmit(JSONObject message, PrintWriter writer) {
        String jobId = message.getString("jobId");
        String jobType = message.getString("jobType");
        JSONObject payload = message.getJSONObject("payload");

        System.out.println("[ClientHandler] Job received: " + jobType + " (id=" + jobId + ")");

        if (eventListener != null) {
            eventListener.onJobReceived(jobId, jobType);
        }

        try {
            List<SubTask> subTasks;

            switch (jobType) {
                case "MATMUL":
                    subTasks = partitioner.partitionMatMul(payload);
                    sendJobAck(writer, jobId, subTasks.size());
                    int matrixSize = payload.getInt("matrixSize");
                    scheduler.dispatchMatMul(jobId, subTasks, matrixSize, writer);
                    if (eventListener != null) eventListener.onJobCompleted(jobId, jobType, "SUCCESS");
                    break;

                case "HASH_CRACK":
                    subTasks = partitioner.partitionHashCrack(payload);
                    sendJobAck(writer, jobId, subTasks.size());
                    String algorithm = payload.getString("algorithm");
                    scheduler.dispatchHashCrack(jobId, subTasks, algorithm, writer);
                    if (eventListener != null) eventListener.onJobCompleted(jobId, jobType, "SUCCESS");
                    break;

                case "IMAGE_PROC":
                    subTasks = partitioner.partitionImageProc(payload);
                    sendJobAck(writer, jobId, subTasks.size());
                    int width = payload.getInt("width");
                    int height = payload.getInt("height");
                    scheduler.dispatchImageProc(jobId, subTasks, width, height, writer);
                    if (eventListener != null) eventListener.onJobCompleted(jobId, jobType, "SUCCESS");
                    break;

                case "KMEANS":
                    int K = payload.getInt("K");
                    int dims = payload.getJSONArray("dataPoints").getJSONArray(0).length();
                    int iterations = payload.optInt("iterations", 10);

                    // Initialize random centroids
                    double[][] initialCentroids = initRandomCentroids(payload, K, dims);

                    subTasks = partitioner.partitionKMeans(payload, initialCentroids);
                    // For K-Means, totalSubTasks = iterations (progress per iteration)
                    sendJobAck(writer, jobId, iterations);
                    scheduler.dispatchKMeans(jobId, subTasks, iterations, K, dims,
                            initialCentroids, partitioner, payload, writer);
                    if (eventListener != null) eventListener.onJobCompleted(jobId, jobType, "SUCCESS");
                    break;

                default:
                    System.err.println("[ClientHandler] Unknown job type: " + jobType);
                    return;
            }

        } catch (Exception e) {
            System.err.println("[ClientHandler] Failed to process job " + jobId + ": " + e.getMessage());
            e.printStackTrace();

            if (eventListener != null) eventListener.onJobCompleted(jobId, jobType, "FAILED");

            JSONObject error = new JSONObject();
            error.put("type", "JOB_RESULT");
            error.put("jobId", jobId);
            error.put("status", "FAILED");
            error.put("error", e.getMessage());
            writer.println(error.toString());
            writer.flush();
        }
    }

    private void sendJobAck(PrintWriter writer, String jobId, int totalSubTasks) {
        JSONObject ack = new JSONObject();
        ack.put("type", "JOB_ACK");
        ack.put("jobId", jobId);
        ack.put("totalSubTasks", totalSubTasks);
        ack.put("status", "ACCEPTED");
        writer.println(ack.toString());
        writer.flush();
        System.out.println("[ClientHandler] Sent JOB_ACK for " + jobId +
                " (" + totalSubTasks + " sub-tasks)");
    }

    /**
     * Initialize K random centroids by selecting random data points.
     */
    private double[][] initRandomCentroids(JSONObject payload, int K, int dims) {
        org.json.JSONArray dataArray = payload.getJSONArray("dataPoints");
        int N = dataArray.length();
        Random rand = new Random(42); // fixed seed for reproducibility
        double[][] centroids = new double[K][dims];

        for (int k = 0; k < K; k++) {
            int idx = rand.nextInt(N);
            org.json.JSONArray point = dataArray.getJSONArray(idx);
            for (int d = 0; d < dims; d++) {
                centroids[k][d] = point.getDouble(d);
            }
        }
        return centroids;
    }
}
