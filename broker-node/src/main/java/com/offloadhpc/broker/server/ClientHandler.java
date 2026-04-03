package com.offloadhpc.broker.server;

import com.offloadhpc.broker.model.SubTask;
import com.offloadhpc.broker.registry.NodeRegistry;
import com.offloadhpc.broker.scheduler.AsyncScheduler;
import com.offloadhpc.broker.scheduler.JobPartitioner;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

/**
 * Handles a single TCP connection — could be a Worker registering
 * or an Android client submitting jobs.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final NodeRegistry registry;
    private final JobPartitioner partitioner;
    private final AsyncScheduler scheduler;

    public ClientHandler(Socket socket, NodeRegistry registry,
            JobPartitioner partitioner, AsyncScheduler scheduler) {
        this.socket = socket;
        this.registry = registry;
        this.partitioner = partitioner;
        this.scheduler = scheduler;
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

                System.out.println("[ClientHandler] Received: " + line);

                try {
                    JSONObject message = new JSONObject(line);
                    String type = message.getString("type");

                    switch (type) {
                        case "WORKER_REGISTER":
                            handleWorkerRegister(message, writer);
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
     * Handle WORKER_REGISTER: add worker to registry and send WORKER_ACK.
     */
    private void handleWorkerRegister(JSONObject message, PrintWriter writer) {
        String workerId = message.getString("workerId");
        String ip = message.getString("ip");
        int rmiPort = message.getInt("rmiPort");

        // Register the worker
        registry.addWorker(workerId, ip, rmiPort);
        registry.printAll();

        // Send WORKER_ACK
        JSONObject ack = new JSONObject();
        ack.put("type", "WORKER_ACK");
        ack.put("workerId", workerId);
        ack.put("status", "REGISTERED");
        writer.println(ack.toString());
        writer.flush();

        System.out.println("[ClientHandler] Sent WORKER_ACK for " + workerId);
    }

    /**
     * Handle JOB_SUBMIT: partition, send ACK, dispatch, and push results.
     */
    private void handleJobSubmit(JSONObject message, PrintWriter writer) {
        String jobId = message.getString("jobId");
        String jobType = message.getString("jobType");
        JSONObject payload = message.getJSONObject("payload");

        System.out.println("[ClientHandler] Job received: " + jobType + " (id=" + jobId + ")");

        try {
            List<SubTask> subTasks;

            if ("MATMUL".equals(jobType)) {
                subTasks = partitioner.partitionMatMul(payload);
            } else if ("HASH_CRACK".equals(jobType)) {
                subTasks = partitioner.partitionHashCrack(payload);
            } else {
                System.err.println("[ClientHandler] Unknown job type: " + jobType);
                return;
            }

            // Send JOB_ACK BEFORE starting dispatch (Android needs totalSubTasks for
            // progress bar)
            JSONObject ack = new JSONObject();
            ack.put("type", "JOB_ACK");
            ack.put("jobId", jobId);
            ack.put("totalSubTasks", subTasks.size());
            ack.put("status", "ACCEPTED");
            writer.println(ack.toString());
            writer.flush();
            System.out.println("[ClientHandler] Sent JOB_ACK for " + jobId +
                    " (" + subTasks.size() + " sub-tasks)");

            // Dispatch sub-tasks in parallel (this blocks until all complete)
            if ("MATMUL".equals(jobType)) {
                int matrixSize = payload.getInt("matrixSize");
                scheduler.dispatchMatMul(jobId, subTasks, matrixSize, writer);
            } else {
                String algorithm = payload.getString("algorithm");
                scheduler.dispatchHashCrack(jobId, subTasks, algorithm, writer);
            }

        } catch (Exception e) {
            System.err.println("[ClientHandler] Failed to process job " + jobId + ": " + e.getMessage());
            e.printStackTrace();

            // Send failure result
            JSONObject error = new JSONObject();
            error.put("type", "JOB_RESULT");
            error.put("jobId", jobId);
            error.put("status", "FAILED");
            error.put("error", e.getMessage());
            writer.println(error.toString());
            writer.flush();
        }
    }
}
