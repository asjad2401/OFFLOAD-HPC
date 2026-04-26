package com.offloadhpc.broker.registry;

import com.offloadhpc.contract.WorkerService;
import com.offloadhpc.ui.GridNodeEventListener;

import java.rmi.Naming;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe in-memory registry of all known worker nodes.
 * v2.0 — extended with heartbeat eviction and capability weights.
 */
public class NodeRegistry {

    private static final long HEARTBEAT_TIMEOUT_MS = 30000; // 30 seconds (LAN tolerant)
    private static final long EVICTION_CHECK_INTERVAL_MS = 10000; // check every 10s

    private final ConcurrentHashMap<String, WorkerInfo> registry = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictionScheduler;
    private GridNodeEventListener eventListener;

    public NodeRegistry() {
        // Start heartbeat eviction thread
        evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HeartbeatEvictor");
            t.setDaemon(true);
            return t;
        });
        evictionScheduler.scheduleAtFixedRate(this::evictStaleWorkers,
                EVICTION_CHECK_INTERVAL_MS, EVICTION_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void setEventListener(GridNodeEventListener listener) {
        this.eventListener = listener;
    }

    /**
     * Register a worker (or update its info if re-registering).
     */
    public void addWorker(String workerId, String ip, int rmiPort) {
        WorkerInfo info = new WorkerInfo(workerId, ip, rmiPort);
        registry.put(workerId, info);
        System.out.println("[Registry] Worker registered: " + info);
        if (eventListener != null) {
            eventListener.onWorkerRegistered(workerId, ip, info.getCpuCores(), info.getAvailableMemoryMB());
        }
    }

    /**
     * Register a worker with capability info.
     */
    public void addWorker(String workerId, String ip, int rmiPort,
            int cpuCores, long availableMemoryMB) {
        WorkerInfo info = new WorkerInfo(workerId, ip, rmiPort, cpuCores, availableMemoryMB);
        registry.put(workerId, info);
        System.out.println("[Registry] Worker registered: " + info);
        if (eventListener != null) {
            eventListener.onWorkerRegistered(workerId, ip, cpuCores, availableMemoryMB);
        }
    }

    /**
     * Get a worker by ID.
     */
    public WorkerInfo getWorker(String workerId) {
        return registry.get(workerId);
    }

    /**
     * Return a list of all workers with AVAILABLE status.
     */
    public List<WorkerInfo> getAvailableWorkers() {
        List<WorkerInfo> available = new ArrayList<>();
        for (WorkerInfo w : registry.values()) {
            if (w.getStatus() == WorkerInfo.Status.AVAILABLE) {
                available.add(w);
            }
        }
        return available;
    }

    /**
     * Set a worker's status (AVAILABLE / BUSY).
     */
    public void setStatus(String workerId, WorkerInfo.Status status) {
        WorkerInfo info = registry.get(workerId);
        if (info != null) {
            info.setStatus(status);
        }
    }

    /**
     * Update heartbeat timestamp for a worker.
     * If the worker was evicted, log it so operators know re-registration is needed.
     */
    public void updateHeartbeat(String workerId) {
        WorkerInfo info = registry.get(workerId);
        if (info != null) {
            info.updateHeartbeat();
        } else {
            System.out.println("[Registry] Heartbeat from unknown/evicted worker: " + workerId +
                    " (worker needs to re-register)");
        }
    }

    /**
     * Get normalized capability weights for all available workers.
     * Each weight is the worker's cpuScore / totalScore.
     *
     * @return map of workerId → weight (0.0 to 1.0), sums to 1.0
     */
    public Map<String, Double> getCapabilityWeights() {
        List<WorkerInfo> available = getAvailableWorkers();
        Map<String, Double> weights = new HashMap<>();

        if (available.isEmpty()) {
            return weights;
        }

        double totalScore = 0.0;
        for (WorkerInfo w : available) {
            totalScore += w.getCpuScore();
        }

        for (WorkerInfo w : available) {
            weights.put(w.getWorkerId(), w.getCpuScore() / totalScore);
        }

        return weights;
    }

    /**
     * Look up the RMI stub for a given worker.
     */
    public WorkerService getRmiStub(String workerId) throws Exception {
        WorkerInfo info = registry.get(workerId);
        if (info == null) {
            throw new IllegalArgumentException("Unknown worker: " + workerId);
        }
        return (WorkerService) Naming.lookup(info.getRmiUrl());
    }

    /**
     * Total number of registered workers.
     */
    public int size() {
        return registry.size();
    }

    /**
     * Remove a specific worker.
     */
    public void removeWorker(String workerId) {
        WorkerInfo removed = registry.remove(workerId);
        if (removed != null) {
            System.out.println("[Registry] Worker removed: " + removed);
        }
    }

    /**
     * Evict workers whose heartbeat has timed out.
     */
    private void evictStaleWorkers() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, WorkerInfo>> it = registry.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, WorkerInfo> entry = it.next();
            WorkerInfo w = entry.getValue();
            long age = now - w.getLastHeartbeat();
            if (age > HEARTBEAT_TIMEOUT_MS) {
                it.remove();
                System.out.println("[Registry] EVICTED stale worker: " + w.getWorkerId() +
                        " (last heartbeat " + (age / 1000) + "s ago)");
                if (eventListener != null) {
                    eventListener.onWorkerLost(w.getWorkerId());
                }
            }
        }
    }

    /**
     * Return all registered workers.
     */
    public Collection<WorkerInfo> getAllWorkers() {
        return registry.values();
    }

    /**
     * Dump registry contents to console.
     */
    public void printAll() {
        System.out.println("[Registry] Total workers: " + registry.size());
        for (WorkerInfo w : registry.values()) {
            System.out.println("  " + w);
        }
    }

    /**
     * Shutdown the eviction scheduler.
     */
    public void shutdown() {
        evictionScheduler.shutdown();
    }

    /**
     * Return the number of registered workers (any status).
     */
    public int getAvailableWorkerCount() {
        return registry.size();
    }
}
