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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Singleton TCP socket client that maintains a persistent connection
 * to the Broker node.
 *
 * v2.0 — supports UDP multicast broker auto-discovery.
 * On connect(), broadcasts DISCOVER_BROKER on 239.1.1.1:5000,
 * waits for BROKER_HERE response, then opens TCP.
 *
 * All network I/O runs on background threads to avoid
 * NetworkOnMainThreadException.
 */
public class SocketClient {

    private static final String TAG = "SocketClient";
    private static SocketClient instance;

    // ── Configurable Broker address ──────────────────────────────────
    private String brokerHost = null; // null = use UDP auto-discovery
    private int brokerPort = 9000;
    private boolean useAutoDiscovery = true;
    private static final String MULTICAST_GROUP = "239.1.1.1";
    private static final int MULTICAST_PORT = 5000;
    private static final int DISCOVERY_TIMEOUT_MS = 5000;

    // Fallback IP when auto-discovery fails (e.g. WiFi blocks multicast)
    // Update this to your broker PC's WiFi IP (run ipconfig to find it)
    private String fallbackBrokerHost = "10.7.158.49";

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

    /**
     * Enable or disable UDP auto-discovery.
     * If enabled, connect() will broadcast DISCOVER_BROKER before opening TCP.
     */
    public void setAutoDiscovery(boolean enabled) {
        this.useAutoDiscovery = enabled;
    }

    // ── Connect to Broker ───────────────────────────────────────────
    public void connect() {
        if (isConnected()) {
            Log.d(TAG, "Already connected");
            return;
        }

        new Thread(() -> {
            try {
                // If auto-discovery is enabled and no host is set, discover broker
                if (useAutoDiscovery && brokerHost == null) {
                    Log.i(TAG, "Starting UDP broker auto-discovery...");
                    boolean found = discoverBroker();
                    if (!found) {
                        Log.w(TAG, "Auto-discovery failed. Falling back to " + fallbackBrokerHost);
                        brokerHost = fallbackBrokerHost;
                    }
                }

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

    /**
     * Discover the broker via UDP multicast.
     * Sends DISCOVER_BROKER and waits for BROKER_HERE response.
     * Returns true if broker was found, sets brokerHost and brokerPort.
     */
    private boolean discoverBroker() {
        try {
            DatagramSocket sendSocket = new DatagramSocket();
            sendSocket.setBroadcast(true);
            sendSocket.setSoTimeout(DISCOVERY_TIMEOUT_MS);

            // Send discovery request
            String msg = "DISCOVER_BROKER|android|0";
            byte[] sendData = msg.getBytes();
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length, group, MULTICAST_PORT);
            sendSocket.send(sendPacket);
            Log.i(TAG, "Sent DISCOVER_BROKER to " + MULTICAST_GROUP + ":" + MULTICAST_PORT);

            // Wait for response
            byte[] recvBuf = new byte[512];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);

            long deadline = System.currentTimeMillis() + DISCOVERY_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    int remaining = (int) (deadline - System.currentTimeMillis());
                    if (remaining <= 0)
                        break;
                    sendSocket.setSoTimeout(remaining);
                    sendSocket.receive(recvPacket);

                    String response = new String(recvPacket.getData(), 0, recvPacket.getLength()).trim();
                    Log.d(TAG, "Discovery response: " + response);

                    // Parse BROKER_HERE|nodeId|priority|ip|tcpPort
                    if (response.startsWith("BROKER_HERE|")) {
                        String[] parts = response.split("\\|");
                        if (parts.length >= 5) {
                            brokerHost = parts[3];
                            brokerPort = Integer.parseInt(parts[4]);
                            Log.i(TAG, "Broker discovered at " + brokerHost + ":" + brokerPort);
                            sendSocket.close();
                            return true;
                        }
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }

            sendSocket.close();
        } catch (Exception e) {
            Log.e(TAG, "UDP discovery error: " + e.getMessage(), e);
        }
        return false;
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
                    // Suppress excessive logging of the raw json line to prevent log buffer drops
                    Log.d(TAG, "Received: " + line.substring(0, Math.min(line.length(), 200)));
                    final String msg = line;
                    // Dispatch on current background thread; MessageDispatcher will post UI updates
                    dispatcher.dispatch(msg);
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
