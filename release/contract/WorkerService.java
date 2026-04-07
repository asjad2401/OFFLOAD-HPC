import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI interface for Worker Nodes in the OFFLOAD-HPC-MOBILE grid.
 * Shared contract — do NOT modify without team consensus.
 */
public interface WorkerService extends Remote {

    /**
     * Called at worker startup to announce availability.
     */
    void register(String workerId, String brokerHost, int brokerPort)
            throws RemoteException;

    /**
     * Execute a MatMul sub-task on rows [startRow, endRow).
     */
    double[][] executeMatMul(String subTaskId,
            double[][] matrixA, double[][] matrixB,
            int startRow, int endRow) throws RemoteException;

    /**
     * Execute a hash crack on keyspace indices [startIndex, endIndex).
     * Returns the cracked plaintext or null if not found in this range.
     */
    String executeHashCrack(String subTaskId,
            String targetHash, String algorithm,
            String charset, int maxLength,
            long startIndex, long endIndex) throws RemoteException;

    /**
     * Health check — returns true if worker is alive.
     */
    boolean ping() throws RemoteException;
}
