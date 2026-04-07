package com.offloadhpc.worker.test;

import com.offloadhpc.worker.compute.MatMulEngine;
import com.offloadhpc.worker.compute.HashCrackEngine;
import com.offloadhpc.worker.compute.HashUtils;

/**
 * WorkerTest — simple unit tests for the Worker Node compute engines.
 * Run with: java -cp src com.offloadhpc.worker.test.WorkerTest
 */
public class WorkerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Worker Node Unit Tests ===\n");

        testHashUtils();
        testIndexToString();
        testTotalKeyspace();
        testMatMulEngine();
        testHashCrackEngine();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ========== HashUtils Tests ==========

    private static void testHashUtils() {
        System.out.println("-- HashUtils Tests --");

        // MD5("abc") = 900150983cd24fb0d6963f7d28e17f72
        assertEqual("MD5 of 'abc'",
                "900150983cd24fb0d6963f7d28e17f72",
                HashUtils.hash("abc", "MD5"));

        // MD5("") = d41d8cd98f00b204e9800998ecf8427e
        assertEqual("MD5 of empty string",
                "d41d8cd98f00b204e9800998ecf8427e",
                HashUtils.hash("", "MD5"));

        // SHA-1("abc") = a9993e364706816aba3e25717850c26c9cd0d89d
        assertEqual("SHA-1 of 'abc'",
                "a9993e364706816aba3e25717850c26c9cd0d89d",
                HashUtils.hash("abc", "SHA-1"));

        // MD5("password") = 5f4dcc3b5aa765d61d8327deb882cf99
        assertEqual("MD5 of 'password'",
                "5f4dcc3b5aa765d61d8327deb882cf99",
                HashUtils.hash("password", "MD5"));
    }

    // ========== indexToString Tests ==========

    private static void testIndexToString() {
        System.out.println("\n-- indexToString Tests --");
        String charset = "abcdefghijklmnopqrstuvwxyz";

        // Length-1 strings: indices 0..25
        assertEqual("index 0 → 'a'", "a", HashCrackEngine.indexToString(0, charset, 5));
        assertEqual("index 1 → 'b'", "b", HashCrackEngine.indexToString(1, charset, 5));
        assertEqual("index 25 → 'z'", "z", HashCrackEngine.indexToString(25, charset, 5));

        // Length-2 strings: indices 26..701 (26 + 26^2 - 1)
        assertEqual("index 26 → 'aa'", "aa", HashCrackEngine.indexToString(26, charset, 5));
        assertEqual("index 27 → 'ab'", "ab", HashCrackEngine.indexToString(27, charset, 5));
        assertEqual("index 51 → 'az'", "az", HashCrackEngine.indexToString(51, charset, 5));
        assertEqual("index 52 → 'ba'", "ba", HashCrackEngine.indexToString(52, charset, 5));

        // Shorter charset test: charset "ab" (base 2)
        String cs2 = "ab";
        assertEqual("base2 index 0 → 'a'", "a", HashCrackEngine.indexToString(0, cs2, 3));
        assertEqual("base2 index 1 → 'b'", "b", HashCrackEngine.indexToString(1, cs2, 3));
        assertEqual("base2 index 2 → 'aa'", "aa", HashCrackEngine.indexToString(2, cs2, 3));
        assertEqual("base2 index 3 → 'ab'", "ab", HashCrackEngine.indexToString(3, cs2, 3));
        assertEqual("base2 index 4 → 'ba'", "ba", HashCrackEngine.indexToString(4, cs2, 3));
        assertEqual("base2 index 5 → 'bb'", "bb", HashCrackEngine.indexToString(5, cs2, 3));
        assertEqual("base2 index 6 → 'aaa'", "aaa", HashCrackEngine.indexToString(6, cs2, 3));
    }

    // ========== totalKeyspace Tests ==========

    private static void testTotalKeyspace() {
        System.out.println("\n-- totalKeyspace Tests --");

        // charset "ab" (base 2), maxLength 3: 2 + 4 + 8 = 14
        assertEqual("base2 maxLen3 keyspace",
                String.valueOf(14L),
                String.valueOf(HashCrackEngine.totalKeyspace("ab", 3)));

        // charset "abc" (base 3), maxLength 2: 3 + 9 = 12
        assertEqual("base3 maxLen2 keyspace",
                String.valueOf(12L),
                String.valueOf(HashCrackEngine.totalKeyspace("abc", 2)));

        // charset a-z (base 26), maxLength 1: 26
        String az = "abcdefghijklmnopqrstuvwxyz";
        assertEqual("base26 maxLen1 keyspace",
                String.valueOf(26L),
                String.valueOf(HashCrackEngine.totalKeyspace(az, 1)));
    }

    // ========== MatMulEngine Tests ==========

    private static void testMatMulEngine() {
        System.out.println("\n-- MatMulEngine Tests --");

        // 3x3 matrix multiplication
        // A = [[1,2,3],[4,5,6],[7,8,9]]
        // B = [[9,8,7],[6,5,4],[3,2,1]]
        // C = A × B = [[30,24,18],[84,69,54],[138,114,90]]
        double[][] A = { { 1, 2, 3 }, { 4, 5, 6 }, { 7, 8, 9 } };
        double[][] B = { { 9, 8, 7 }, { 6, 5, 4 }, { 3, 2, 1 } };

        // Full multiplication (all rows)
        double[][] C = MatMulEngine.compute(A, B, 0, 3);
        assertEqual("MatMul C[0][0]", "30.0", String.valueOf(C[0][0]));
        assertEqual("MatMul C[0][1]", "24.0", String.valueOf(C[0][1]));
        assertEqual("MatMul C[0][2]", "18.0", String.valueOf(C[0][2]));
        assertEqual("MatMul C[1][0]", "84.0", String.valueOf(C[1][0]));
        assertEqual("MatMul C[1][1]", "69.0", String.valueOf(C[1][1]));
        assertEqual("MatMul C[2][2]", "90.0", String.valueOf(C[2][2]));

        // Partial multiplication (rows 1-2 only)
        double[][] partial = MatMulEngine.compute(A, B, 1, 3);
        assertEqual("Partial rows", "2", String.valueOf(partial.length));
        assertEqual("Partial C[0][0] (row 1)", "84.0", String.valueOf(partial[0][0]));
        assertEqual("Partial C[1][0] (row 2)", "138.0", String.valueOf(partial[1][0]));
    }

    // ========== HashCrackEngine Tests ==========

    private static void testHashCrackEngine() {
        System.out.println("\n-- HashCrackEngine Tests --");

        String charset = "abcdefghijklmnopqrstuvwxyz";

        // Crack MD5("abc") = 900150983cd24fb0d6963f7d28e17f72
        String result1 = HashCrackEngine.crack(
                "900150983cd24fb0d6963f7d28e17f72", "MD5",
                charset, 3, 0, HashCrackEngine.totalKeyspace(charset, 3));
        assertEqual("Crack MD5('abc')", "abc", result1);

        // Crack MD5("cat") = d077f244def8a70e5ea758bd8352fcd8
        String result2 = HashCrackEngine.crack(
                "d077f244def8a70e5ea758bd8352fcd8", "MD5",
                charset, 3, 0, HashCrackEngine.totalKeyspace(charset, 3));
        assertEqual("Crack MD5('cat')", "cat", result2);

        // Search range that does NOT contain the answer
        String result3 = HashCrackEngine.crack(
                "900150983cd24fb0d6963f7d28e17f72", "MD5",
                charset, 3, 0, 5); // only first 5 candidates
        assertEqual("Crack not in range", null, result3);
    }

    // ========== Helper ==========

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
}
