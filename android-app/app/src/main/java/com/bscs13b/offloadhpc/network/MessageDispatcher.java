package com.bscs13b.offloadhpc.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Routes incoming JSON messages from the Broker to the appropriate
 * MessageListener callback based on the "type" field.
 */
public class MessageDispatcher {

    private static final String TAG = "MessageDispatcher";
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MessageListener listener;

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    public void removeListener() {
        this.listener = null;
    }

    /**
     * Parse a raw JSON line from the Broker and dispatch to the listener.
     */
    public void dispatch(String jsonLine) {
        if (jsonLine == null || jsonLine.trim().isEmpty())
            return;

        try {
            JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();
            String type = obj.get("type").getAsString();

            if (listener == null) {
                Log.w(TAG, "No listener registered, dropping message: " + type);
                return;
            }

            switch (type) {
                case "JOB_ACK":
                    String ackJobId = obj.get("jobId").getAsString();
                    int totalSubTasks = obj.get("totalSubTasks").getAsInt();
                    String ackStatus = obj.get("status").getAsString();
                    mainHandler.post(() -> listener.onJobAck(ackJobId, totalSubTasks, ackStatus));
                    break;

                case "PROGRESS_UPDATE":
                    String progressJobId = obj.get("jobId").getAsString();
                    int completed = obj.get("completedSubTasks").getAsInt();
                    int total = obj.get("totalSubTasks").getAsInt();
                    mainHandler.post(() -> listener.onProgressUpdate(progressJobId, completed, total));
                    break;

                case "JOB_RESULT":
                    String resultJobId = obj.get("jobId").getAsString();
                    String resultStatus = obj.get("status").getAsString();
                    // Pass the full result object as JSON string for flexible parsing
                    JsonObject resultObj = obj.getAsJsonObject("result");
                    String resultJson = resultObj != null ? resultObj.toString() : "{}";
                    // If FAILED, include error message in the result
                    if ("FAILED".equals(resultStatus) && obj.has("error")) {
                        resultJson = "{\"error\":\"" + obj.get("error").getAsString() + "\"}";
                    }
                    final String finalResultJson = resultJson;
                    mainHandler.post(() -> listener.onJobResult(resultJobId, resultStatus, finalResultJson));
                    break;

                case "ERROR":
                    String errorMsg = obj.has("message") ? obj.get("message").getAsString() : "Unknown server error";
                    mainHandler.post(() -> listener.onConnectionError("Server: " + errorMsg));
                    break;

                default:
                    Log.w(TAG, "Unknown message type: " + type);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing message: " + jsonLine, e);
        }
    }
}

