---
description: Developer 1 — Android App (OFFLOAD-HPC-MOBILE). Job submission UI, TCP socket client, progress monitoring, and result display.
---

# Agent: Developer 1 — Android App

> **Role:** Android client developer  
> **Language:** Java (Android SDK)  
> **IDE:** Android Studio  
> **Owns:** `/android-app/` directory entirely  
> **Depends on:** Broker IP + port (from Teammate A), `WorkerService.java` (reference only)

---

## Project Context

OFFLOAD-HPC-MOBILE is a small-scale grid computing system. This Android app submits computationally intensive jobs (MatMul & Hash Cracking) to a Broker node over TCP sockets on port 9000 using newline-delimited JSON messages. The Broker distributes work to Worker nodes and pushes progress updates and results back to this app.

---

## Shared Contract Reference

The `WorkerService.java` RMI interface (in `/contract/`) is the system-wide contract. You do NOT implement this interface — you only need awareness of it to understand what job types the system supports. Your communication is exclusively via TCP JSON with the Broker.

---

## TCP Message Protocol

All messages are single-line JSON objects terminated by `\n`. Use `BufferedReader.readLine()` to parse.

### Outgoing (Android → Broker)

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

### Incoming (Broker → Android)

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

---

## Module Breakdown

### Module 1 — Job Launcher Screen

Primary UI screen with two tabs: **MatMul** and **Hash Crack**. Use `TabLayout + ViewPager2` or a toggle within one Activity — do NOT use separate Activities.

**MatMul tab fields:**
- Matrix size selector: `Spinner` with options `128, 256, 512` (integer, used to generate random float matrices)
- A "Generate & Submit" button that creates random `matrixA` and `matrixB`, then calls the Socket Client

**Hash Crack tab fields:**
- Target hash: `EditText` (user pastes a hash string)
- Algorithm: `Spinner` — MD5 / SHA-1
- Max length: `Spinner` — 3, 4, 5 (keep small for demo speed)
- Submit button: serialises to `JOB_SUBMIT` JSON and calls the Socket Client

Both tabs must generate a UUID for `jobId` client-side using `UUID.randomUUID().toString()`.

### Module 2 — Socket Client

Manages the single persistent TCP connection to the Broker. **Must run exclusively on a background thread** — Android throws `NetworkOnMainThreadException` otherwise.

**Responsibilities:**
- Open a TCP socket to Broker IP on port 9000 at app start (or on first job submit)
- Maintain a `BufferedWriter` for outgoing and `BufferedReader` for incoming
- Outgoing: serialise job to JSON + append `\n` + flush
- Incoming: run a continuous `readLine()` loop on a dedicated reader thread
- Dispatch incoming messages to the appropriate handler based on the `type` field
- Handle socket disconnection gracefully with reconnect attempt and user-visible error

**Recommended:** Use a single `HandlerThread` or `Thread + Handler` pair. Do NOT use `AsyncTask` (deprecated). `IntentService` or plain `Thread` with `Looper` works.

### Module 3 — Progress & Result Screen

Shown immediately after Submit. Navigate to it programmatically after socket send is confirmed.

**Components:**
- `ProgressBar` (horizontal, determinate) — set `max = totalSubTasks` from `JOB_ACK`
- `TextView` showing "X of Y sub-tasks complete" updated on each `PROGRESS_UPDATE`
- Job type label (MatMul / Hash Crack) and submitted `jobId`
- On `JOB_RESULT`: hide progress bar, show result in scrollable `TextView`
  - MatMul: display matrix dimensions + preview of top-left 3×3 corner of `matrixC`
  - Hash Crack: display cracked value prominently, or "Not found" if null

> **CRITICAL:** All UI updates from the reader thread must use `runOnUiThread(() -> { ... })` or post to a Handler. Direct UI manipulation from a background thread will crash the app.

---

## Package Structure

```
com.offloadhpc.mobile
  ui/
    MainActivity.java          -- hosts tabs
    JobLauncherFragment.java   -- MatMul + Hash tabs
    ProgressActivity.java      -- progress bar + result
  network/
    SocketClient.java          -- TCP connection + reader loop
    MessageDispatcher.java     -- routes incoming messages by type
  model/
    JobRequest.java            -- POJO for outgoing job
    JobResult.java             -- POJO for incoming result
    ProgressUpdate.java        -- POJO for progress updates
```

---

## Week-by-Week Tasks

| Week | Tasks |
|------|-------|
| **W1** | Set up Android Studio project. Create package structure (`ui`, `network`, `model`). Add Gson dependency to `build.gradle`. Verify build runs on emulator or phone. Review and approve `WorkerService.java` contract. |
| **W2** | Build Job Launcher screen with both tabs. Implement Socket Client skeleton (connect, send, log raw response). Test sending a hardcoded `JOB_SUBMIT` and printing the raw Broker reply to Logcat. |
| **W3** | Build Progress & Result screen. Wire up `ProgressBar` to `PROGRESS_UPDATE` messages. Wire up result display to `JOB_RESULT`. Handle `JOB_ACK` to initialise progress bar max value. |
| **W4** | Internal integration: test full MatMul flow end-to-end against a locally running Broker stub. Fix threading issues. |
| **W5** | Full E2E MatMul integration with real workers. Debug progress bar updates. Verify result display for all 3 matrix sizes. |
| **W6** | Hash Crack integration. Edge cases: null result? Connection drops mid-job? Add basic error toasts. |
| **W7** | Testing: try large matrix (512×512), long hash crack. UI polish. Prepare demo script. Begin slides contribution. |
| **W8** | Report contribution: write Android architecture section, UI screenshots, threading model explanation. Demo support. |

---

## Key Implementation Notes

- Use **Gson** for JSON — add `implementation 'com.google.code.gson:gson:2.10.1'` to `build.gradle`
- Matrix generation: `double[][] matrix = new double[n][n]`, fill with `Math.random()`
- Large matrices (512×512) produce ~2MB JSON. Test that socket buffer handles this without truncation
- Store Broker IP as a **configurable string constant** — do not hardcode; you will change networks during demo
- Test on a **real Android device over Wi-Fi**, not just the emulator. Emulator networking is different

---

## Demo Checklist (Your Part)

1. Launch Android app on phone connected to the same Wi-Fi as Broker
2. Submit a MatMul job (256×256) — show progress bar updating and final result
3. Submit a Hash Crack job (short known password, maxLength=4) — show cracked value returned
4. Keep it crisp — two jobs, two results, progress bar moving
