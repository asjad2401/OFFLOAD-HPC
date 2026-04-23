package com.offloadhpc.broker.registry;

/**
 * POJO representing a registered worker node.
 * v2.0 — extended with capability fields and heartbeat tracking.
 */
public class WorkerInfo {

    public enum Status {
        AVAILABLE, BUSY
    }

    private final String workerId;
    private final String ip;
    private final int rmiPort;
    private volatile Status status;

    // v2.0 capability fields
    private int cpuCores;
    private long availableMemoryMB;
    private double cpuScore; // composite capability score

    // v2.0 heartbeat tracking
    private volatile long lastHeartbeat;

    public WorkerInfo(String workerId, String ip, int rmiPort) {
        this.workerId = workerId;
        this.ip = ip;
        this.rmiPort = rmiPort;
        this.status = Status.AVAILABLE;
        this.lastHeartbeat = System.currentTimeMillis();
        // Default capabilities (will be overridden by actual report)
        this.cpuCores = 1;
        this.availableMemoryMB = 512;
        this.cpuScore = 1.0;
    }

    public WorkerInfo(String workerId, String ip, int rmiPort,
            int cpuCores, long availableMemoryMB) {
        this(workerId, ip, rmiPort);
        this.cpuCores = cpuCores;
        this.availableMemoryMB = availableMemoryMB;
        this.cpuScore = computeScore(cpuCores, availableMemoryMB);
    }

    // --- Getters ---

    public String getWorkerId() {
        return workerId;
    }

    public String getIp() {
        return ip;
    }

    public int getRmiPort() {
        return rmiPort;
    }

    public Status getStatus() {
        return status;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    public long getAvailableMemoryMB() {
        return availableMemoryMB;
    }

    public double getCpuScore() {
        return cpuScore;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    // --- Setters ---

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setCapabilities(int cpuCores, long availableMemoryMB) {
        this.cpuCores = cpuCores;
        this.availableMemoryMB = availableMemoryMB;
        this.cpuScore = computeScore(cpuCores, availableMemoryMB);
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * Build RMI lookup URL for this worker.
     */
    public String getRmiUrl() {
        return "rmi://" + ip + ":" + rmiPort + "/WorkerService";
    }

    /**
     * Compute a composite capability score.
     * Higher cores and memory → higher score.
     * Score = cpuCores * (1 + log2(memoryMB / 512))
     */
    private static double computeScore(int cpuCores, long memoryMB) {
        double memFactor = 1.0 + Math.log(Math.max(1, memoryMB) / 512.0) / Math.log(2);
        return cpuCores * Math.max(0.5, memFactor);
    }

    @Override
    public String toString() {
        return "WorkerInfo{id='" + workerId + "', ip='" + ip +
                "', rmiPort=" + rmiPort + ", status=" + status +
                ", cores=" + cpuCores + ", memMB=" + availableMemoryMB +
                ", score=" + String.format("%.2f", cpuScore) + "}";
    }
}
