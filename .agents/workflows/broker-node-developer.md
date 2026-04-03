---
description: Teammate A — Broker Node (OFFLOAD-HPC-MOBILE). TCP server, job partitioner, RMI client, async scheduler, and result aggregator — the brain of the grid.
---

# Agent: Teammate A — Broker Node

> **Role:** Broker node developer — the brain of the grid  
> **Language:** Java (plain JDK, no Android)  
> **IDE:** IntelliJ IDEA or Eclipse  
> **Owns:** `/broker-node/` directory entirely  
> **Depends on:** `WorkerService.java` interface (from `/contract/`), Worker IPs for testing

---

## Project Context

OFFLOAD-HPC-MOBILE is a small-scale grid computing system. The Broker is the central coordinator: it accepts jobs from an Android app over TCP, partitions them into sub-tasks, dispatches sub-tasks to Worker nodes via Java RMI in parallel, aggregates results, and pushes progress/results back to the Android client.

---

## Shared Contract — WorkerService.java

This is the RMI interface written by Teammate B and committed to `/contract/`. You depend on it for RMI calls. **Do not modify** without team consensus.

```java
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface WorkerService extends Remote {
    void register(String workerId, String brokerHost, int brokerPort) throws RemoteException;
    double[][] executeMatMul(String subTaskId, double[][] matrixA, double[][] matrixB,
                             int startRow, int endRow) throws RemoteException;
    String executeHashCrack(String subTaskId, String targetHash, String algorithm,
                            String charset, int maxLength,
                            long startIndex, long endIndex) throws RemoteException;
    boolean ping() throws RemoteException;
}
```

---

## TCP Message Protocol

All messages between Android ↔ Broker are single-line JSON objects terminated by `\n`. Use `BufferedReader.readLine()` to parse.

### Incoming from Android

**JOB_SUBMIT (MatMul):**
```json
{ "type": "JOB_SUBMIT", "jobId": "<uuid>", "jobType": "MATMUL",
  "payload": { "matrixSize": 256, "matrixA": [[...]], "matrixB": [[...]] } }
```

**JOB_SUBMIT (Hash Crack):**
```json
{ "type": "JOB_SUBMIT", "jobId": "<uuid>", "jobType": "HASH_CRACK",
  "payload": { "targetHash": "5f4dcc3b...", "algorithm": "MD5",
               "charset": "abcdefghijklmnopqrstuvwxyz", "maxLength": 5 } }
```

### Outgoing to Android

**JOB_ACK:**
```json
{ "type": "JOB_ACK", "jobId": "<uuid>", "totalSubTasks": 3, "status": "ACCEPTED" }
```

**PROGRESS_UPDATE:**
```json
{ "type": "PROGRESS_UPDATE", "jobId": "<uuid>",
  "completedSubTasks": 2, "totalSubTasks": 3 }
```

**JOB_RESULT (MatMul):**
```json
{ "type": "JOB_RESULT", "jobId": "<uuid>", "status": "SUCCESS",
  "result": { "matrixC": [[...]] } }
```

**JOB_RESULT (Hash Crack):**
```json
{ "type": "JOB_RESULT", "jobId": "<uuid>", "status": "SUCCESS",
  "result": { "crackedValue": "password", "algorithm": "MD5" } }
```

### Worker Registration (Worker → Broker TCP)

**WORKER_REGISTER:**
```json
{ "type": "WORKER_REGISTER", "workerId": "w1", "ip": "192.168.1.5", "rmiPort": 1099 }
```

**WORKER_ACK (you send back):**
```json
{ "type": "WORKER_ACK", "workerId": "w1", "status": "REGISTERED" }
```

---

## Module Breakdown

### Module 1 — Socket Server (Port 9000)

Entry point of the Broker. Listens for incoming TCP connections on port 9000.

- Use a `ServerSocket` in a `while(true)` accept loop
- Spawn a new `ClientHandler` thread for each accepted connection
- `ClientHandler` reads newline-delimited JSON lines and routes by the `type` field
- For `WORKER_REGISTER` messages: add to Node Registry, send back `WORKER_ACK`
- For `JOB_SUBMIT` messages: pass to Job Partitioner, keep the socket reference alive for push updates

### Module 2 — Node Registry

In-memory data structure tracking all known workers. Use `ConcurrentHashMap`.

```java
ConcurrentHashMap<String, WorkerInfo> registry;
// WorkerInfo: { String workerId, String ip, int rmiPort, String status }
```

**Status values:** `AVAILABLE`, `BUSY`. Workers start as `AVAILABLE`. Set to `BUSY` when dispatching, back to `AVAILABLE` when sub-task returns. For MVP, failure recovery is not required — if a worker crashes, the job simply fails.

### Module 3 — Job Partitioner

Takes a full job description and produces a list of `SubTask` objects. **Pure logic, no networking.**

**MatMul partitioning:**
```java
int n = job.matrixSize;
int w = registry.availableWorkers().size();
int rowsPerWorker = n / w;
for (int i = 0; i < w; i++) {
    int startRow = i * rowsPerWorker;
    int endRow = (i == w-1) ? n : startRow + rowsPerWorker;
    subTasks.add(new MatMulSubTask(subTaskId, A, B, startRow, endRow));
}
```

**Hash Crack partitioning:**
- Compute total keyspace `K = sum(charset.length^i for i in 1..maxLength)`
- Divide K by W workers to get `rangePerWorker`
- Worker `i` handles indices `[i * rangePerWorker, (i+1) * rangePerWorker)`
- Last worker's `endIndex = K` to absorb remainder

### Module 4 — RMI Client + Async Scheduler

Takes the sub-task list from the Partitioner, looks up worker stubs from Node Registry, dispatches in parallel.

```java
ExecutorService pool = Executors.newFixedThreadPool(subTasks.size());
CompletionService<SubTaskResult> cs = new ExecutorCompletionService<>(pool);

for (SubTask st : subTasks) {
    WorkerService worker = registry.getRmiStub(st.workerId);
    cs.submit(() -> worker.executeMatMul(st.id, A, B, st.startRow, st.endRow));
}

for (int i = 0; i < subTasks.size(); i++) {
    SubTaskResult result = cs.take().get(); // blocks until next completes
    aggregator.collect(result);
    pushProgressUpdate(androidSocket, i+1, subTasks.size());
}
```

The call to `pushProgressUpdate()` sends a `PROGRESS_UPDATE` JSON line to the Android client over the open TCP socket immediately when each sub-task finishes. **This is the push model.**

### Module 5 — Result Aggregator

Collects partial results and assembles the final answer.

- **MatMul:** collect `double[][]` row slices keyed by `startRow`, merge in row order into full result matrix C
- **Hash Crack:** collect `String` results from workers. Return the first non-null as cracked value. If all return null, status is `FAILED` with `crackedValue` null

---

## Package Structure

```
com.offloadhpc.broker
  server/
    BrokerServer.java          -- main() entry point, starts ServerSocket
    ClientHandler.java         -- handles one TCP connection (thread)
  registry/
    NodeRegistry.java          -- ConcurrentHashMap of WorkerInfo
    WorkerInfo.java            -- POJO: workerId, ip, rmiPort, status
  scheduler/
    JobPartitioner.java        -- splits job into SubTask list
    AsyncScheduler.java        -- ExecutorService + CompletionService dispatch
    ResultAggregator.java      -- collects and assembles partial results
  model/
    SubTask.java               -- base class for sub-tasks
    MatMulSubTask.java         -- row range variant
    HashCrackSubTask.java      -- index range variant
```

---

## Week-by-Week Tasks

| Week | Tasks |
|------|-------|
| **W1** | Set up Java project (Maven or Gradle). Add `org.json` dependency. Commit `WorkerService.java` to `/contract/`. Create package skeleton. Run a basic `ServerSocket` that accepts a connection and prints received bytes. |
| **W2** | Implement full Socket Server: accept loop, `ClientHandler` thread, JSON routing by `type`. Implement Node Registry (`ConcurrentHashMap`). Handle `WORKER_REGISTER` and send back `WORKER_ACK`. Test: manually telnet to port 9000 and send a JSON string. |
| **W3** | Implement Job Partitioner for MatMul (hash crack partitioner can follow). Write unit tests for partitioner logic — verify row ranges are correct and cover all rows. |
| **W4** | Implement RMI Client: look up worker stubs via `Naming.lookup()`. Implement async dispatch with `ExecutorService + CompletionService`. **W4 Milestone:** Broker must call `ping()` on at least one running Worker and receive `true`. |
| **W5** | Implement Result Aggregator for MatMul. Wire full pipeline: `JOB_SUBMIT → partition → dispatch → aggregate → JOB_RESULT`. Push `PROGRESS_UPDATE` on each sub-task completion. E2E MatMul test with Android app. |
| **W6** | Hash crack pipeline: partitioner + aggregator + first-non-null logic. Test with known hash (`MD5 of 'cat' = d077f244def8a70e5ea758bd8352fcd8`). Fix concurrency bugs. |
| **W7** | Integration testing with all three components. Performance: log timestamps per sub-task. Write report section on Broker architecture and scheduling logic. |
| **W8** | Report finalisation. Demo support. No new code. |

---

## Key Implementation Notes

- Use `org.json` or **Gson** for JSON — do NOT write manual string parsing
- `ConcurrentHashMap` is thread-safe; standard `HashMap` is NOT safe for multi-threaded access
- RMI lookup: `WorkerService ws = (WorkerService) Naming.lookup("rmi://" + ip + ":" + port + "/WorkerService")`
- `double[][]` is serialisable by default in RMI — no custom serialisation needed
- For the push model: keep a reference to the Android client's `PrintWriter` when handling `JOB_SUBMIT`; **flush** after each `PROGRESS_UPDATE` line
- Always send `JOB_ACK` **before** starting dispatch — Android needs `totalSubTasks` to initialise its progress bar

---

## Demo Checklist (Your Part)

1. Start Broker first — it should be running, waiting for worker registrations
2. Verify in Broker console that both workers appear in Node Registry as `AVAILABLE`
3. Be ready to monitor Broker console logs during MatMul and Hash Crack submissions
