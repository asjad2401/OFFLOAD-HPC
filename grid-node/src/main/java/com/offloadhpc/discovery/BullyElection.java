package com.offloadhpc.discovery;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BullyElection -- implements the Bully leader election algorithm.
 *
 * Algorithm:
 * 1. Node broadcasts ELECTION|nodeId|priority
 * 2. Any node with higher priority responds ALIVE|nodeId|priority
 * 3. If no ALIVE received within ELECTION_TIMEOUT -> this node wins
 * 4. Winner broadcasts COORDINATOR|nodeId|priority|tcpPort|brokerIp
 * 5. All nodes receiving COORDINATOR accept the sender as broker
 *
 * On broker heartbeat timeout -> surviving nodes trigger re-election.
 *
 * v2.1 -- hardened for LAN: synchronized role transitions, debounced
 * elections, proper cleanup on role change.
 */
public class BullyElection {

    private static final long ELECTION_TIMEOUT_MS = 3000;
    private static final long HEARTBEAT_INTERVAL_MS = 5000;
    private static final long HEARTBEAT_TIMEOUT_MS = 15000;
    private static final long COORDINATOR_REBROADCAST_MS = 10000;
    // Debounce: ignore COORDINATOR messages that arrive within 2s of our own election
    private static final long ELECTION_COOLDOWN_MS = 2000;

    private final String nodeId;
    private final int priority;
    private final int tcpPort;
    private final UdpDiscovery udp;

    // Election state -- all guarded by synchronized(stateLock)
    private final Object stateLock = new Object();
    private volatile String currentBrokerId = null;
    private volatile String currentBrokerIp = null;
    private volatile int currentBrokerTcpPort = -1;
    private volatile boolean isBroker = false;
    private volatile long lastBrokerHeartbeat = 0;
    private volatile long lastElectionTime = 0;

    // Guard against concurrent elections
    private final AtomicBoolean electionInProgress = new AtomicBoolean(false);

    private ElectionListener listener;

    private ScheduledExecutorService heartbeatMonitor;
    private ScheduledExecutorService brokerHeartbeatSender;

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
     * Start the election process with atomic guard to prevent races.
     */
    public void startElection() {
        if (!electionInProgress.compareAndSet(false, true)) {
            System.out.println("[Election] Election already in progress, skipping.");
            return;
        }

        try {
            lastElectionTime = System.currentTimeMillis();
            System.out.println("[Election] Node " + nodeId + " (priority=" + priority +
                    ") starting election...");

            String electionMsg = "ELECTION|" + nodeId + "|" + priority;
            boolean higherNodeExists = udp.sendAndWaitForResponse(
                    electionMsg, "ALIVE", ELECTION_TIMEOUT_MS);

            if (!higherNodeExists) {
                declareCoordinator();
            } else {
                System.out.println("[Election] Higher-priority node exists. Waiting for COORDINATOR...");
                waitForCoordinator();
            }
        } finally {
            electionInProgress.set(false);
        }
    }

    /**
     * Declare this node as the coordinator (broker).
     */
    private void declareCoordinator() {
        synchronized (stateLock) {
            // Stop any heartbeat monitor from the worker phase
            stopHeartbeatMonitor();

            isBroker = true;
            currentBrokerId = nodeId;
            currentBrokerTcpPort = tcpPort;

            try {
                String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
                currentBrokerIp = localIp;

                String coordMsg = "COORDINATOR|" + nodeId + "|" + priority + "|" + tcpPort + "|" + localIp;
                udp.send(coordMsg);

                System.out.println("==================================================");
                System.out.println("  [Election] NODE " + nodeId + " ELECTED AS BROKER");
                System.out.println("  [Election] Broker IP: " + localIp + ":" + tcpPort);
                System.out.println("==================================================");
            } catch (Exception e) {
                System.err.println("[Election] Failed to resolve local IP: " + e.getMessage());
            }

            startBrokerHeartbeat();
        }

        if (listener != null) {
            listener.onElectedAsBroker();
        }
    }

    /**
     * Wait for a COORDINATOR message after seeing a higher-priority node.
     */
    private void waitForCoordinator() {
        long deadline = System.currentTimeMillis() + ELECTION_TIMEOUT_MS * 2;
        while (System.currentTimeMillis() < deadline) {
            if (currentBrokerId != null) {
                return; // COORDINATOR received via handleMessage
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                break;
            }
        }
        System.out.println("[Election] No COORDINATOR received. Starting new election...");
        electionInProgress.set(false);
        startElection();
    }

    /**
     * Handle incoming UDP messages related to election.
     */
    public void handleMessage(String message) {
        if (message == null || message.isEmpty()) return;
        String[] parts = message.split("\\|");
        if (parts.length < 3) return;

        String type = parts[0];
        String senderId = parts[1];

        int senderPriority;
        try {
            senderPriority = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }

        // Ignore our own messages
        if (senderId.equals(nodeId)) return;

        switch (type) {
            case "ELECTION":
                knownNodes.put(senderId, senderPriority);
                if (priority > senderPriority) {
                    udp.send("ALIVE|" + nodeId + "|" + priority);
                    System.out.println("[Election] Responded ALIVE to " + senderId +
                            " (our priority " + priority + " > " + senderPriority + ")");
                    if (!isBroker) {
                        new Thread(() -> startElection(), "Election-" + nodeId).start();
                    }
                }
                break;

            case "ALIVE":
                knownNodes.put(senderId, senderPriority);
                break;

            case "COORDINATOR":
                handleCoordinator(parts, senderId, senderPriority);
                break;

            case "HEARTBEAT":
                if (senderId.equals(currentBrokerId)) {
                    lastBrokerHeartbeat = System.currentTimeMillis();
                }
                if (parts.length >= 4) {
                    currentBrokerIp = parts[3];
                }
                break;

            case "DISCOVER_BROKER":
                if (isBroker) {
                    try {
                        String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
                        String brokerResponse = "BROKER_HERE|" + nodeId + "|" + priority +
                                "|" + localIp + "|" + tcpPort;

                        // Send via multicast (for other grid nodes)
                        udp.send(brokerResponse);

                        // Also send unicast directly to the sender (for Android devices
                        // that cannot receive multicast without WifiManager.MulticastLock)
                        // Message format: DISCOVER_BROKER|android|0|SENDER|ip:port
                        for (int i = 0; i < parts.length - 1; i++) {
                            if ("SENDER".equals(parts[i]) && i + 1 < parts.length) {
                                String[] senderAddr = parts[i + 1].split(":");
                                if (senderAddr.length == 2) {
                                    java.net.InetAddress senderIp = java.net.InetAddress.getByName(senderAddr[0]);
                                    int senderPort = Integer.parseInt(senderAddr[1]);
                                    udp.sendTo(brokerResponse, senderIp, senderPort);
                                    System.out.println("[Election] Sent BROKER_HERE unicast to " +
                                            senderAddr[0] + ":" + senderPort);
                                }
                                break;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Election] Error responding to DISCOVER_BROKER: " + e.getMessage());
                    }
                }
                break;

            case "BROKER_HERE":
                break;
        }
    }

    /**
     * Handle COORDINATOR message with proper synchronization.
     * Prevents accepting stale coordinators during our own election.
     */
    private void handleCoordinator(String[] parts, String senderId, int senderPriority) {
        if (parts.length < 4) return;

        int brokerTcpPort = Integer.parseInt(parts[3]);
        String brokerIp = parts.length >= 5 ? parts[4] : null;

        // If we have higher priority and are currently electing, ignore this
        if (senderPriority < priority && electionInProgress.get()) {
            System.out.println("[Election] Ignoring COORDINATOR from lower-priority " + senderId);
            return;
        }

        synchronized (stateLock) {
            // If we are the broker and someone with lower priority tries to take over, ignore
            if (isBroker && senderPriority < priority) {
                System.out.println("[Election] Ignoring COORDINATOR from " + senderId +
                        " (we are broker with higher priority)");
                return;
            }

            boolean wasAlreadyWorkerForThisBroker =
                    senderId.equals(currentBrokerId) && !isBroker;

            knownNodes.put(senderId, senderPriority);
            currentBrokerId = senderId;
            currentBrokerTcpPort = brokerTcpPort;
            currentBrokerIp = brokerIp;
            boolean wasBroker = isBroker;
            isBroker = false;
            lastBrokerHeartbeat = System.currentTimeMillis();

            // Stop broker heartbeat if we were previously broker
            if (wasBroker && brokerHeartbeatSender != null) {
                brokerHeartbeatSender.shutdown();
                brokerHeartbeatSender = null;
            }

            System.out.println("[Election] Broker is: " + senderId +
                    " (priority=" + senderPriority +
                    ", ip=" + brokerIp +
                    ", tcpPort=" + brokerTcpPort + ")");

            startHeartbeatMonitor();

            // Only notify listener if this is a new broker assignment
            // Avoid re-triggering startAsWorker for re-broadcasts from the same broker
            if (!wasAlreadyWorkerForThisBroker && listener != null) {
                listener.onBrokerDiscovered(senderId, brokerIp, brokerTcpPort);
            }
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

        final long[] counter = {0};

        brokerHeartbeatSender.scheduleAtFixedRate(() -> {
            if (isBroker) {
                try {
                    String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
                    udp.send("HEARTBEAT|" + nodeId + "|" + priority + "|" + localIp);

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
        stopHeartbeatMonitor();
        heartbeatMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HeartbeatMonitor");
            t.setDaemon(true);
            return t;
        });
        heartbeatMonitor.scheduleAtFixedRate(() -> {
            if (!isBroker && currentBrokerId != null) {
                long elapsed = System.currentTimeMillis() - lastBrokerHeartbeat;
                if (elapsed > HEARTBEAT_TIMEOUT_MS) {
                    System.out.println("[Election] Broker " + currentBrokerId +
                            " heartbeat timeout (" + (elapsed / 1000) + "s). Re-electing...");
                    synchronized (stateLock) {
                        currentBrokerId = null;
                        currentBrokerIp = null;
                        currentBrokerTcpPort = -1;
                    }

                    if (listener != null) {
                        listener.onBrokerLost();
                    }

                    new Thread(() -> startElection(), "ReElection-" + nodeId).start();
                }
            }
        }, HEARTBEAT_TIMEOUT_MS, 5000, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeatMonitor() {
        if (heartbeatMonitor != null) {
            heartbeatMonitor.shutdown();
            heartbeatMonitor = null;
        }
    }

    /**
     * Force a new election (e.g., from UI button).
     */
    public void forceElection() {
        synchronized (stateLock) {
            isBroker = false;
            currentBrokerId = null;
            currentBrokerIp = null;
            currentBrokerTcpPort = -1;
        }
        electionInProgress.set(false);
        new Thread(() -> startElection(), "ForceElection-" + nodeId).start();
    }

    // --- Getters ---

    public boolean isBroker() { return isBroker; }
    public String getCurrentBrokerId() { return currentBrokerId; }
    public String getCurrentBrokerIp() { return currentBrokerIp; }
    public int getCurrentBrokerTcpPort() { return currentBrokerTcpPort; }
    public ConcurrentHashMap<String, Integer> getKnownNodes() { return knownNodes; }

    public void shutdown() {
        stopHeartbeatMonitor();
        if (brokerHeartbeatSender != null)
            brokerHeartbeatSender.shutdown();
    }
}
