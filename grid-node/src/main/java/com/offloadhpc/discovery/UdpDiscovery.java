package com.offloadhpc.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.function.Consumer;

/**
 * UdpDiscovery — UDP multicast send/receive for node discovery and election.
 *
 * Message format: "MSG_TYPE|nodeId|priority|data"
 * Supported message types:
 * ELECTION — a node is calling an election
 * ALIVE — a higher-priority node responds to an election
 * COORDINATOR — a node declares itself as broker
 * HEARTBEAT — broker sends periodic heartbeats
 * DISCOVER_BROKER — Android client asks "who is the broker?"
 * BROKER_HERE — broker responds with its TCP address
 */
public class UdpDiscovery {

    private final String multicastGroup;
    private final int port;
    private MulticastSocket socket;
    private InetAddress group;
    private volatile boolean running = false;
    private Thread listenerThread;

    public UdpDiscovery(String multicastGroup, int port) {
        this.multicastGroup = multicastGroup;
        this.port = port;
    }

    /**
     * Start listening for multicast messages.
     * Calls messageHandler for each received message.
     */
    public void startListening(Consumer<String> messageHandler) throws IOException {
        group = InetAddress.getByName(multicastGroup);
        socket = new MulticastSocket(port);

        // Join multicast group — try each interface individually
        boolean joined = false;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            try {
                if (ni.isUp() && ni.supportsMulticast()) {
                    socket.joinGroup(new java.net.InetSocketAddress(group, port), ni);
                    System.out.println("[UdpDiscovery] Joined multicast on interface: " + ni.getDisplayName());
                    joined = true;
                }
            } catch (Exception e) {
                // Some interfaces don't support multicast — skip silently
            }
        }

        // If no per-interface join worked, try the legacy method
        if (!joined) {
            try {
                socket.joinGroup(group);
                System.out.println("[UdpDiscovery] Joined multicast via legacy method");
                joined = true;
            } catch (Exception e) {
                System.err.println("[UdpDiscovery] WARNING: Could not join multicast group: " + e.getMessage());
                System.err.println("[UdpDiscovery] Election will still work on same-machine nodes via loopback.");
            }
        }

        socket.setSoTimeout(1000); // 1s timeout so we can check `running` flag
        running = true;

        listenerThread = new Thread(() -> {
            byte[] buf = new byte[1024];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength()).trim();
                    if (!message.isEmpty()) {
                        messageHandler.accept(message);
                    }
                } catch (SocketTimeoutException e) {
                    // Expected — loop and check running flag
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[UdpDiscovery] Receive error: " + e.getMessage());
                    }
                }
            }
        }, "UdpDiscovery-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        System.out.println("[UdpDiscovery] Listening on " + multicastGroup + ":" + port);
    }

    /**
     * Send a multicast message.
     */
    public void send(String message) {
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, group, port);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("[UdpDiscovery] Send error: " + e.getMessage());
        }
    }

    /**
     * Send a message and wait for responses of a specific type.
     * Uses a separate socket to avoid conflicts with the listener thread.
     * Returns true if at least one matching response was received.
     */
    public boolean sendAndWaitForResponse(String message, String expectedType, long timeoutMs) {
        // Send via the main socket (shared, write-only here)
        send(message);

        // Listen for responses on a separate temporary socket to avoid recv conflicts
        try {
            MulticastSocket tempSocket = new MulticastSocket(null);
            tempSocket.setReuseAddress(true);
            tempSocket.bind(new java.net.InetSocketAddress(port));

            // Join multicast on all interfaces
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                try {
                    if (ni.isUp() && ni.supportsMulticast()) {
                        tempSocket.joinGroup(new java.net.InetSocketAddress(group, port), ni);
                    }
                } catch (Exception e) { /* skip */ }
            }

            tempSocket.setSoTimeout((int) timeoutMs);
            byte[] buf = new byte[1024];

            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    int remaining = (int) (deadline - System.currentTimeMillis());
                    if (remaining <= 0) break;
                    tempSocket.setSoTimeout(remaining);

                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    tempSocket.receive(packet);
                    String response = new String(packet.getData(), 0, packet.getLength()).trim();
                    if (response.startsWith(expectedType + "|")) {
                        tempSocket.close();
                        return true;
                    }
                } catch (SocketTimeoutException e) {
                    break;
                } catch (IOException e) {
                    break;
                }
            }

            tempSocket.close();
        } catch (Exception e) {
            System.err.println("[UdpDiscovery] sendAndWaitForResponse error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Stop listening.
     */
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.leaveGroup(new java.net.InetSocketAddress(group, port), null);
            } catch (Exception e) {
                try {
                    socket.leaveGroup(group);
                } catch (Exception e2) {
                    /* ignore */ }
            }
            socket.close();
        }
    }

    public boolean isRunning() {
        return running;
    }
}
