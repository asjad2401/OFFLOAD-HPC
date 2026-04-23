package com.offloadhpc.worker.test;

import com.offloadhpc.worker.compute.MatMulEngine;
import com.offloadhpc.worker.compute.HashCrackEngine;
import com.offloadhpc.worker.compute.HashUtils;
import com.offloadhpc.worker.compute.ImageProcEngine;
import com.offloadhpc.worker.compute.KMeansEngine;

/**
 * WorkerTest — unit tests for all compute engines.
 * v2.0 — extended with ImageProc and KMeans tests.
 * Run with: java -cp target\classes com.offloadhpc.worker.test.WorkerTest
 */
public class WorkerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Grid Node v2.0 Unit Tests ===\n");

        testHashUtils();
        testIndexToString();
        testTotalKeyspace();
        testMatMulEngine();
        testHashCrackEngine();
        testImageProcGrayscale();
        testImageProcEdgeDetect();
        testImageProcBlur();
        testKMeansEngine();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ========== HashUtils Tests ==========

    private static void testHashUtils() {
        System.out.println("-- HashUtils Tests --");

        assertEqual("MD5 of 'abc'",
                "900150983cd24fb0d6963f7d28e17f72",
                HashUtils.hash("abc", "MD5"));

        assertEqual("MD5 of empty string",
                "d41d8cd98f00b204e9800998ecf8427e",
                HashUtils.hash("", "MD5"));

        assertEqual("SHA-1 of 'abc'",
                "a9993e364706816aba3e25717850c26c9cd0d89d",
                HashUtils.hash("abc", "SHA-1"));

        assertEqual("MD5 of 'password'",
                "5f4dcc3b5aa765d61d8327deb882cf99",
                HashUtils.hash("password", "MD5"));
    }

    // ========== indexToString Tests ==========

    private static void testIndexToString() {
        System.out.println("\n-- indexToString Tests --");
        String charset = "abcdefghijklmnopqrstuvwxyz";

        assertEqual("index 0 → 'a'", "a", HashCrackEngine.indexToString(0, charset, 5));
        assertEqual("index 1 → 'b'", "b", HashCrackEngine.indexToString(1, charset, 5));
        assertEqual("index 25 → 'z'", "z", HashCrackEngine.indexToString(25, charset, 5));
        assertEqual("index 26 → 'aa'", "aa", HashCrackEngine.indexToString(26, charset, 5));
        assertEqual("index 27 → 'ab'", "ab", HashCrackEngine.indexToString(27, charset, 5));
    }

    // ========== totalKeyspace Tests ==========

    private static void testTotalKeyspace() {
        System.out.println("\n-- totalKeyspace Tests --");

        assertEqual("base2 maxLen3 keyspace",
                String.valueOf(14L),
                String.valueOf(HashCrackEngine.totalKeyspace("ab", 3)));

        assertEqual("base3 maxLen2 keyspace",
                String.valueOf(12L),
                String.valueOf(HashCrackEngine.totalKeyspace("abc", 2)));
    }

    // ========== MatMulEngine Tests ==========

    private static void testMatMulEngine() {
        System.out.println("\n-- MatMulEngine Tests --");

        double[][] A = { { 1, 2, 3 }, { 4, 5, 6 }, { 7, 8, 9 } };
        double[][] B = { { 9, 8, 7 }, { 6, 5, 4 }, { 3, 2, 1 } };

        double[][] C = MatMulEngine.compute(A, B, 0, 3);
        assertEqual("MatMul C[0][0]", "30.0", String.valueOf(C[0][0]));
        assertEqual("MatMul C[0][1]", "24.0", String.valueOf(C[0][1]));
        assertEqual("MatMul C[1][0]", "84.0", String.valueOf(C[1][0]));
        assertEqual("MatMul C[2][2]", "90.0", String.valueOf(C[2][2]));

        double[][] partial = MatMulEngine.compute(A, B, 1, 3);
        assertEqual("Partial rows", "2", String.valueOf(partial.length));
        assertEqual("Partial C[0][0] (row 1)", "84.0", String.valueOf(partial[0][0]));
    }

    // ========== HashCrackEngine Tests ==========

    private static void testHashCrackEngine() {
        System.out.println("\n-- HashCrackEngine Tests --");

        String charset = "abcdefghijklmnopqrstuvwxyz";

        String result1 = HashCrackEngine.crack(
                "900150983cd24fb0d6963f7d28e17f72", "MD5",
                charset, 3, 0, HashCrackEngine.totalKeyspace(charset, 3));
        assertEqual("Crack MD5('abc')", "abc", result1);

        String result2 = HashCrackEngine.crack(
                "d077f244def8a70e5ea758bd8352fcd8", "MD5",
                charset, 3, 0, HashCrackEngine.totalKeyspace(charset, 3));
        assertEqual("Crack MD5('cat')", "cat", result2);

        String result3 = HashCrackEngine.crack(
                "900150983cd24fb0d6963f7d28e17f72", "MD5",
                charset, 3, 0, 5);
        assertEqual("Crack not in range", null, result3);
    }

    // ========== ImageProcEngine Tests ==========

    private static void testImageProcGrayscale() {
        System.out.println("\n-- ImageProcEngine Grayscale Tests --");

        // 3x2 image: pure red, pure green, pure blue, white, black, gray
        int[] pixels = {
                0xFFFF0000, 0xFF00FF00, 0xFF0000FF, // row 0: red, green, blue
                0xFFFFFFFF, 0xFF000000, 0xFF808080 // row 1: white, black, gray
        };

        int[] result = ImageProcEngine.grayscale(pixels, 3, 0, 2);
        assertEqual("Grayscale pixels count", String.valueOf(6), String.valueOf(result.length));

        // Red → gray(76), Green → gray(150), Blue → gray(29)
        int redGray = (result[0] >> 16) & 0xFF;
        assertEqual("Red grayscale R channel", "76", String.valueOf(redGray));

        int greenGray = (result[1] >> 16) & 0xFF;
        assertEqual("Green grayscale R channel", "149", String.valueOf(greenGray));

        // White → gray(255)
        int whiteGray = (result[3] >> 16) & 0xFF;
        assertEqual("White grayscale", "255", String.valueOf(whiteGray));

        // Black → gray(0)
        int blackGray = (result[4] >> 16) & 0xFF;
        assertEqual("Black grayscale", "0", String.valueOf(blackGray));
    }

    private static void testImageProcEdgeDetect() {
        System.out.println("\n-- ImageProcEngine EdgeDetect Tests --");

        // 4x4 white image with black square in middle → edges should be detected
        int W = 0xFFFFFFFF;
        int B = 0xFF000000;
        int[] pixels = {
                W, W, W, W,
                W, B, B, W,
                W, B, B, W,
                W, W, W, W
        };

        int[] result = ImageProcEngine.edgeDetect(pixels, 4, 4, 0, 4);
        assertEqual("EdgeDetect pixel count", String.valueOf(16), String.valueOf(result.length));

        // Border pixels should be black
        int corner = (result[0] >> 16) & 0xFF;
        assertEqual("EdgeDetect corner is black", "0", String.valueOf(corner));

        // Interior boundary pixel [1][1] should have non-zero magnitude
        int edge = (result[5] >> 16) & 0xFF; // row 1, col 1
        assertTrue("EdgeDetect boundary has magnitude > 0", edge > 0);
    }

    private static void testImageProcBlur() {
        System.out.println("\n-- ImageProcEngine Blur Tests --");

        // 3x3 image: center pixel is white, rest black
        int B = 0xFF000000;
        int W = 0xFFFFFFFF;
        int[] pixels = {
                B, B, B,
                B, W, B,
                B, B, B
        };

        int[] result = ImageProcEngine.blur(pixels, 3, 3, 0, 3);
        assertEqual("Blur pixel count", String.valueOf(9), String.valueOf(result.length));

        // Center pixel [1][1] should be blurred (average of 8 blacks + 1 white = 255/9
        // ≈ 28)
        int centerR = (result[4] >> 16) & 0xFF;
        assertTrue("Blur center R < 255", centerR < 255);
        assertTrue("Blur center R > 0", centerR > 0);
    }

    // ========== KMeansEngine Tests ==========

    private static void testKMeansEngine() {
        System.out.println("\n-- KMeansEngine Tests --");

        // 6 points in 2D, 2 clusters
        double[][] data = {
                { 1, 1 }, { 2, 1 }, { 1, 2 }, // cluster near (1.33, 1.33)
                { 10, 10 }, { 11, 10 }, { 10, 11 } // cluster near (10.33, 10.33)
        };
        double[][] centroids = { { 0, 0 }, { 12, 12 } };

        // Full range
        double[][] result = KMeansEngine.computePartial(data, centroids, 0, 6);
        assertEqual("KMeans result rows", "2", String.valueOf(result.length));
        assertEqual("KMeans result cols", "3", String.valueOf(result[0].length)); // dims+1

        // Cluster 0 should have 3 points summing to (4, 4)
        assertEqual("KMeans cluster 0 sum_x", "4.0", String.valueOf(result[0][0]));
        assertEqual("KMeans cluster 0 sum_y", "4.0", String.valueOf(result[0][1]));
        assertEqual("KMeans cluster 0 count", "3.0", String.valueOf(result[0][2]));

        // Cluster 1 should have 3 points summing to (31, 31)
        assertEqual("KMeans cluster 1 sum_x", "31.0", String.valueOf(result[1][0]));
        assertEqual("KMeans cluster 1 sum_y", "31.0", String.valueOf(result[1][1]));
        assertEqual("KMeans cluster 1 count", "3.0", String.valueOf(result[1][2]));

        // Test new centroids computation
        double[][] newCentroids = KMeansEngine.computeNewCentroids(result, centroids);
        String c0x = String.format("%.2f", newCentroids[0][0]);
        assertEqual("New centroid 0 x", "1.33", c0x);
        String c1x = String.format("%.2f", newCentroids[1][0]);
        assertEqual("New centroid 1 x", "10.33", c1x);

        // Test partial range (only first 3 points)
        double[][] partial = KMeansEngine.computePartial(data, centroids, 0, 3);
        assertEqual("Partial cluster 0 count", "3.0", String.valueOf(partial[0][2]));
        assertEqual("Partial cluster 1 count", "0.0", String.valueOf(partial[1][2]));
    }

    // ========== Helpers ==========

    private static void assertEqual(String testName, String expected, String actual) {
        if ((expected == null && actual == null) ||
                (expected != null && expected.equals(actual))) {
            System.out.println("  ✓ " + testName);
            passed++;
        } else {
            System.out.println("  ✗ " + testName + " — expected: " + expected + ", got: " + actual);
            failed++;
        }
    }

    private static void assertTrue(String testName, boolean condition) {
        if (condition) {
            System.out.println("  ✓ " + testName);
            passed++;
        } else {
            System.out.println("  ✗ " + testName + " — expected true");
            failed++;
        }
    }
}
