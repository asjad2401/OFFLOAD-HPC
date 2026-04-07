package com.offloadhpc.worker.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * BrokerRegistrar — handles TCP registration with the Broker node.
 *
 * Opens a short-lived TCP connection to the Broker, sends WORKER_REGISTER,
 * waits for WORKER_ACK, then closes the connection. All subsequent
 * communication happens via RMI.
 */
public class BrokerRegistrar {

    /**
     * Register this worker with the Broker over TCP.
     *
     * @param workerId   unique worker identifier (e.g. "w1")
     * @param brokerHost Broker IP address
     * @param brokerPort Broker TCP port (usually 9000)
     * @param workerIp   this worker's IP address
     * @param rmiPort    this worker's RMI registry port
     */
    public static void register(String workerId, String brokerHost, int brokerPort,
            String workerIp, int rmiPort) {
        try (Socket socket = new Socket(brokerHost, brokerPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Build WORKER_REGISTER JSON message
            String registerMsg = "{"
                    + "\"type\":\"WORKER_REGISTER\","
                    + "\"workerId\":\"" + workerId + "\","
                    + "\"ip\":\"" + workerIp + "\","
                    + "\"rmiPort\":" + rmiPort
                    + "}";

            // Send registration message
            out.println(registerMsg);
            out.flush();
            System.out.println("[Worker " + workerId + "] Sent WORKER_REGISTER to "
                    + brokerHost + ":" + brokerPort);

            // Wait for WORKER_ACK
            String response = in.readLine();
            if (response != null) {
                System.out.println("[Worker " + workerId + "] Broker response: " + response);

                // Basic validation — check if status is REGISTERED
                if (response.contains("\"REGISTERED\"")) {
                    System.out.println("[Worker " + workerId + "] Successfully registered with Broker.");
                } else {
                    System.err.println("[Worker " + workerId + "] Unexpected response from Broker.");
                }
            } else {
                System.err.println("[Worker " + workerId + "] No response from Broker.");
            }

            // Connection closes automatically via try-with-resources

        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Failed to register with Broker at "
                    + brokerHost + ":" + brokerPort + " — " + e.getMessage());
            System.err.println("[Worker " + workerId + "] Worker will continue running. "
                    + "Re-registration can be attempted manually.");
        }
    }
}
