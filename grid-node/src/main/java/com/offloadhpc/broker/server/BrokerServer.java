package com.offloadhpc.broker.server;

import com.offloadhpc.broker.registry.NodeRegistry;
import com.offloadhpc.broker.scheduler.AsyncScheduler;
import com.offloadhpc.broker.scheduler.JobPartitioner;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TCP server for the Broker role.
 * Listens for connections from both Android clients and Worker nodes.
 * v2.0 — same core logic, but started by GridNode after election.
 */
public class BrokerServer {

    public static final int DEFAULT_PORT = 9000;

    private final int port;
    private final NodeRegistry registry;
    private final JobPartitioner partitioner;
    private final AsyncScheduler scheduler;
    private volatile boolean running = false;
    private ServerSocket serverSocket;

    public BrokerServer(int port, NodeRegistry registry) {
        this.port = port;
        this.registry = registry;
        this.partitioner = new JobPartitioner(registry);
        this.scheduler = new AsyncScheduler(registry);
    }

    /**
     * Start the server — blocks in accept loop.
     */
    public void start() {
        running = true;
        System.out.println("==============================================");
        System.out.println("   OFFLOAD-HPC Broker Node (v2.0)");
        System.out.println("   Listening on port " + port);
        System.out.println("==============================================");

        try {
            serverSocket = new ServerSocket(port);
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] New connection from " +
                        clientSocket.getInetAddress().getHostAddress() + ":" +
                        clientSocket.getPort());

                ClientHandler handler = new ClientHandler(
                        clientSocket, registry, partitioner, scheduler);
                new Thread(handler, "ClientHandler-" + clientSocket.getPort()).start();
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[Server] Fatal error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Stop the server.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error closing server socket: " + e.getMessage());
        }
        registry.shutdown();
    }

    public int getPort() {
        return port;
    }

    public NodeRegistry getRegistry() {
        return registry;
    }
}
