package com.offloadhpc.broker.registry;

import com.offloadhpc.contract.WorkerService;

import java.rmi.Naming;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory registry of all known worker nodes.
 */
public class NodeRegistry {

    private final ConcurrentHashMap<String, WorkerInfo> registry = new ConcurrentHashMap<>();

    /**
     * Register a worker (or update its info if re-registering).
     */
    public void addWorker(String workerId, String ip, int rmiPort) {
        WorkerInfo info = new WorkerInfo(workerId, ip, rmiPort);
        registry.put(workerId, info);
        System.out.println("[Registry] Worker registered: " + info);
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
     * Dump registry contents to console.
     */
    public void printAll() {
        System.out.println("[Registry] Total workers: " + registry.size());
        for (WorkerInfo w : registry.values()) {
            System.out.println("  " + w);
        }
    }
}
