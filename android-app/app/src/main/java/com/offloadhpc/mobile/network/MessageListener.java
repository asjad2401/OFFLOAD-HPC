package com.offloadhpc.mobile.network;

/**
 * Listener interface for incoming messages from the Broker.
 * Implemented by ProgressActivity to receive real-time updates.
 */
public interface MessageListener {
    void onJobAck(String jobId, int totalSubTasks, String status);

    void onProgressUpdate(String jobId, int completedSubTasks, int totalSubTasks);

    void onJobResult(String jobId, String status, String resultJson);

    void onConnectionError(String errorMessage);
}
