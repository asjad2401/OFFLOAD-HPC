# OFFLOAD-HPC

**OFFLOAD-HPC** is a distributed High-Performance Computing (HPC) grid system designed to offload compute-intensive tasks from mobile devices to a local network of PCs. By pooling the CPU resources of idle machines on a LAN, OFFLOAD-HPC accelerates processing times and significantly reduces battery drain on mobile clients.

## 🌟 Key Features

*   **Mobile Compute Offloading:** Seamlessly offload heavy workloads (like Matrix Multiplication, Hash Cracking, and Image Processing) from an Android device to a powerful local grid.
*   **Automatic Node Discovery:** Nodes and mobile clients automatically discover the grid using UDP broadcast/multicast—no hardcoded IP addresses required.
*   **Dynamic Role Election (Bully Algorithm):** The grid is self-organizing. It uses a robust implementation of the Bully Election algorithm to automatically elect a "Broker" node. If the Broker goes offline, the remaining nodes instantly elect a new one, ensuring high availability.
*   **Distributed Task Processing:** The Broker divides large jobs into smaller sub-tasks and distributes them across available "Worker" nodes using Java RMI (Remote Method Invocation) for parallel execution.
*   **Cross-Platform Architecture:** The grid nodes run on any system supporting Java, while the client interface is a native Android application.

## 🏗️ System Architecture

The system consists of two main components:

1.  **Grid Nodes (Java PCs):**
    *   **Broker Role:** Manages the grid. Discovers workers, accepts jobs from mobile clients, partitions jobs into sub-tasks, schedules them to workers, aggregates the results, and sends them back to the client.
    *   **Worker Role:** Provides compute power. Registers with the Broker, receives sub-tasks via RMI, executes them, and returns the results.
2.  **Mobile Client (Android):**
    *   Initiates jobs, streams large payloads (like image data) efficiently to the Broker, and displays the processed results.

## 🚀 Supported Workloads

*   **Matrix Multiplication (MatMul):** Distributed calculation of large matrices.
*   **Hash Cracking:** Parallelized brute-force hash cracking.
*   **Image Processing:** Distributed application of filters (Grayscale, Edge Detection, Blur) on high-resolution images.
*   **K-Means Clustering:** Distributed data clustering.

---

## 🛠️ Installation & Setup

### Prerequisites

**For Grid Nodes:**
*   **Java Runtime Environment (JRE/JDK):** Version 8 or higher.
*   **OS:** Windows, macOS, or Linux (Tested primarily on Windows).
*   **Network:** All nodes and mobile devices must be connected to the **same Local Area Network (LAN/Wi-Fi)**.

**For Mobile App:**
*   **Android Device:** Android 8.0 (Oreo) or higher.
*   **Android Studio:** Required only if you want to build the APK from source.

### Running a Grid Node (Quick Start)

The easiest way to run a node is using the pre-packaged executable JAR.

1.  Navigate to the release directory (`release/V2/`).
2.  Ensure you have the following two files in the same folder:
    *   `OFFLOAD-HPC.jar`
    *   `start-grid-node.bat` (Windows)
3.  Double-click `start-grid-node.bat`.
4.  The prompt will ask you for a unique **Node ID** (e.g., `pc-1`, `laptop`) and a **Priority** (higher number = more likely to become the Broker).
5.  A GUI dashboard will open, showing the node's current status, role, connected mobile devices, and active sub-tasks.

*To run from the command line:*
```bash
java -jar OFFLOAD-HPC.jar <NodeID> <Priority>
# Example: java -jar OFFLOAD-HPC.jar Worker-Alpha 10
```

### Running the Android App

1.  Install the `app-debug.apk` on your Android device. (In folder: `android-app/app/build/outputs/apk/debug/app-debug.apk`)
2.  Ensure your phone is connected to the same Wi-Fi network as your Grid Nodes.
3.  Open the app. It will automatically broadcast a discovery request and connect to the active Broker.
4.  Select a workload tab (e.g., Image Proc), configure the settings, and tap **Submit**.

---

## 👨‍💻 Building from Source

If you want to modify the code or build the binaries yourself:

### Building the Grid Node
1.  Navigate to the `grid-node/` directory.
2.  Run the build script:
    ```cmd
    build-jar.bat
    ```
3.  This will compile the Java classes, bundle the required `json-20231013.jar` dependency, and output a fat JAR at `target/OFFLOAD-HPC.jar`.

### Building the Android App
1.  Open the `android-app/` folder in Android Studio.
2.  Sync Gradle dependencies.
3.  Build and Run on your connected device or emulator.

---

## 🧩 Troubleshooting

*   **Mobile app says "Not connected to Grid":** Ensure a Grid Node is running and currently holds the "Broker" role. Verify that the PC and the phone are on the exact same Wi-Fi subnet. You may need to check your PC's firewall settings to ensure Java is allowed to communicate over UDP port 5000 and TCP port 9000.
*   **Node isn't joining the grid:** Ensure other nodes are running. The console will display `[Registry] Heartbeat from unknown/evicted worker` if a node was temporarily disconnected and needs to restart.
*   **Out of Memory (OOM) Errors on large images:** The mobile app automatically downsamples very large images (to max 512x512) before sending them to the grid to prevent heap exhaustion.

## 📄 License
*Specify your license here (e.g., MIT License, GPL, etc.)*
