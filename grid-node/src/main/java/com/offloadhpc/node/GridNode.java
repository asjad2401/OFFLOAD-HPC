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
 * GridNode -- unified entry point for OFFLOAD-HPC v2.1.
 *
 * v2.1 -- hardened role transitions: synchronized start/stop of
 * broker and worker components, prevents race conditions during
 * re-elections on LAN.
 */
public class GridNode {

    private final NodeConfig config;
    private final UdpDiscovery udp;
    private final BullyElection election;

    private BrokerServer brokerServer;
    private WorkerRunner workerRunner;

    // Synchronizes role transitions (startAsBroker / startAsWorker)
    private final Object roleLock = new Object();
    private volatile String currentRole = "NONE";

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
        System.out.println("   OFFLOAD-HPC GridNode v2.1");
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
                new Thread(() -> {
                    startAsBroker(localIp);
                    if (eventListener != null) {
                        eventListener.onRoleChanged("BROKER", config.getNodeId(), localIp, config.getTcpPort());
                    }
                }, "RoleTransition-Broker").start();
            }

            @Override
            public void onBrokerDiscovered(String brokerId, String brokerIp, int brokerTcpPort) {
                String ip = brokerIp;
                if (ip == null || ip.isEmpty()) {
                    ip = election.getCurrentBrokerIp();
                    if (ip == null) ip = localIp;
                }
                final String finalIp = ip;
                new Thread(() -> {
                    startAsWorker(finalIp, brokerTcpPort);
                    if (eventListener != null) {
                        eventListener.onRoleChanged("WORKER", brokerId, finalIp, brokerTcpPort);
                    }
                }, "RoleTransition-Worker").start();
            }

            @Override
            public void onBrokerLost() {
                System.out.println("[GridNode] Broker lost! Cleaning up...");
                synchronized (roleLock) {
                    stopWorkerInternal();
                    currentRole = "NONE";
                }
                if (eventListener != null) {
                    eventListener.onLogMessage("[!] Broker lost! Re-election starting...");
                }
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
     * Synchronized to prevent race with concurrent role transitions.
     */
    private void startAsBroker(String localIp) {
        synchronized (roleLock) {
            try {
                System.out.println("[GridNode] Transitioning to BROKER role...");

                // Stop any existing components
                stopWorkerInternal();
                stopBrokerInternal();
                Thread.sleep(1500); // let ports release

                NodeRegistry registry = new NodeRegistry();
                registry.setEventListener(eventListener);

                brokerServer = new BrokerServer(config.getTcpPort(), registry);
                brokerServer.setEventListener(eventListener);

                Thread brokerThread = new Thread(() -> brokerServer.start(), "BrokerServer");
                brokerThread.setDaemon(true);
                brokerThread.start();

                Thread.sleep(500); // wait for ServerSocket to bind

                // Start embedded worker
                workerRunner = new WorkerRunner(
                        config.getNodeId() + "-worker",
                        localIp, config.getTcpPort(), config.getRmiPort());
                workerRunner.setEventListener(eventListener);
                workerRunner.start();

                currentRole = "BROKER";
                System.out.println("[GridNode] BROKER + embedded worker active");
            } catch (Exception e) {
                System.err.println("[GridNode] Failed to start broker: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Activate worker role: connect to discovered broker.
     */
    private void startAsWorker(String brokerIp, int brokerTcpPort) {
        synchronized (roleLock) {
            try {
                System.out.println("[GridNode] Transitioning to WORKER role (broker=" +
                        brokerIp + ":" + brokerTcpPort + ")...");

                // Stop existing components
                stopWorkerInternal();
                stopBrokerInternal();
                Thread.sleep(500);

                workerRunner = new WorkerRunner(
                        config.getNodeId(),
                        brokerIp, brokerTcpPort, config.getRmiPort());
                workerRunner.setEventListener(eventListener);
                workerRunner.start();

                currentRole = "WORKER";
                System.out.println("[GridNode] WORKER active, broker at " + brokerIp + ":" + brokerTcpPort);
            } catch (Exception e) {
                System.err.println("[GridNode] Failed to start worker: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // Internal stop methods (called within roleLock)
    private void stopWorkerInternal() {
        if (workerRunner != null) {
            workerRunner.stop();
            workerRunner = null;
        }
    }

    private void stopBrokerInternal() {
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
        synchronized (roleLock) {
            stopWorkerInternal();
            stopBrokerInternal();
        }
    }

    // -- Main --

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
