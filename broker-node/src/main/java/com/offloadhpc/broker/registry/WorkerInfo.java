package com.offloadhpc.broker.registry;

/**
 * POJO representing a registered worker node.
 */
public class WorkerInfo {

    public enum Status {
        AVAILABLE, BUSY
    }

    private final String workerId;
    private final String ip;
    private final int rmiPort;
    private volatile Status status;

    public WorkerInfo(String workerId, String ip, int rmiPort) {
        this.workerId = workerId;
        this.ip = ip;
        this.rmiPort = rmiPort;
        this.status = Status.AVAILABLE;
    }

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

    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Build RMI lookup URL for this worker.
     */
    public String getRmiUrl() {
        return "rmi://" + ip + ":" + rmiPort + "/WorkerService";
    }

    @Override
    public String toString() {
        return "WorkerInfo{id='" + workerId + "', ip='" + ip +
                "', rmiPort=" + rmiPort + ", status=" + status + "}";
    }
}
