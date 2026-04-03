package com.offloadhpc.mobile.model;

import com.google.gson.annotations.SerializedName;

/**
 * POJO for incoming PROGRESS_UPDATE messages from the Broker.
 * Used to update the progress bar in ProgressActivity.
 */
public class ProgressUpdate {

    @SerializedName("type")
    private String type;

    @SerializedName("jobId")
    private String jobId;

    @SerializedName("completedSubTasks")
    private int completedSubTasks;

    @SerializedName("totalSubTasks")
    private int totalSubTasks;

    public ProgressUpdate() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public int getCompletedSubTasks() {
        return completedSubTasks;
    }

    public void setCompletedSubTasks(int completedSubTasks) {
        this.completedSubTasks = completedSubTasks;
    }

    public int getTotalSubTasks() {
        return totalSubTasks;
    }

    public void setTotalSubTasks(int totalSubTasks) {
        this.totalSubTasks = totalSubTasks;
    }
}
