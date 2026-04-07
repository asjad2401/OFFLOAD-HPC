package com.offloadhpc.broker.server;

import com.offloadhpc.broker.registry.NodeRegistry;
import com.offloadhpc.broker.scheduler.AsyncScheduler;
import com.offloadhpc.broker.scheduler.JobPartitioner;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Main entry point for the Broker Node.
 * Listens for TCP connections on port 9000 from both Android clients
 * and Worker nodes.
 */
public class BrokerServer {

    public static final int DEFAULT_PORT = 9000;

    private final int port;
    private final NodeRegistry registry;
    private final JobPartitioner partitioner;
    private final AsyncScheduler scheduler;

    public BrokerServer(int port) {
        this.port = port;
        this.registry = new NodeRegistry();
        this.partitioner = new JobPartitioner(registry);
        this.scheduler = new AsyncScheduler(registry);
    }

    /**
     * Start the server — blocks in accept loop.
     */
    public void start() {
        System.out.println("==============================================");
        System.out.println("   OFFLOAD-HPC Broker Node");
        System.out.println("   Listening on port " + port);
        System.out.println("==============================================");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] New connection from " +
                        clientSocket.getInetAddress().getHostAddress() + ":" +
                        clientSocket.getPort());

                // Spawn a handler thread for each connection
                ClientHandler handler = new ClientHandler(
                        clientSocket, registry, partitioner, scheduler);
                new Thread(handler, "ClientHandler-" + clientSocket.getPort()).start();
            }
        } catch (IOException e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0] + ", using default " + DEFAULT_PORT);
            }
        }

        BrokerServer server = new BrokerServer(port);
        server.start();
    }
}
