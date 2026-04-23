package com.offloadhpc.mobile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.offloadhpc.mobile.R;
import com.offloadhpc.mobile.model.JobRequest;
import com.offloadhpc.mobile.network.SocketClient;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_imageproc, container, false);

        spinnerImageSize = view.findViewById(R.id.spinnerImageSize);
        spinnerOperation = view.findViewById(R.id.spinnerOperation);
        Button btnSubmit = view.findViewById(R.id.btnSubmitImageProc);

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

        // Generate a random test image (ARGB pixel array)
        List<Integer> pixelData = generateTestImage(width, height);

        Map<String, Object> payload = new HashMap<>();
        payload.put("width", width);
        payload.put("height", height);
        payload.put("operation", operation);
        payload.put("pixelData", pixelData);

        JobRequest request = new JobRequest(jobId, "IMAGE_PROC", payload);

        if (!SocketClient.getInstance().isConnected()) {
            Toast.makeText(requireContext(),
                    "Not connected to Broker. Reconnecting…", Toast.LENGTH_SHORT).show();
            SocketClient.getInstance().connect();
            return;
        }

        SocketClient.getInstance().sendJob(request);

        // Navigate to Progress screen
        Intent intent = new Intent(requireContext(), ProgressActivity.class);
        intent.putExtra("jobId", jobId);
        intent.putExtra("jobType", "IMAGE_PROC");
        startActivity(intent);
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
