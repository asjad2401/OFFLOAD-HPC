package com.offloadhpc.broker.model;

/**
 * Base class for all sub-tasks dispatched to workers.
 */
public abstract class SubTask {

    private final String subTaskId;
    private String workerId;

    public SubTask(String subTaskId) {
        this.subTaskId = subTaskId;
    }

    public String getSubTaskId() {
        return subTaskId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }
}
