package com.offloadhpc.broker.model;

/**
 * Sub-task representing an index-range slice of a Hash Crack job.
 */
public class HashCrackSubTask extends SubTask {

    private final String targetHash;
    private final String algorithm;
    private final String charset;
    private final int maxLength;
    private final long startIndex;
    private final long endIndex;

    public HashCrackSubTask(String subTaskId, String targetHash, String algorithm,
            String charset, int maxLength,
            long startIndex, long endIndex) {
        super(subTaskId);
        this.targetHash = targetHash;
        this.algorithm = algorithm;
        this.charset = charset;
        this.maxLength = maxLength;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    public String getTargetHash() {
        return targetHash;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getCharset() {
        return charset;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public long getStartIndex() {
        return startIndex;
    }

    public long getEndIndex() {
        return endIndex;
    }
}
