package com.bscs13b.offloadhpc.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.InputStream;
import androidx.fragment.app.Fragment;

import com.bscs13b.offloadhpc.R;
import com.bscs13b.offloadhpc.model.JobRequest;
import com.bscs13b.offloadhpc.network.SocketClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Image Processing tab — lets the user select an image size and
 * operation, generates a test image, and submits a processing
 * job to the Broker.
 */
public class ImageProcFragment extends Fragment {

    private Spinner spinnerImageSize;
    private Spinner spinnerOperation;
    private ImageView ivPreview;
    private Bitmap selectedBitmap;

    // Temporary storage for ProgressActivity to run a local speed comparison
    public static List<Integer> currentPixelData;
    public static String currentOperation;

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        InputStream is = requireContext().getContentResolver().openInputStream(uri);
                        selectedBitmap = BitmapFactory.decodeStream(is);
                        ivPreview.setImageBitmap(selectedBitmap);
                        if (is != null)
                            is.close();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_imageproc, container, false);

        spinnerImageSize = view.findViewById(R.id.spinnerImageSize);
        spinnerOperation = view.findViewById(R.id.spinnerOperation);
        Button btnSubmit = view.findViewById(R.id.btnSubmitImageProc);
        Button btnSelectImage = view.findViewById(R.id.btnSelectImage);
        ivPreview = view.findViewById(R.id.ivPreview);

        btnSelectImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // Image size spinner
        ArrayAdapter<String> sizeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[] { "16x16", "32x32", "64x64", "128x128" });
        sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerImageSize.setAdapter(sizeAdapter);
        spinnerImageSize.setSelection(1); // default 32x32

        // Operation spinner
        ArrayAdapter<String> opAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[] { "GRAYSCALE", "EDGE_DETECT", "BLUR" });
        opAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOperation.setAdapter(opAdapter);

        btnSubmit.setOnClickListener(v -> submitImageProcJob());

        return view;
    }

    private void submitImageProcJob() {
        String sizeStr = spinnerImageSize.getSelectedItem().toString();
        String operation = spinnerOperation.getSelectedItem().toString();
        String jobId = UUID.randomUUID().toString();

        // Parse dimension
        int size = Integer.parseInt(sizeStr.split("x")[0]);
        int width = size;
        int height = size;

        List<Integer> pixelData;
        if (selectedBitmap != null) {
            // Downsample large images to prevent OOM.
            // Raw pixels in JSON are ~10 bytes each, so 512x512 = ~2.5MB JSON.
            Bitmap workBitmap = downsampleBitmap(selectedBitmap, 512);
            width = workBitmap.getWidth();
            height = workBitmap.getHeight();
            int[] pixels = new int[width * height];
            workBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            pixelData = new ArrayList<>(pixels.length);
            for (int p : pixels) {
                pixelData.add(p);
            }
            // Free the working copy if it's different from the original
            if (workBitmap != selectedBitmap) {
                workBitmap.recycle();
            }
        } else {
            // Generate a random test image (ARGB pixel array)
            pixelData = generateTestImage(width, height);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("width", width);
        payload.put("height", height);
        payload.put("operation", operation);
        payload.put("pixelData", pixelData);

        JobRequest request = new JobRequest(jobId, "IMAGE_PROC", payload);

        if (!SocketClient.getInstance().isConnected()) {
            Toast.makeText(requireContext(),
                    "Not connected to Grid. Use the Reconnect button in the toolbar.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        SocketClient.getInstance().sendJob(request);

        // Store globally for local speed comparison
        currentPixelData = pixelData;
        currentOperation = operation;

        // Navigate to Progress screen
        Intent intent = new Intent(requireContext(), ProgressActivity.class);
        intent.putExtra("jobId", jobId);
        intent.putExtra("jobType", "IMAGE_PROC");
        intent.putExtra("imageWidth", width);
        intent.putExtra("imageHeight", height);
        startActivity(intent);
    }

    /**
     * Downsample a bitmap so its largest dimension is at most maxDim pixels.
     * Preserves aspect ratio. Returns the original bitmap if already small enough.
     */
    private Bitmap downsampleBitmap(Bitmap original, int maxDim) {
        int w = original.getWidth();
        int h = original.getHeight();
        if (w <= maxDim && h <= maxDim) {
            return original;
        }
        float scale = Math.min((float) maxDim / w, (float) maxDim / h);
        int newW = Math.round(w * scale);
        int newH = Math.round(h * scale);
        return Bitmap.createScaledBitmap(original, newW, newH, true);
    }

    /**
     * Generate a test image with a gradient pattern.
     * Creates visual content that makes processing results visible.
     */
    private List<Integer> generateTestImage(int width, int height) {
        List<Integer> pixels = new ArrayList<>();
        Random random = new Random(42);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Create a gradient with some noise for interesting edge detection
                int r = (int) (255.0 * x / (width - 1));
                int g = (int) (255.0 * y / (height - 1));
                int b = random.nextInt(128);

                // Add a bright rectangle in the center for edge detection testing
                if (x > width / 4 && x < 3 * width / 4 &&
                        y > height / 4 && y < 3 * height / 4) {
                    r = 255;
                    g = 255;
                    b = 255;
                }

                int pixel = (0xFF << 24) | (r << 16) | (g << 8) | b;
                pixels.add(pixel);
            }
        }
        return pixels;
    }
}

