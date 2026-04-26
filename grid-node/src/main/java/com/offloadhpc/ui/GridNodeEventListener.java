package com.offloadhpc.ui;

/**
 * Callback interface for grid node state changes.
 * Consumed by the Swing UI and/or CLI logging.
 */
public interface GridNodeEventListener {

    /** Called when the node's role changes (e.g., became BROKER or WORKER). */
    void onRoleChanged(String role, String brokerId, String brokerIp, int brokerTcpPort);

    /** Called when a worker registers with the broker. */
    void onWorkerRegistered(String workerId, String ip, int cores, long memMB);

    /** Called when a worker is evicted (heartbeat timeout). */
    void onWorkerLost(String workerId);

    /** Called when a job is received by the broker. */
    void onJobReceived(String jobId, String jobType);

    /** Called when a job completes. */
    void onJobCompleted(String jobId, String jobType, String status);

    /** General log message for the event log. */
    void onLogMessage(String message);
}
