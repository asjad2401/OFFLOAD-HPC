package com.offloadhpc.discovery;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * BullyElection — implements the Bully leader election algorithm.
 *
 * Algorithm:
 * 1. Node broadcasts ELECTION|nodeId|priority
 * 2. Any node with higher priority responds ALIVE|nodeId|priority
 * 3. If no ALIVE received within ELECTION_TIMEOUT → this node wins
 * 4. Winner broadcasts COORDINATOR|nodeId|priority|tcpPort
 * 5. All nodes receiving COORDINATOR accept the sender as broker
 *
 * On broker heartbeat timeout → surviving nodes trigger re-election.
 */
public class BullyElection {

    private static final long ELECTION_TIMEOUT_MS = 3000; // wait 3s for ALIVE responses
    private static final long HEARTBEAT_INTERVAL_MS = 5000; // broker heartbeats every 5s
    private static final long HEARTBEAT_TIMEOUT_MS = 15000; // 15s without heartbeat → re-elect

    private final String nodeId;
    private final int priority;
    private final int tcpPort;
    private final UdpDiscovery udp;

    // Election state
    private volatile String currentBrokerId = null;
    private volatile String currentBrokerIp = null;
    private volatile int currentBrokerTcpPort = -1;
    private volatile boolean isBroker = false;
    private volatile long lastBrokerHeartbeat = 0;

    // Callback for role assignments
    private ElectionListener listener;

    // Heartbeat monitoring
    private ScheduledExecutorService heartbeatMonitor;
    private ScheduledExecutorService brokerHeartbeatSender;

    // Track known nodes
    private final ConcurrentHashMap<String, Integer> knownNodes = new ConcurrentHashMap<>();

    public interface ElectionListener {
        void onElectedAsBroker();

        void onBrokerDiscovered(String brokerId, String brokerIp, int brokerTcpPort);

        void onBrokerLost();
    }

    public BullyElection(String nodeId, int priority, int tcpPort, UdpDiscovery udp) {
        this.nodeId = nodeId;
        this.priority = priority;
        this.tcpPort = tcpPort;
        this.udp = udp;
    }

    public void setListener(ElectionListener listener) {
        this.listener = listener;
    }

    /**
     * Start the election process. This should be called after UdpDiscovery is
     * listening.
     */
    public void startElection() {
        System.out.println("[Election] Node " + nodeId + " (priority=" + priority +
                ") starting election...");

        // Broadcast ELECTION message
        String electionMsg = "ELECTION|" + nodeId + "|" + priority;
        boolean higherNodeExists = udp.sendAndWaitForResponse(
                electionMsg, "ALIVE", ELECTION_TIMEOUT_MS);

        if (!higherNodeExists) {
            // No higher-priority node responded — we are the coordinator
            declareCoordinator();
        } else {
            // A higher-priority node exists — wait for COORDINATOR
            System.out.println("[Election] Higher-priority node exists. Waiting for COORDINATOR...");
            waitForCoordinator();
        }
    }

    /**
     * Declare this node as the coordinator (broker).
     */
    private void declareCoordinator() {
        isBroker = true;
        currentBrokerId = nodeId;
        currentBrokerTcpPort = tcpPort;

        String coordMsg = "COORDINATOR|" + nodeId + "|" + priority + "|" + tcpPort;
        udp.send(coordMsg);

        System.out.println("==================================================");
        System.out.println("  [Election] ★ NODE " + nodeId + " ELECTED AS BROKER ★");
        System.out.println("==================================================");

        // Start sending broker heartbeats
        startBrokerHeartbeat();

        if (listener != null) {
            listener.onElectedAsBroker();
        }
    }

    /**
     * Wait for a COORDINATOR message after seeing a higher-priority node.
     * If no COORDINATOR arrives in 2x election timeout, start a new election.
     */
    private void waitForCoordinator() {
        long deadline = System.currentTimeMillis() + ELECTION_TIMEOUT_MS * 2;
        while (System.currentTimeMillis() < deadline) {
            if (currentBrokerId != null) {
                return; // COORDINATOR received via message handler
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                break;
            }
        }
        // No COORDINATOR received — start new election
        System.out.println("[Election] No COORDINATOR received. Starting new election...");
        startElection();
    }

    /**
     * Handle incoming UDP messages related to election.
     * Called by GridNode from the UdpDiscovery listener.
     */
    public void handleMessage(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 3)
            return;

        String type = parts[0];
        String senderId = parts[1];
        int senderPriority = Integer.parseInt(parts[2]);

        // Ignore our own messages
        if (senderId.equals(nodeId))
            return;

        switch (type) {
            case "ELECTION":
                knownNodes.put(senderId, senderPriority);
                if (priority > senderPriority) {
                    // We have higher priority — respond with ALIVE
                    udp.send("ALIVE|" + nodeId + "|" + priority);
                    System.out.println("[Election] Responded ALIVE to " + senderId +
                            " (our priority " + priority + " > " + senderPriority + ")");
                    // Start our own election (we might be the winner)
                    if (!isBroker) {
                        new Thread(() -> startElection(), "Election-" + nodeId).start();
                    }
                }
                break;

            case "ALIVE":
                knownNodes.put(senderId, senderPriority);
                // Higher-priority node is alive — just wait
                break;

            case "COORDINATOR":
                if (parts.length >= 4) {
                    int brokerTcpPort = Integer.parseInt(parts[3]);
                    knownNodes.put(senderId, senderPriority);
                    currentBrokerId = senderId;
                    currentBrokerTcpPort = brokerTcpPort;
                    isBroker = false;
                    lastBrokerHeartbeat = System.currentTimeMillis();

                    System.out.println("[Election] Broker is: " + senderId +
                            " (priority=" + senderPriority + ", tcpPort=" + brokerTcpPort + ")");

                    // Start monitoring broker heartbeats
                    startHeartbeatMonitor();

                    if (listener != null) {
                        listener.onBrokerDiscovered(senderId, null, brokerTcpPort);
                    }
                }
                break;

            case "HEARTBEAT":
                if (senderId.equals(currentBrokerId)) {
                    lastBrokerHeartbeat = System.currentTimeMillis();
                }
                // Also handle broker IP from heartbeat
                if (parts.length >= 4) {
                    currentBrokerIp = parts[3];
                }
                break;

            case "DISCOVER_BROKER":
                if (isBroker) {
                    try {
                        String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
                        udp.send("BROKER_HERE|" + nodeId + "|" + priority +
                                "|" + localIp + "|" + tcpPort);
                    } catch (Exception e) {
                        System.err.println("[Election] Error responding to DISCOVER_BROKER: " + e.getMessage());
                    }
                }
                break;

            case "BROKER_HERE":
                // Not used by grid nodes, but Android discovery will use this
                break;
        }
    }

    /**
     * Start sending periodic heartbeat as the broker.
     */
    private void startBrokerHeartbeat() {
        if (brokerHeartbeatSender != null) {
            brokerHeartbeatSender.shutdown();
        }
        brokerHeartbeatSender = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BrokerHeartbeat");
            t.setDaemon(true);
            return t;
        });
        brokerHeartbeatSender.scheduleAtFixedRate(() -> {
            if (isBroker) {
                try {
                    String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
                    udp.send("HEARTBEAT|" + nodeId + "|" + priority + "|" + localIp);
                } catch (Exception e) {
                    System.err.println("[Election] Heartbeat send error: " + e.getMessage());
                }
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Start monitoring broker heartbeats (for worker nodes).
     */
    private void startHeartbeatMonitor() {
        if (heartbeatMonitor != null) {
            heartbeatMonitor.shutdown();
        }
        heartbeatMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HeartbeatMonitor");
            t.setDaemon(true);
            return t;
        });
        heartbeatMonitor.scheduleAtFixedRate(() -> {
            if (!isBroker && currentBrokerId != null) {
                long elapsed = System.currentTimeMillis() - lastBrokerHeartbeat;
                if (elapsed > HEARTBEAT_TIMEOUT_MS) {
                    System.out.println("[Election] ⚠ Broker " + currentBrokerId +
                            " heartbeat timeout (" + (elapsed / 1000) + "s). Triggering re-election...");
                    currentBrokerId = null;
                    currentBrokerIp = null;
                    currentBrokerTcpPort = -1;

                    if (listener != null) {
                        listener.onBrokerLost();
                    }

                    // Trigger re-election
                    new Thread(() -> startElection(), "ReElection-" + nodeId).start();
                }
            }
        }, HEARTBEAT_TIMEOUT_MS, 5000, TimeUnit.MILLISECONDS);
    }

    // --- Getters ---

    public boolean isBroker() {
        return isBroker;
    }

    public String getCurrentBrokerId() {
        return currentBrokerId;
    }

    public String getCurrentBrokerIp() {
        return currentBrokerIp;
    }

    public int getCurrentBrokerTcpPort() {
        return currentBrokerTcpPort;
    }

    /**
     * Shutdown all election-related threads.
     */
    public void shutdown() {
        if (heartbeatMonitor != null)
            heartbeatMonitor.shutdown();
        if (brokerHeartbeatSender != null)
            brokerHeartbeatSender.shutdown();
    }
}
