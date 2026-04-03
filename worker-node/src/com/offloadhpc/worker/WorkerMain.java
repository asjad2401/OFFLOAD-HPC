package com.offloadhpc.worker;

import com.offloadhpc.worker.rmi.WorkerServiceImpl;
import com.offloadhpc.worker.network.BrokerRegistrar;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.net.InetAddress;

/**
 * WorkerMain — entry point for the Worker Node.
 *
 * Usage: java com.offloadhpc.worker.WorkerMain <workerId> <brokerHost>
 * <brokerPort> [rmiPort]
 *
 * Example:
 * java -cp src com.offloadhpc.worker.WorkerMain w1 192.168.1.3 9000 1099
 * java -cp src com.offloadhpc.worker.WorkerMain w2 192.168.1.3 9000 1100
 */
public class WorkerMain {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: WorkerMain <workerId> <brokerHost> <brokerPort> [rmiPort]");
            System.err.println("  workerId   - unique worker identifier (e.g. w1, w2)");
            System.err.println("  brokerHost - IP address of the Broker node");
            System.err.println("  brokerPort - TCP port of the Broker (usually 9000)");
            System.err.println("  rmiPort    - RMI registry port (default: 1099)");
            System.exit(1);
        }

        String workerId = args[0];
        String brokerHost = args[1];
        int brokerPort = Integer.parseInt(args[2]);
        int rmiPort = args.length > 3 ? Integer.parseInt(args[3]) : 1099;

        try {
            // Set the RMI server hostname so remote clients can connect
            String localIp = InetAddress.getLocalHost().getHostAddress();
            System.setProperty("java.rmi.server.hostname", localIp);

            // Create RMI registry and bind our service
            Registry registry = LocateRegistry.createRegistry(rmiPort);
            WorkerServiceImpl impl = new WorkerServiceImpl(workerId);
            registry.rebind("WorkerService", impl);
            System.out.println("[Worker " + workerId + "] RMI ready on port " + rmiPort);
            System.out.println("[Worker " + workerId + "] Local IP: " + localIp);

            // Register with Broker over TCP
            BrokerRegistrar.register(workerId, brokerHost, brokerPort, localIp, rmiPort);

            System.out.println("[Worker " + workerId + "] Running. Press Ctrl+C to stop.");

            // Keep the main thread alive so the RMI registry stays bound
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
