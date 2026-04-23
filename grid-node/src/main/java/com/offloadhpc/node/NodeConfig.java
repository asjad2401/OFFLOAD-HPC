package com.offloadhpc.node;

/**
 * Configuration POJO for a GridNode.
 * Holds identity, priority, and network settings.
 */
public class NodeConfig {

    private final String nodeId;
    private final int priority;
    private final int tcpPort;
    private final int rmiPort;
    private final String multicastGroup;
    private final int multicastPort;

    public NodeConfig(String nodeId, int priority, int tcpPort, int rmiPort) {
        this.nodeId = nodeId;
        this.priority = priority;
        this.tcpPort = tcpPort;
        this.rmiPort = rmiPort;
        this.multicastGroup = "239.1.1.1";
        this.multicastPort = 5000;
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getPriority() {
        return priority;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public int getRmiPort() {
        return rmiPort;
    }

    public String getMulticastGroup() {
        return multicastGroup;
    }

    public int getMulticastPort() {
        return multicastPort;
    }

    @Override
    public String toString() {
        return "NodeConfig{id='" + nodeId + "', priority=" + priority +
                ", tcpPort=" + tcpPort + ", rmiPort=" + rmiPort + "}";
    }
}
