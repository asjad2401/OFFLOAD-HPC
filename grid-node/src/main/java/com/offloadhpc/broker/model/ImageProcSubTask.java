package com.offloadhpc.broker.model;

/**
 * Sub-task for Image Processing jobs.
 * Carries full pixel data plus the row range this worker should process.
 */
public class ImageProcSubTask extends SubTask {

    private final int[] pixelData;
    private final int width;
    private final int height;
    private final int startRow;
    private final int endRow;
    private final String operation;

    public ImageProcSubTask(String subTaskId, int[] pixelData, int width, int height,
            int startRow, int endRow, String operation) {
        super(subTaskId);
        this.pixelData = pixelData;
        this.width = width;
        this.height = height;
        this.startRow = startRow;
        this.endRow = endRow;
        this.operation = operation;
    }

    public int[] getPixelData() {
        return pixelData;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getStartRow() {
        return startRow;
    }

    public int getEndRow() {
        return endRow;
    }

    public String getOperation() {
        return operation;
    }
}
