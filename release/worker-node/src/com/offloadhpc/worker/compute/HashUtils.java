package com.offloadhpc.worker.compute;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * HashUtils — MessageDigest wrapper for MD5 and SHA-1 hashing.
 */
public class HashUtils {

    /**
     * Hash a string using the specified algorithm (MD5 or SHA-1).
     *
     * @param input     the plaintext string to hash
     * @param algorithm "MD5" or "SHA-1"
     * @return lowercase hex string of the hash, or null on error
     */
    public static String hash(String input, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("[HashUtils] Unsupported algorithm: " + algorithm);
            return null;
        }
    }

    /**
     * Convert a byte array to a lowercase hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
