package com.offloadhpc.worker.compute;

/**
 * ImageProcEngine — pure-Java image processing on a pixel strip.
 *
 * All operations work on flat ARGB int arrays.
 * Supported operations: GRAYSCALE, EDGE_DETECT (Sobel), BLUR (box blur 3x3).
 *
 * Each method processes rows [startRow, endRow) of the image.
 * For edge detection and blur, border rows need 1-row padding from
 * adjacent strips (the full image is passed to avoid boundary issues).
 */
public class ImageProcEngine {

    /**
     * Dispatch to the appropriate operation.
     */
    public static int[] process(int[] pixels, int width, int height,
            int startRow, int endRow, String operation) {
        switch (operation.toUpperCase()) {
            case "GRAYSCALE":
                return grayscale(pixels, width, startRow, endRow);
            case "EDGE_DETECT":
                return edgeDetect(pixels, width, height, startRow, endRow);
            case "BLUR":
                return blur(pixels, width, height, startRow, endRow);
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    /**
     * Convert pixel strip to grayscale using luminance formula.
     * Y = 0.299*R + 0.587*G + 0.114*B
     */
    public static int[] grayscale(int[] pixels, int width, int startRow, int endRow) {
        int stripRows = endRow - startRow;
        int[] result = new int[stripRows * width];

        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < width; col++) {
                int idx = row * width + col;
                int pixel = pixels[idx];

                int a = (pixel >> 24) & 0xFF;
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                gray = Math.min(255, Math.max(0, gray));

                int outIdx = (row - startRow) * width + col;
                result[outIdx] = (a << 24) | (gray << 16) | (gray << 8) | gray;
            }
        }
        return result;
    }

    /**
     * Sobel edge detection on pixel strip.
     * Uses 3x3 Sobel kernels for X and Y gradients.
     */
    public static int[] edgeDetect(int[] pixels, int width, int height,
            int startRow, int endRow) {
        int stripRows = endRow - startRow;
        int[] result = new int[stripRows * width];

        // Sobel kernels
        int[][] sobelX = { { -1, 0, 1 }, { -2, 0, 2 }, { -1, 0, 1 } };
        int[][] sobelY = { { -1, -2, -1 }, { 0, 0, 0 }, { 1, 2, 1 } };

        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < width; col++) {
                int outIdx = (row - startRow) * width + col;

                // Border pixels → black
                if (row == 0 || row == height - 1 || col == 0 || col == width - 1) {
                    result[outIdx] = 0xFF000000; // opaque black
                    continue;
                }

                double gx = 0, gy = 0;
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int idx = (row + ky) * width + (col + kx);
                        int pixel = pixels[idx];
                        // Convert to grayscale intensity
                        int r = (pixel >> 16) & 0xFF;
                        int g = (pixel >> 8) & 0xFF;
                        int b = pixel & 0xFF;
                        double intensity = 0.299 * r + 0.587 * g + 0.114 * b;

                        gx += intensity * sobelX[ky + 1][kx + 1];
                        gy += intensity * sobelY[ky + 1][kx + 1];
                    }
                }

                int magnitude = (int) Math.min(255, Math.sqrt(gx * gx + gy * gy));
                result[outIdx] = (0xFF << 24) | (magnitude << 16) | (magnitude << 8) | magnitude;
            }
        }
        return result;
    }

    /**
     * Box blur (3x3 averaging) on pixel strip.
     */
    public static int[] blur(int[] pixels, int width, int height,
            int startRow, int endRow) {
        int stripRows = endRow - startRow;
        int[] result = new int[stripRows * width];

        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < width; col++) {
                int outIdx = (row - startRow) * width + col;

                // Border pixels → copy original
                if (row == 0 || row == height - 1 || col == 0 || col == width - 1) {
                    result[outIdx] = pixels[row * width + col];
                    continue;
                }

                int sumR = 0, sumG = 0, sumB = 0, sumA = 0;
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int idx = (row + ky) * width + (col + kx);
                        int pixel = pixels[idx];
                        sumA += (pixel >> 24) & 0xFF;
                        sumR += (pixel >> 16) & 0xFF;
                        sumG += (pixel >> 8) & 0xFF;
                        sumB += pixel & 0xFF;
                    }
                }

                int a = sumA / 9;
                int r = sumR / 9;
                int g = sumG / 9;
                int b = sumB / 9;

                result[outIdx] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return result;
    }
}
