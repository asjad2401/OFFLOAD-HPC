package com.bscs13b.offloadhpc.compute;

import java.util.List;

/**
 * Executes a completely localized version of the Image Processing algorithms
 * to provide a true side-by-side time comparison against the Grid.
 * This runs directly on the mobile device's CPU.
 */
public class LocalCompute {

    public static double runLocalImageProc(List<Integer> pixelList, int width, int height, String operation) {
        long startTime = System.currentTimeMillis();

        // Convert list to int array
        int[] pixels = new int[pixelList.size()];
        for (int i = 0; i < pixelList.size(); i++) {
            pixels[i] = pixelList.get(i);
        }

        int startRow = 0;
        int endRow = height;

        // Execute the exact same math as the Grid
        switch (operation.toUpperCase()) {
            case "GRAYSCALE":
                grayscale(pixels, width, startRow, endRow);
                break;
            case "EDGE_DETECT":
                edgeDetect(pixels, width, height, startRow, endRow);
                break;
            case "BLUR":
                blur(pixels, width, height, startRow, endRow);
                break;
        }

        return (System.currentTimeMillis() - startTime) / 1000.0;
    }

    private static int[] grayscale(int[] pixels, int width, int startRow, int endRow) {
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

    private static int[] edgeDetect(int[] pixels, int width, int height, int startRow, int endRow) {
        int stripRows = endRow - startRow;
        int[] result = new int[stripRows * width];
        int[][] sobelX = { { -1, 0, 1 }, { -2, 0, 2 }, { -1, 0, 1 } };
        int[][] sobelY = { { -1, -2, -1 }, { 0, 0, 0 }, { 1, 2, 1 } };
        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < width; col++) {
                int outIdx = (row - startRow) * width + col;
                if (row == 0 || row == height - 1 || col == 0 || col == width - 1) {
                    result[outIdx] = 0xFF000000;
                    continue;
                }
                double gx = 0, gy = 0;
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int idx = (row + ky) * width + (col + kx);
                        int pixel = pixels[idx];
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

    private static int[] blur(int[] pixels, int width, int height, int startRow, int endRow) {
        int stripRows = endRow - startRow;
        int[] result = new int[stripRows * width];
        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < width; col++) {
                int outIdx = (row - startRow) * width + col;
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

