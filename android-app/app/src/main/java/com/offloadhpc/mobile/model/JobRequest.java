package com.offloadhpc.mobile.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * POJO for outgoing JOB_SUBMIT messages sent to the Broker.
 * Supports both MatMul and Hash Crack job types.
 */
public class JobRequest {

    @SerializedName("type")
    private String type = "JOB_SUBMIT";

    @SerializedName("jobId")
    private String jobId;

    @SerializedName("jobType")
    private String jobType;

    @SerializedName("payload")
    private Map<String, Object> payload;

    public JobRequest() {
    }

    public JobRequest(String jobId, String jobType, Map<String, Object> payload) {
        this.jobId = jobId;
        this.jobType = jobType;
        this.payload = payload;
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

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
