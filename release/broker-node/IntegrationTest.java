import java.io.*;
import java.net.*;

/**
 * Integration test client for OFFLOAD-HPC.
 * Uses a SINGLE persistent TCP connection (matching Android app behavior).
 *
 * Usage: java IntegrationTest [brokerHost] [brokerPort]
 * Default: java IntegrationTest 127.0.0.1 9000
 */
public class IntegrationTest {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9000;

        System.out.println("=== OFFLOAD-HPC Integration Test ===");
        System.out.println("Connecting to Broker at " + host + ":" + port + "...\n");

        try (Socket socket = new Socket(host, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            socket.setSoTimeout(120000); // 2 minute timeout

            // Test 1: MatMul 4x4
            System.out.println("--- TEST 1: MatMul 4x4 (2 workers = 2 sub-tasks) ---");
            boolean test1 = runMatMulTest(out, in);

            // Test 2: Hash Crack MD5("cat")
            System.out.println("\n--- TEST 2: Hash Crack MD5('cat') (2 workers = 2 sub-tasks) ---");
            boolean test2 = runHashCrackTest(out, in);

            // Summary
            System.out.println("\n=== SUMMARY ===");
            System.out.println("  MatMul:     " + (test1 ? "[PASS]" : "[FAIL]"));
            System.out.println("  Hash Crack: " + (test2 ? "[PASS]" : "[FAIL]"));

            if (test1 && test2) {
                System.out.println("\n  All tests PASSED!");
            } else {
                System.out.println("\n  Some tests FAILED!");
                System.exit(1);
            }
        }
    }

    static boolean runMatMulTest(PrintWriter out, BufferedReader in) throws Exception {
        // A = 4x4 identity, B = known values => C should equal B
        String matA = "[[1,0,0,0],[0,1,0,0],[0,0,1,0],[0,0,0,1]]";
        String matB = "[[2,3,4,5],[6,7,8,9],[10,11,12,13],[14,15,16,17]]";

        String job = "{\"type\":\"JOB_SUBMIT\",\"jobId\":\"test-matmul-001\",\"jobType\":\"MATMUL\","
                + "\"payload\":{\"matrixSize\":4,\"matrixA\":" + matA + ",\"matrixB\":" + matB + "}}";

        out.println(job);
        out.flush();
        System.out.println("  -> Job sent");

        return readResults(in, "test-matmul-001");
    }

    static boolean runHashCrackTest(PrintWriter out, BufferedReader in) throws Exception {
        // MD5("cat") = d077f244def8a70e5ea758bd8352fcd8
        String job = "{\"type\":\"JOB_SUBMIT\",\"jobId\":\"test-hash-001\",\"jobType\":\"HASH_CRACK\","
                + "\"payload\":{\"targetHash\":\"d077f244def8a70e5ea758bd8352fcd8\","
                + "\"algorithm\":\"MD5\",\"charset\":\"abcdefghijklmnopqrstuvwxyz\",\"maxLength\":3}}";

        out.println(job);
        out.flush();
        System.out.println("  -> Job sent");

        return readResults(in, "test-hash-001");
    }

    static boolean readResults(BufferedReader in, String expectedJobId) throws Exception {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.contains("\"JOB_ACK\"")) {
                System.out.println("  <- JOB_ACK: " + abbreviate(line, 100));
            } else if (line.contains("\"PROGRESS_UPDATE\"")) {
                System.out.println("  <- PROGRESS: " + abbreviate(line, 100));
            } else if (line.contains("\"JOB_RESULT\"")) {
                System.out.println("  <- RESULT: " + abbreviate(line, 200));
                if (line.contains("\"SUCCESS\"")) {
                    System.out.println("  [PASS] TEST PASSED");
                    return true;
                } else {
                    System.out.println("  [FAIL] TEST FAILED - status is not SUCCESS");
                    return false;
                }
            } else {
                System.out.println("  <- " + abbreviate(line, 120));
            }
        }
        System.out.println("  [FAIL] Connection closed before result received");
        return false;
    }

    static String abbreviate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
