package com.offloadhpc.mobile.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.offloadhpc.mobile.model.JobRequest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

/**
 * Singleton TCP socket client that maintains a persistent connection
 * to the Broker node on port 9000.
 *
 * All network I/O runs on background threads to avoid
 * NetworkOnMainThreadException.
 */
public class SocketClient {

    private static final String TAG = "SocketClient";
    private static SocketClient instance;

    // ── Configurable Broker address ──────────────────────────────────
    // Change these values to match your Broker's IP and port.
    private String brokerHost = "172.28.179.17"; // TODO: Update with your computer's Hotspot IP (from ipconfig) // TODO: Replace with your Laptop's Hotspot IP
    private int brokerPort = 9000;

    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Thread readerThread;
    private volatile boolean running = false;

    private final Gson gson = new Gson();
    private final MessageDispatcher dispatcher = new MessageDispatcher();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ConnectionCallback connectionCallback;

    // ── Callback interface for connection state ─────────────────────
    public interface ConnectionCallback {
        void onConnected();

        void onDisconnected(String reason);

        void onError(String error);
    }

    private SocketClient() {
    }

    public static synchronized SocketClient getInstance() {
        if (instance == null) {
            instance = new SocketClient();
        }
        return instance;
    }

    // ── Configuration ───────────────────────────────────────────────
    public void setBrokerAddress(String host, int port) {
        this.brokerHost = host;
        this.brokerPort = port;
    }

    public String getBrokerHost() {
        return brokerHost;
    }

    public int getBrokerPort() {
        return brokerPort;
    }

    public void setConnectionCallback(ConnectionCallback callback) {
        this.connectionCallback = callback;
    }

    public MessageDispatcher getDispatcher() {
        return dispatcher;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    // ── Connect to Broker ───────────────────────────────────────────
    public void connect() {
        if (isConnected()) {
            Log.d(TAG, "Already connected");
            return;
        }

        new Thread(() -> {
            try {
                Log.i(TAG, "Connecting to Broker at " + brokerHost + ":" + brokerPort);
                socket = new Socket(brokerHost, brokerPort);
                writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream()));
                reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                running = true;
                startReaderLoop();

                Log.i(TAG, "Connected to Broker successfully");
                mainHandler.post(() -> {
                    if (connectionCallback != null)
                        connectionCallback.onConnected();
                });

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    if (connectionCallback != null)
                        connectionCallback.onError("Connection failed: " + e.getMessage());
                    if (dispatcher != null) {
                        MessageListener l = getListenerFromDispatcher();
                        if (l != null)
                            l.onConnectionError("Connection failed: " + e.getMessage());
                    }
                });
            }
        }, "SocketClient-Connect").start();
    }

    // ── Send a job to the Broker ────────────────────────────────────
    public void sendJob(JobRequest job) {
        new Thread(() -> {
            try {
                if (!isConnected()) {
                    Log.e(TAG, "Not connected — cannot send job");
                    mainHandler.post(() -> {
                        MessageListener l = getListenerFromDispatcher();
                        if (l != null)
                            l.onConnectionError("Not connected to Broker");
                    });
                    return;
                }

                String json = gson.toJson(job);
                Log.d(TAG, "Sending: " + json.substring(0, Math.min(json.length(), 200)));
                writer.write(json);
                writer.newLine();
                writer.flush();
                Log.i(TAG, "Job sent: " + job.getJobId());

            } catch (IOException e) {
                Log.e(TAG, "Send failed: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    MessageListener l = getListenerFromDispatcher();
                    if (l != null)
                        l.onConnectionError("Send failed: " + e.getMessage());
                });
            }
        }, "SocketClient-Send").start();
    }

    // ── Continuous reader loop (background thread) ──────────────────
    private void startReaderLoop() {
        readerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    Log.d(TAG, "Received: " + line.substring(0, Math.min(line.length(), 200)));
                    final String msg = line;
                    // Dispatch on main thread so listeners can update UI
                    mainHandler.post(() -> dispatcher.dispatch(msg));
                }
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "Reader loop error: " + e.getMessage(), e);
                    mainHandler.post(() -> {
                        if (connectionCallback != null)
                            connectionCallback.onDisconnected(e.getMessage());
                        MessageListener l = getListenerFromDispatcher();
                        if (l != null)
                            l.onConnectionError("Disconnected: " + e.getMessage());
                    });
                }
            } finally {
                running = false;
            }
        }, "SocketClient-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    // ── Disconnect ──────────────────────────────────────────────────
    public void disconnect() {
        running = false;
        try {
            if (reader != null)
                reader.close();
            if (writer != null)
                writer.close();
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }
        socket = null;
        writer = null;
        reader = null;
    }

    // Helper — dispatcher doesn't expose its listener directly,
    // so we keep a reference via a package-private accessor.
    private MessageListener currentListener;

    public void setMessageListener(MessageListener listener) {
        this.currentListener = listener;
        dispatcher.setListener(listener);
    }

    private MessageListener getListenerFromDispatcher() {
        return currentListener;
    }
}
