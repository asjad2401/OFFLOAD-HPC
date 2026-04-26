package com.offloadhpc.discovery;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BullyElection — implements the Bully leader election algorithm.
 *
 * Algorithm:
 * 1. Node broadcasts ELECTION|nodeId|priority
 * 2. Any node with higher priority responds ALIVE|nodeId|priority
 * 3. If no ALIVE received within ELECTION_TIMEOUT → this node wins
 * 4. Winner broadcasts COORDINATOR|nodeId|priority|tcpPort|brokerIp
 * 5. All nodes receiving COORDINATOR accept the sender as broker
 *
 * On broker heartbeat timeout → surviving nodes trigger re-election.
 */
public class BullyElection {

    private static final long ELECTION_TIMEOUT_MS = 3000; // wait 3s for ALIVE responses
    private static final long HEARTBEAT_INTERVAL_MS = 5000; // broker heartbeats every 5s
    private static final long HEARTBEAT_TIMEOUT_MS = 15000; // 15s without heartbeat → re-elect
    private static final long COORDINATOR_REBROADCAST_MS = 10000; // re-announce every 10s

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

    // Guard against concurrent elections
    private final AtomicBoolean electionInProgress = new AtomicBoolean(false);

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
     * listening. Uses an atomic guard to prevent concurrent election races.
     */
    public void startElection() {
        if (!electionInProgress.compareAndSet(false, true)) {
            System.out.println("[Election] Election already in progress, skipping.");
            return;
        }

        try {
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
        } finally {
            electionInProgress.set(false);
        }
    }

    /**
     * Declare this node as the coordinator (broker).
     * Includes broker IP in the COORDINATOR message for LAN deployment.
     */
    private void declareCoordinator() {
        isBroker = true;
        currentBrokerId = nodeId;
        currentBrokerTcpPort = tcpPort;

        try {
            String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
            currentBrokerIp = localIp;

            // COORDINATOR|nodeId|priority|tcpPort|brokerIp
            String coordMsg = "COORDINATOR|" + nodeId + "|" + priority + "|" + tcpPort + "|" + localIp;
            udp.send(coordMsg);

            System.out.println("==================================================");
            System.out.println("  [Election] ★ NODE " + nodeId + " ELECTED AS BROKER ★");
            System.out.println("  [Election] Broker IP: " + localIp + ":" + tcpPort);
            System.out.println("==================================================");
        } catch (Exception e) {
            System.err.println("[Election] Failed to resolve local IP: " + e.getMessage());
        }

        // Start sending broker heartbeats (also serves as COORDINATOR re-broadcast)
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
        electionInProgress.set(false); // release guard before recursion
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

        int senderPriority;
        try {
            senderPriority = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return; // malformed message
        }

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
                    // v2.0 LAN fix: parse broker IP from message
                    String brokerIp = parts.length >= 5 ? parts[4] : null;

                    knownNodes.put(senderId, senderPriority);
                    currentBrokerId = senderId;
                    currentBrokerTcpPort = brokerTcpPort;
                    currentBrokerIp = brokerIp;
                    isBroker = false;
                    lastBrokerHeartbeat = System.currentTimeMillis();

                    // Stop any broker heartbeat sender if we were previously broker
                    if (brokerHeartbeatSender != null) {
                        brokerHeartbeatSender.shutdown();
                        brokerHeartbeatSender = null;
                    }

                    System.out.println("[Election] Broker is: " + senderId +
                            " (priority=" + senderPriority +
                            ", ip=" + brokerIp +
                            ", tcpPort=" + brokerTcpPort + ")");

                    // Start monitoring broker heartbeats
                    startHeartbeatMonitor();

                    if (listener != null) {
                        listener.onBrokerDiscovered(senderId, brokerIp, brokerTcpPort);
                    }
                }
                break;

            case "HEARTBEAT":
                if (senderId.equals(currentBrokerId)) {
                    lastBrokerHeartbeat = System.currentTimeMillis();
                }
                // Update broker IP from heartbeat
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
     * Also periodically re-broadcasts COORDINATOR for late-joining nodes.
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

        // Track iterations for periodic COORDINATOR re-broadcast
        final long[] counter = {0};

        brokerHeartbeatSender.scheduleAtFixedRate(() -> {
            if (isBroker) {
                try {
                    String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
                    udp.send("HEARTBEAT|" + nodeId + "|" + priority + "|" + localIp);

                    // Re-broadcast COORDINATOR every ~10s for late joiners
                    counter[0]++;
                    if (counter[0] % (COORDINATOR_REBROADCAST_MS / HEARTBEAT_INTERVAL_MS) == 0) {
                        udp.send("COORDINATOR|" + nodeId + "|" + priority + "|" + tcpPort + "|" + localIp);
                    }
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

    /**
     * Force a new election (e.g., from UI button).
     */
    public void forceElection() {
        isBroker = false;
        currentBrokerId = null;
        currentBrokerIp = null;
        currentBrokerTcpPort = -1;
        electionInProgress.set(false);
        new Thread(() -> startElection(), "ForceElection-" + nodeId).start();
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

    public ConcurrentHashMap<String, Integer> getKnownNodes() {
        return knownNodes;
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
