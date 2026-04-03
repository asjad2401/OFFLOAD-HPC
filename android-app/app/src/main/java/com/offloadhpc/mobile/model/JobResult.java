package com.offloadhpc.mobile.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * POJO for incoming JOB_RESULT messages from the Broker.
 * The result map contains job-type-specific data:
 * - MatMul: { "matrixC": [[...]] }
 * - Hash Crack: { "crackedValue": "...", "algorithm": "..." }
 */
public class JobResult {

    @SerializedName("type")
    private String type;

    @SerializedName("jobId")
    private String jobId;

    @SerializedName("status")
    private String status;

    @SerializedName("result")
    private Map<String, Object> result;

    public JobResult() {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }
}
