package com.bscs13b.offloadhpc.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.bscs13b.offloadhpc.model.JobRequest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Singleton TCP socket client that maintains a persistent connection
 * to the Broker node.
 *
 * v2.1 -- hardened for LAN reliability:
 * - TCP connect timeout (5s) prevents hanging on bad IPs
 * - Socket keepAlive detects dead connections
 * - sendJob checks writer state and fires errors immediately
 * - Reader loop auto-disconnects and notifies UI
 * - All errors surface to the user, never silently stuck
 */
public class SocketClient {

    private static final String TAG = "SocketClient";
    private static SocketClient instance;

    // -- Configurable Broker address --
    private String brokerHost = null;
    private int brokerPort = 9000;
    private boolean useAutoDiscovery = true;
    private static final String MULTICAST_GROUP = "239.1.1.1";
    private static final int MULTICAST_PORT = 5000;
    private static final int DISCOVERY_TIMEOUT_MS = 5000;
    private static final int TCP_CONNECT_TIMEOUT_MS = 5000;

    // Fallback IP when auto-discovery fails
    private String fallbackBrokerHost = null;

    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Thread readerThread;
    private volatile boolean running = false;
    private volatile boolean connecting = false;

    private final Gson gson = new Gson();
    private final MessageDispatcher dispatcher = new MessageDispatcher();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ConnectionCallback connectionCallback;

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

    // -- Configuration --
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
        return running && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void setAutoDiscovery(boolean enabled) {
        this.useAutoDiscovery = enabled;
    }

    // -- Connect to Broker --
    public void connect() {
        if (isConnected()) {
            Log.d(TAG, "Already connected");
            return;
        }
        if (connecting) {
            Log.d(TAG, "Connection already in progress");
            return;
        }

        new Thread(() -> {
            connecting = true;
            try {
                // Try discovery up to 3 times
                int maxAttempts = 3;
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    if (useAutoDiscovery && brokerHost == null) {
                        Log.i(TAG, "Starting UDP broker auto-discovery (attempt " + attempt + "/" + maxAttempts + ")...");
                        boolean found = discoverBroker();
                        if (found) {
                            break;
                        }
                        if (attempt < maxAttempts) {
                            Log.w(TAG, "Discovery attempt " + attempt + " failed. Retrying in 1s...");
                            Thread.sleep(1000);
                        }
                    } else {
                        break;
                    }
                }

                if (brokerHost == null) {
                    if (fallbackBrokerHost != null) {
                        Log.w(TAG, "Auto-discovery failed. Falling back to " + fallbackBrokerHost);
                        brokerHost = fallbackBrokerHost;
                    } else {
                        Log.e(TAG, "Auto-discovery failed and no fallback configured.");
                        mainHandler.post(() -> {
                            if (connectionCallback != null)
                                connectionCallback.onError(
                                        "Could not find Broker on this network.\nMake sure a grid node is running and you are on the same Wi-Fi.");
                        });
                        return;
                    }
                }

                Log.i(TAG, "Connecting to Broker at " + brokerHost + ":" + brokerPort);

                // Use connect with timeout to prevent hanging
                Socket newSocket = new Socket();
                newSocket.connect(new InetSocketAddress(brokerHost, brokerPort), TCP_CONNECT_TIMEOUT_MS);
                newSocket.setKeepAlive(true);
                newSocket.setSoTimeout(0); // no read timeout (long jobs)

                socket = newSocket;
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
                // Reset brokerHost so next connect() triggers fresh discovery
                brokerHost = null;
                mainHandler.post(() -> {
                    if (connectionCallback != null)
                        connectionCallback.onError("Connection failed: " + e.getMessage() +
                                "\nTap Reconnect to try again.");
                    MessageListener l = getListenerFromDispatcher();
                    if (l != null)
                        l.onConnectionError("Connection failed: " + e.getMessage());
                });
            } catch (InterruptedException e) {
                Log.e(TAG, "Discovery interrupted", e);
                Thread.currentThread().interrupt();
            } finally {
                connecting = false;
            }
        }, "SocketClient-Connect").start();
    }

    /**
     * Discover the broker via UDP.
     * Sends DISCOVER_BROKER to the multicast group, then listens for
     * the broker's unicast reply on the same socket.
     *
     * The broker sends BROKER_HERE back directly to our IP:port (unicast),
     * so we don't need a MulticastSocket or WifiManager.MulticastLock.
     */
    private boolean discoverBroker() {
        DatagramSocket socket = null;
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);

            // Plain DatagramSocket on an ephemeral port -- receives unicast replies
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(DISCOVERY_TIMEOUT_MS);

            // Send discovery request to multicast group
            String msg = "DISCOVER_BROKER|android|0";
            byte[] sendData = msg.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length, group, MULTICAST_PORT);
            socket.send(sendPacket);
            Log.i(TAG, "Sent DISCOVER_BROKER to " + MULTICAST_GROUP + ":" + MULTICAST_PORT);

            // Wait for unicast reply from broker
            byte[] recvBuf = new byte[512];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);

            long deadline = System.currentTimeMillis() + DISCOVERY_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    int remaining = (int) (deadline - System.currentTimeMillis());
                    if (remaining <= 0)
                        break;
                    socket.setSoTimeout(remaining);
                    socket.receive(recvPacket);

                    String response = new String(recvPacket.getData(), 0, recvPacket.getLength()).trim();
                    Log.d(TAG, "Discovery response: " + response);

                    // Parse BROKER_HERE|nodeId|priority|ip|tcpPort
                    if (response.startsWith("BROKER_HERE|")) {
                        String[] parts = response.split("\\|");
                        if (parts.length >= 5) {
                            brokerHost = parts[3];
                            brokerPort = Integer.parseInt(parts[4]);
                            Log.i(TAG, "Broker discovered at " + brokerHost + ":" + brokerPort);
                            socket.close();
                            return true;
                        }
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }

            socket.close();
        } catch (Exception e) {
            Log.e(TAG, "UDP discovery error: " + e.getMessage(), e);
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
        return false;
    }

    // -- Send a job to the Broker --
    public void sendJob(JobRequest job) {
        if (!isConnected()) {
            Log.e(TAG, "Not connected -- cannot send job");
            mainHandler.post(() -> {
                MessageListener l = getListenerFromDispatcher();
                if (l != null)
                    l.onConnectionError("Not connected to Grid. Use Reconnect.");
            });
            return;
        }

        new Thread(() -> {
            try {
                Log.i(TAG, "Streaming job " + job.getJobId() + " to broker...");

                synchronized (this) {
                    if (writer == null) {
                        throw new IOException("Writer is null - connection lost");
                    }
                    // Stream JSON directly to the socket writer instead of
                    // building a huge String in memory (avoids OOM on large payloads)
                    gson.toJson(job, job.getClass(), new com.google.gson.stream.JsonWriter(writer));
                    writer.newLine();
                    writer.flush();
                }
                Log.i(TAG, "Job sent: " + job.getJobId());

            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "OOM while sending job: " + oom.getMessage(), oom);
                mainHandler.post(() -> {
                    MessageListener l = getListenerFromDispatcher();
                    if (l != null)
                        l.onConnectionError("Job payload too large for this device. Try a smaller input.");
                });
            } catch (IOException e) {
                Log.e(TAG, "Send failed: " + e.getMessage(), e);
                handleConnectionLost("Send failed: " + e.getMessage());
            }
        }, "SocketClient-Send").start();
    }

    // -- Continuous reader loop --
    private void startReaderLoop() {
        readerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    Log.d(TAG, "Received: " + line.substring(0, Math.min(line.length(), 200)));
                    dispatcher.dispatch(line);
                }
                // readLine returned null = server closed connection
                if (running) {
                    handleConnectionLost("Server closed connection");
                }
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "Reader loop error: " + e.getMessage(), e);
                    handleConnectionLost("Connection lost: " + e.getMessage());
                }
            } finally {
                running = false;
            }
        }, "SocketClient-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Handle a broken connection: notify UI, clean up state.
     */
    private void handleConnectionLost(String reason) {
        running = false;
        // Reset brokerHost so next connect triggers fresh discovery
        brokerHost = null;
        mainHandler.post(() -> {
            if (connectionCallback != null)
                connectionCallback.onDisconnected(reason);
            MessageListener l = getListenerFromDispatcher();
            if (l != null)
                l.onConnectionError(reason);
        });
    }

    // -- Disconnect --
    public void disconnect() {
        running = false;
        try {
            if (reader != null)
                reader.close();
        } catch (IOException ignored) {}
        try {
            if (writer != null)
                writer.close();
        } catch (IOException ignored) {}
        try {
            if (socket != null)
                socket.close();
        } catch (IOException ignored) {}
        socket = null;
        writer = null;
        reader = null;
    }

    private MessageListener currentListener;

    public void setMessageListener(MessageListener listener) {
        this.currentListener = listener;
        dispatcher.setListener(listener);
    }

    private MessageListener getListenerFromDispatcher() {
        return currentListener;
    }
}

