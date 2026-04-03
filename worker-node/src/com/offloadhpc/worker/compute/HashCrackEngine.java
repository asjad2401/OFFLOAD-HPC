package com.offloadhpc.worker.compute;

/**
 * HashCrackEngine — brute-force hash cracking over a keyspace slice.
 *
 * Takes a numeric index range [startIndex, endIndex) and iterates through
 * candidate strings, hashing each and comparing to the target hash.
 */
public class HashCrackEngine {

    /**
     * Attempt to crack a hash by brute-forcing a keyspace slice.
     *
     * @param targetHash the target hash to match (hex string)
     * @param algorithm  "MD5" or "SHA-1"
     * @param charset    characters to use for candidate generation
     * @param maxLength  maximum candidate string length
     * @param startIndex start of keyspace slice (inclusive)
     * @param endIndex   end of keyspace slice (exclusive)
     * @return the cracked plaintext, or null if not found in this range
     */
    public static String crack(String targetHash, String algorithm,
            String charset, int maxLength,
            long startIndex, long endIndex) {

        for (long idx = startIndex; idx < endIndex; idx++) {
            String candidate = indexToString(idx, charset, maxLength);
            if (candidate == null) {
                // Index exceeds the total keyspace — stop early
                break;
            }
            String hashed = HashUtils.hash(candidate, algorithm);
            if (hashed != null && hashed.equalsIgnoreCase(targetHash)) {
                return candidate;
            }
        }

        return null; // not found in this range
    }

    /**
     * Convert a numeric index to a candidate string.
     *
     * The keyspace is ordered by length:
     * indices 0 .. base-1 → length 1 strings
     * indices base .. base+base^2-1 → length 2 strings
     * etc.
     *
     * @param index   the global keyspace index
     * @param charset the character set to use
     * @param maxLen  maximum candidate length
     * @return the candidate string, or null if index exceeds keyspace
     */
    public static String indexToString(long index, String charset, int maxLen) {
        int base = charset.length();
        int len = 1;
        long count = base;

        // Determine which length bucket this index falls into
        while (index >= count && len < maxLen) {
            index -= count;
            len++;
            count = (long) Math.pow(base, len);
        }

        // If index still exceeds the current bucket, it's out of keyspace
        if (index >= count) {
            return null;
        }

        // Build the string from the adjusted index within the length bucket
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.insert(0, charset.charAt((int) (index % base)));
            index /= base;
        }

        return sb.toString();
    }

    /**
     * Compute the total keyspace size for a given charset and max length.
     * Total = base^1 + base^2 + ... + base^maxLength
     *
     * @param charset   the character set
     * @param maxLength maximum candidate length
     * @return total number of candidates
     */
    public static long totalKeyspace(String charset, int maxLength) {
        int base = charset.length();
        long total = 0;
        long power = base;
        for (int len = 1; len <= maxLength; len++) {
            total += power;
            power *= base;
        }
        return total;
    }
}
