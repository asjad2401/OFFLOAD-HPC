---
description: Teammate B — Worker Nodes (OFFLOAD-HPC-MOBILE). RMI server, MatMul compute engine, Hash Crack compute engine, and TCP registration — the compute muscle of the grid.
---

# Agent: Teammate B — Worker Nodes

> **Role:** Worker node developer — compute engine for the grid  
> **Language:** Java (plain JDK)  
> **IDE:** IntelliJ IDEA or Eclipse  
> **Owns:** `/worker-node/` directory (one codebase, two instances)  
> **Depends on:** `WorkerService.java` (you write this), Broker IP for registration

**Important:** Both Worker Node 1 and Worker Node 2 run the **exact same codebase**. The only difference is the `workerId` passed as a startup argument (e.g., `java -jar worker.jar w1 192.168.1.3 9000`). You maintain one project, run two instances.

---

## Project Context

OFFLOAD-HPC-MOBILE is a small-scale grid computing system. Workers are the compute engines: they register with the Broker via TCP at startup, then receive and execute sub-tasks (MatMul row slices or Hash Crack keyspace ranges) via Java RMI. Workers are stateless — each method call takes all inputs as parameters and returns the result.

---

## Shared Contract — WorkerService.java (YOU OWN THIS)

You write this file. It is the **contract for the entire project**. Write it in Week 1, get it reviewed by all three teammates, and commit it to `/contract/`. Once committed, **do not change method signatures** without a team decision — breaking this interface breaks the Broker's RMI calls.

```java
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface WorkerService extends Remote {
    // Called at worker startup to announce availability
    void register(String workerId, String brokerHost, int brokerPort)
        throws RemoteException;

    // Execute a MatMul sub-task on rows [startRow, endRow)
    double[][] executeMatMul(String subTaskId,
        double[][] matrixA, double[][] matrixB,
        int startRow, int endRow) throws RemoteException;

    // Execute a hash crack on keyspace indices [startIndex, endIndex)
    String executeHashCrack(String subTaskId,
        String targetHash, String algorithm,
        String charset, int maxLength,
        long startIndex, long endIndex) throws RemoteException;

    // Health check — returns true if worker is alive
    boolean ping() throws RemoteException;
}
```

---

## Worker Registration Protocol (TCP)

At startup, after binding the RMI service, the worker opens a **short-lived TCP connection** to the Broker on port 9000 to register itself.

**WORKER_REGISTER (you send):**
```json
{ "type": "WORKER_REGISTER", "workerId": "w1", "ip": "192.168.1.5", "rmiPort": 1099 }
```

**WORKER_ACK (Broker responds):**
```json
{ "type": "WORKER_ACK", "workerId": "w1", "status": "REGISTERED" }
```

After receiving `WORKER_ACK`, close the TCP connection. All subsequent communication is via RMI only.

---

## Module Breakdown

### Module 1 — WorkerService Interface

The interface defined above. Implement it in `WorkerServiceImpl.java`.

### Module 2 — RMI Server Setup

The worker starts an RMI registry and binds its implementation. Startup sequence:

```java
// In main():
String workerId = args[0];        // e.g. "w1"
String brokerHost = args[1];      // e.g. "192.168.1.3"
int brokerPort = Integer.parseInt(args[2]);  // 9000
int rmiPort = args.length > 3 ? Integer.parseInt(args[3]) : 1099;

Registry registry = LocateRegistry.createRegistry(rmiPort);
WorkerService impl = new WorkerServiceImpl(workerId);
registry.rebind("WorkerService", impl);
System.out.println("Worker " + workerId + " RMI ready on port " + rmiPort);

// Then register with Broker over TCP:
registerWithBroker(workerId, brokerHost, brokerPort, rmiPort);
```

The `registerWithBroker()` method opens a short-lived TCP connection to the Broker on port 9000, sends `WORKER_REGISTER`, waits for `WORKER_ACK`, then closes the connection.

### Module 3 — MatMul Compute Engine

Implements `executeMatMul()`. Takes full matrices A and B plus a row range, computes **only those rows** of C = A × B.

```java
public double[][] executeMatMul(String subTaskId,
        double[][] A, double[][] B, int startRow, int endRow) {
    int n = A[0].length; // columns of A = rows of B
    int resultRows = endRow - startRow;
    double[][] C = new double[resultRows][n];
    for (int i = 0; i < resultRows; i++) {
        for (int j = 0; j < n; j++) {
            for (int k = 0; k < n; k++) {
                C[i][j] += A[startRow + i][k] * B[k][j];
            }
        }
    }
    return C; // returns ONLY the assigned rows
}
```

> **Note:** The returned matrix C has `(endRow - startRow)` rows, not N rows. The Broker's aggregator places this slice back at the correct row offset.

### Module 4 — Hash Crack Compute Engine

Implements `executeHashCrack()`. Takes a numeric index range `[startIndex, endIndex)` and iterates through that slice of the candidate keyspace.

**indexToString utility:**
```java
public static String indexToString(long index, String charset, int maxLen) {
    int base = charset.length();
    StringBuilder sb = new StringBuilder();
    // Handle length prefix: indices 0..base-1 are length 1,
    // base..base+base^2-1 are length 2, etc.
    int len = 1;
    long count = base;
    while (index >= count) {
        index -= count;
        len++;
        count = (long) Math.pow(base, len);
    }
    for (int i = 0; i < len; i++) {
        sb.insert(0, charset.charAt((int)(index % base)));
        index /= base;
    }
    return sb.toString();
}
```

**Main crack loop:**
```java
for (long idx = startIndex; idx < endIndex; idx++) {
    String candidate = indexToString(idx, charset, maxLength);
    String hashed = hash(candidate, algorithm); // MD5 or SHA-1
    if (hashed.equalsIgnoreCase(targetHash)) return candidate;
}
return null; // not found in this range
```

**HashUtils — MessageDigest wrapper:**
```java
MessageDigest md = MessageDigest.getInstance("MD5"); // or "SHA-1"
byte[] hash = md.digest(candidate.getBytes(StandardCharsets.UTF_8));
// Convert to hex string for comparison
```

---

## Package Structure

```
com.offloadhpc.worker
  WorkerMain.java              -- main() entry point, args: workerId brokerHost brokerPort rmiPort
  rmi/
    WorkerServiceImpl.java     -- implements WorkerService interface
  compute/
    MatMulEngine.java          -- executeMatMul logic
    HashCrackEngine.java       -- executeHashCrack + indexToString logic
    HashUtils.java             -- MessageDigest wrapper for MD5/SHA-1
  network/
    BrokerRegistrar.java       -- TCP registration with Broker
```

---

## Week-by-Week Tasks

| Week | Tasks |
|------|-------|
| **W1** | Write `WorkerService.java` in `/contract/`. Create Java project skeleton. Verify RMI hello-world works locally: bind a simple remote object and call a method on it from the same machine. Get teammates to review and approve. |
| **W2** | Implement `WorkerServiceImpl`: stub all four methods (`register`, `ping`, `executeMatMul`, `executeHashCrack`) with placeholder returns. Set up RMI server startup and verify Broker can do `Naming.lookup()` and call `ping()` successfully. |
| **W3** | Implement MatMul compute engine. Unit test locally: generate two random 256×256 matrices, call `executeMatMul` with full row range, verify result equals a local reference multiplication. |
| **W4** | Implement TCP registration (`registerWithBroker`). Test full registration flow: worker starts → registers with Broker → Broker logs it. **W4 Milestone:** Broker can `ping()` the worker over RMI from a different machine on the same LAN. |
| **W5** | Implement Hash Crack engine. Test `indexToString()` thoroughly with known inputs. Test `executeHashCrack` with known short password (e.g. `MD5 of 'abc' = 900150983cd24fb0d6963f7d28e17f72`). |
| **W6** | Run both worker instances simultaneously. Test that two workers each receive different sub-tasks and both return results. Integration test with Broker and Android app for Hash Crack. |
| **W7** | Performance profiling: log execution time per sub-task. Test with `maxLength=5`, `charset=a-z` (~12M candidates per worker). Write report section on compute engines and RMI setup. |
| **W8** | Report finalisation. Demo support. Prepare startup script for both workers. |

---

## Key Implementation Notes

- Both workers are **identical JARs** — differentiate only by the `workerId` argument at startup
- If running both on the **same PC**, they must bind to **different RMI ports** (e.g., 1099 and 1100). Pass `rmiPort` as a 4th startup argument
- For MD5/SHA-1: use Java's built-in `MessageDigest` — no external library needed
- Keep `executeMatMul()` and `executeHashCrack()` **stateless** — all inputs as parameters, return the result. No shared mutable state between calls
- The `register()` method in `WorkerService` is available via RMI if needed, but the **primary registration is the TCP `WORKER_REGISTER` message** — make sure `registerWithBroker()` over TCP is your primary path

---

## Demo Checklist (Your Part)

1. Start Worker Node 1 (PC 2): `java -jar worker.jar w1 <brokerIP> 9000 1099`
2. Start Worker Node 2 (PC 3): `java -jar worker.jar w2 <brokerIP> 9000 1099` — or port 1100 if same PC
3. Verify in Broker console that both workers appear as `AVAILABLE`
