package com.offloadhpc.worker.rmi;

import com.offloadhpc.worker.compute.MatMulEngine;
import com.offloadhpc.worker.compute.HashCrackEngine;

import com.offloadhpc.contract.WorkerService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * WorkerServiceImpl — RMI implementation of the WorkerService contract.
 *
 * Stateless: all inputs are method parameters, all outputs are return values.
 * Delegates compute work to MatMulEngine and HashCrackEngine.
 */
public class WorkerServiceImpl extends UnicastRemoteObject implements WorkerService {

    private final String workerId;

    public WorkerServiceImpl(String workerId) throws RemoteException {
        super();
        this.workerId = workerId;
    }

    @Override
    public void register(String workerId, String brokerHost, int brokerPort) throws RemoteException {
        System.out.println("[Worker " + this.workerId + "] register() called via RMI — "
                + "workerId=" + workerId + ", brokerHost=" + brokerHost + ", brokerPort=" + brokerPort);
    }

    @Override
    public double[][] executeMatMul(String subTaskId,
            double[][] matrixA, double[][] matrixB,
            int startRow, int endRow) throws RemoteException {

        System.out.println("[Worker " + workerId + "] MatMul sub-task " + subTaskId
                + " rows [" + startRow + ", " + endRow + ")");

        long start = System.currentTimeMillis();
        double[][] result = MatMulEngine.compute(matrixA, matrixB, startRow, endRow);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("[Worker " + workerId + "] MatMul sub-task " + subTaskId
                + " completed in " + elapsed + " ms");
        return result;
    }

    @Override
    public String executeHashCrack(String subTaskId,
            String targetHash, String algorithm,
            String charset, int maxLength,
            long startIndex, long endIndex) throws RemoteException {

        System.out.println("[Worker " + workerId + "] HashCrack sub-task " + subTaskId
                + " range [" + startIndex + ", " + endIndex + ")");

        long start = System.currentTimeMillis();
        String result = HashCrackEngine.crack(targetHash, algorithm, charset, maxLength, startIndex, endIndex);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("[Worker " + workerId + "] HashCrack sub-task " + subTaskId
                + " completed in " + elapsed + " ms — "
                + (result != null ? "FOUND: " + result : "not found in range"));
        return result;
    }

    @Override
    public boolean ping() throws RemoteException {
        System.out.println("[Worker " + workerId + "] ping() — alive");
        return true;
    }
}
