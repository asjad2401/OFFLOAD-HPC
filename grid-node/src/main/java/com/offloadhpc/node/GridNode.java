package com.offloadhpc.node;

import com.offloadhpc.broker.registry.NodeRegistry;
import com.offloadhpc.broker.server.BrokerServer;
import com.offloadhpc.discovery.BullyElection;
import com.offloadhpc.discovery.UdpDiscovery;
import com.offloadhpc.ui.GridNodeEventListener;
import com.offloadhpc.ui.GridNodeUI;
import com.offloadhpc.worker.WorkerRunner;

import javax.swing.*;
import java.net.InetAddress;

/**
 * GridNode — unified entry point for OFFLOAD-HPC v2.0.
 *
 * Every machine runs a GridNode. On startup:
 * 1. Start UDP multicast discovery
 * 2. Run Bully election algorithm
 * 3. If elected → start BrokerServer + embedded WorkerRunner
 * 4. If not elected → start WorkerRunner only, register with broker
 *
 * On broker failure → re-election triggers automatically.
 *
 * Usage: java GridNode <nodeId> [priority] [tcpPort] [rmiPort] [--headless]
 * nodeId - unique node identifier (e.g. n1, n2)
 * priority - election priority (higher wins). Default: hash of nodeId
 * tcpPort - TCP port for broker role. Default: 9000
 * rmiPort - RMI port for worker role. Default: 1099
 * --headless - disable Swing UI, CLI only
 */
public class GridNode {

    private final NodeConfig config;
    private final UdpDiscovery udp;
    private final BullyElection election;

    private BrokerServer brokerServer;
    private WorkerRunner workerRunner;

    // Optional event listener (UI or null for headless)
    private GridNodeEventListener eventListener;

    public GridNode(NodeConfig config) {
        this.config = config;
        this.udp = new UdpDiscovery(config.getMulticastGroup(), config.getMulticastPort());
        this.election = new BullyElection(
                config.getNodeId(), config.getPriority(),
                config.getTcpPort(), udp);
    }

    public void setEventListener(GridNodeEventListener listener) {
        this.eventListener = listener;
    }

    public BullyElection getElection() {
        return election;
    }

    public BrokerServer getBrokerServer() {
        return brokerServer;
    }

    /**
     * Start the GridNode: discover peers, elect leader, activate role.
     */
    public void start() throws Exception {
        System.out.println("==============================================");
        System.out.println("   OFFLOAD-HPC GridNode v2.0");
        System.out.println("   Node: " + config.getNodeId());
        System.out.println("   Priority: " + config.getPriority());
        System.out.println("   TCP Port: " + config.getTcpPort());
        System.out.println("   RMI Port: " + config.getRmiPort());
        System.out.println("==============================================");

        String localIp = InetAddress.getLocalHost().getHostAddress();
        System.out.println("[GridNode] Local IP: " + localIp);

        if (eventListener != null) {
            eventListener.onLogMessage("GridNode starting on " + localIp);
        }

        // 1. Start UDP discovery listener
        udp.startListening(message -> election.handleMessage(message));
        System.out.println("[GridNode] UDP discovery started");

        // 2. Brief delay to allow other nodes to start listening
        Thread.sleep(1000);

        // 3. Set election listener
        election.setListener(new BullyElection.ElectionListener() {
            @Override
            public void onElectedAsBroker() {
                startAsBroker(localIp);
                if (eventListener != null) {
                    eventListener.onRoleChanged("BROKER", config.getNodeId(), localIp, config.getTcpPort());
                }
            }

            @Override
            public void onBrokerDiscovered(String brokerId, String brokerIp, int brokerTcpPort) {
                // Use the actual broker IP from the COORDINATOR message
                if (brokerIp == null || brokerIp.isEmpty()) {
                    // Fallback: try getting it from election state or use localIp
                    brokerIp = election.getCurrentBrokerIp();
                    if (brokerIp == null) {
                        brokerIp = localIp; // last resort for same-machine testing
                    }
                }
                startAsWorker(brokerIp, brokerTcpPort);
                if (eventListener != null) {
                    eventListener.onRoleChanged("WORKER", brokerId, brokerIp, brokerTcpPort);
                }
            }

            @Override
            public void onBrokerLost() {
                System.out.println("[GridNode] Broker lost! Stopping current worker...");
                stopWorker();
                if (eventListener != null) {
                    eventListener.onLogMessage("⚠ Broker lost! Re-election starting...");
                }
                // Re-election will be triggered by BullyElection
            }
        });

        // 4. Run election
        election.startElection();

        // 5. Keep main thread alive
        System.out.println("[GridNode] Running. Press Ctrl+C to stop.");
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "ShutdownHook"));
        Thread.currentThread().join();
    }

    /**
     * Activate broker role: start BrokerServer + embedded worker.
     */
    private void startAsBroker(String localIp) {
        try {
            // Stop any existing worker/broker from a previous election
            stopWorker();
            stopBroker();
            Thread.sleep(1000); // let ports release

            NodeRegistry registry = new NodeRegistry();
            // Wire registry to the UI event listener
            registry.setEventListener(eventListener);

            brokerServer = new BrokerServer(config.getTcpPort(), registry);
            // Wire broker server to the UI event listener
            brokerServer.setEventListener(eventListener);

            // Start broker server in background thread
            Thread brokerThread = new Thread(() -> brokerServer.start(), "BrokerServer");
            brokerThread.setDaemon(true);
            brokerThread.start();

            // Wait a moment for server to start
            Thread.sleep(500);

            // Start embedded worker (registers with self)
            workerRunner = new WorkerRunner(
                    config.getNodeId() + "-worker",
                    localIp, config.getTcpPort(), config.getRmiPort());
            workerRunner.start();

            System.out.println("[GridNode] Broker + embedded worker active");
        } catch (Exception e) {
            System.err.println("[GridNode] Failed to start broker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Activate worker role: connect to discovered broker.
     */
    private void startAsWorker(String brokerIp, int brokerTcpPort) {
        try {
            // Stop any existing worker
            stopWorker();

            workerRunner = new WorkerRunner(
                    config.getNodeId(),
                    brokerIp, brokerTcpPort, config.getRmiPort());
            workerRunner.start();

            System.out.println("[GridNode] Worker active, broker at " + brokerIp + ":" + brokerTcpPort);
        } catch (Exception e) {
            System.err.println("[GridNode] Failed to start worker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopWorker() {
        if (workerRunner != null) {
            workerRunner.stop();
            workerRunner = null;
        }
    }

    private void stopBroker() {
        if (brokerServer != null) {
            brokerServer.stop();
            brokerServer = null;
        }
    }

    /**
     * Graceful shutdown.
     */
    public void shutdown() {
        System.out.println("[GridNode] Shutting down...");
        if (eventListener != null) {
            eventListener.onLogMessage("Node shutting down...");
        }
        election.shutdown();
        udp.stop();
        if (workerRunner != null)
            workerRunner.stop();
        if (brokerServer != null)
            brokerServer.stop();
    }

    // ── Main ───────────────────────────────────────────────────────

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: GridNode <nodeId> [priority] [tcpPort] [rmiPort] [--headless]");
            System.err.println("  nodeId   - unique node identifier (e.g. n1, n2)");
            System.err.println("  priority - election priority, higher wins (default: hash of nodeId)");
            System.err.println("  tcpPort  - TCP port for broker role (default: 9000)");
            System.err.println("  rmiPort  - RMI port for worker role (default: 1099)");
            System.err.println("  --headless - run without GUI (CLI only)");
            System.err.println();
            System.err.println("Examples:");
            System.err.println("  GridNode n1 10");
            System.err.println("  GridNode n2 5 9000 1100");
            System.err.println("  GridNode n1 10 9000 1099 --headless");
            System.exit(1);
        }

        String nodeId = args[0];
        int priority = args.length > 1 ? Integer.parseInt(args[1]) : Math.abs(nodeId.hashCode() % 100);
        int tcpPort = args.length > 2 ? Integer.parseInt(args[2]) : 9000;
        int rmiPort = args.length > 3 ? Integer.parseInt(args[3]) : 1099;

        // Check for --headless flag
        boolean headless = false;
        for (String arg : args) {
            if ("--headless".equalsIgnoreCase(arg) || "--no-ui".equalsIgnoreCase(arg)) {
                headless = true;
                break;
            }
        }

        NodeConfig config = new NodeConfig(nodeId, priority, tcpPort, rmiPort);
        GridNode node = new GridNode(config);

        if (!headless) {
            // Use default cross-platform LAF (Metal) — it respects custom colors
            // Windows native LAF ignores setBackground/setForeground on buttons

            // Launch UI on EDT
            String localIp;
            try {
                localIp = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                localIp = "unknown";
            }

            final String ip = localIp;
            SwingUtilities.invokeLater(() -> {
                GridNodeUI ui = new GridNodeUI(nodeId, priority, tcpPort, rmiPort, ip);
                node.setEventListener(ui);

                // Wire control callbacks
                ui.setOnForceElection(() -> node.getElection().forceElection());
                ui.setOnStopNode(() -> node.shutdown());

                ui.setVisible(true);
            });
        }

        try {
            node.start();
        } catch (Exception e) {
            System.err.println("[GridNode] Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
