package com.offloadhpc.worker;

import com.offloadhpc.worker.rmi.WorkerServiceImpl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WorkerRunner — manages the worker lifecycle within a GridNode.
 *
 * Responsibilities:
 * 1. Start RMI service and bind to registry
 * 2. Register with broker over TCP (with capabilities)
 * 3. Send periodic heartbeats to broker
 */
public class WorkerRunner {

    private static final long HEARTBEAT_INTERVAL_MS = 5000; // 5 seconds

    private final String workerId;
    private final String brokerHost;
    private final int brokerPort;
    private final int rmiPort;

    private Registry rmiRegistry;
    private WorkerServiceImpl serviceImpl;
    private ScheduledExecutorService heartbeatScheduler;
    private volatile boolean running = false;

    public WorkerRunner(String workerId, String brokerHost, int brokerPort, int rmiPort) {
        this.workerId = workerId;
        this.brokerHost = brokerHost;
        this.brokerPort = brokerPort;
        this.rmiPort = rmiPort;
    }

    /**
     * Start the worker: bind RMI, register with broker, start heartbeat.
     */
    public void start() throws Exception {
        String localIp = InetAddress.getLocalHost().getHostAddress();
        System.setProperty("java.rmi.server.hostname", localIp);

        // Create or reuse RMI registry (handles restart after re-election)
        try {
            rmiRegistry = LocateRegistry.createRegistry(rmiPort);
        } catch (Exception e) {
            // Registry already exists on this port — reuse it
            rmiRegistry = LocateRegistry.getRegistry(rmiPort);
        }
        serviceImpl = new WorkerServiceImpl(workerId);
        rmiRegistry.rebind("WorkerService", serviceImpl);
        System.out.println("[Worker " + workerId + "] RMI ready on port " + rmiPort);
        System.out.println("[Worker " + workerId + "] Local IP: " + localIp);

        // Register with broker (with capabilities)
        registerWithBroker(localIp);

        // Start heartbeat thread
        running = true;
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Heartbeat-" + workerId);
            t.setDaemon(true);
            return t;
        });
        heartbeatScheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(),
                HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        System.out.println("[Worker " + workerId + "] Running. Heartbeat every " +
                (HEARTBEAT_INTERVAL_MS / 1000) + "s.");
    }

    /**
     * Register this worker with the Broker over TCP, including capability info.
     * Retries up to 3 times with 2s backoff for LAN timing windows.
     */
    private void registerWithBroker(String localIp) {
        Runtime rt = Runtime.getRuntime();
        int cpuCores = rt.availableProcessors();
        long memMB = rt.maxMemory() / (1024 * 1024);

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Socket socket = new Socket(brokerHost, brokerPort);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                String registerMsg = "{"
                        + "\"type\":\"WORKER_REGISTER\","
                        + "\"workerId\":\"" + workerId + "\","
                        + "\"ip\":\"" + localIp + "\","
                        + "\"rmiPort\":" + rmiPort + ","
                        + "\"cpuCores\":" + cpuCores + ","
                        + "\"availableMemoryMB\":" + memMB
                        + "}";

                out.println(registerMsg);
                out.flush();
                System.out.println("[Worker " + workerId + "] Sent WORKER_REGISTER to " +
                        brokerHost + ":" + brokerPort + " (cores=" + cpuCores + ", memMB=" + memMB + ")");

                String response = in.readLine();
                if (response != null && response.contains("\"REGISTERED\"")) {
                    System.out.println("[Worker " + workerId + "] Successfully registered with Broker.");
                    return; // success
                } else {
                    System.err.println("[Worker " + workerId + "] Unexpected response: " + response);
                }
            } catch (Exception e) {
                System.err.println("[Worker " + workerId + "] Registration attempt " + attempt + "/" + maxRetries +
                        " failed: " + e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000); // 2s backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        System.err.println("[Worker " + workerId + "] Failed to register after " + maxRetries + " attempts. " +
                "Worker will continue running. Registration can be reattempted.");
    }

    /**
     * Send a heartbeat to the broker over a short-lived TCP connection.
     */
    private void sendHeartbeat() {
        try (Socket socket = new Socket(brokerHost, brokerPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String heartbeat = "{"
                    + "\"type\":\"WORKER_HEARTBEAT\","
                    + "\"workerId\":\"" + workerId + "\""
                    + "}";

            out.println(heartbeat);
            out.flush();
        } catch (Exception e) {
            // Broker might be down — will be handled by re-election
            if (running) {
                System.err.println("[Worker " + workerId + "] Heartbeat failed: " + e.getMessage());
            }
        }
    }

    /**
     * Stop the worker.
     */
    public void stop() {
        running = false;
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
        try {
            if (rmiRegistry != null) {
                rmiRegistry.unbind("WorkerService");
            }
        } catch (Exception e) {
            // ignore
        }
        System.out.println("[Worker " + workerId + "] Stopped.");
    }
}
