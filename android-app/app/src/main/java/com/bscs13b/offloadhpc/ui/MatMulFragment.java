package com.bscs13b.offloadhpc.ui;

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

import com.bscs13b.offloadhpc.R;
import com.bscs13b.offloadhpc.model.JobRequest;
import com.bscs13b.offloadhpc.network.SocketClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MatMul tab — lets the user pick a matrix size and submit
 * a matrix multiplication job to the Broker.
 */
public class MatMulFragment extends Fragment {

    private Spinner spinnerSize;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_matmul, container, false);

        spinnerSize = view.findViewById(R.id.spinnerMatrixSize);
        Button btnSubmit = view.findViewById(R.id.btnSubmitMatMul);

        // Populate spinner: 128, 256, 512
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[] { "128", "256", "512" });
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSize.setAdapter(adapter);
        spinnerSize.setSelection(1); // default 256

        btnSubmit.setOnClickListener(v -> submitMatMulJob());

        return view;
    }

    private void submitMatMulJob() {
        int size = Integer.parseInt(spinnerSize.getSelectedItem().toString());
        String jobId = UUID.randomUUID().toString();

        // Generate random matrices
        List<List<Double>> matrixA = generateRandomMatrix(size);
        List<List<Double>> matrixB = generateRandomMatrix(size);

        Map<String, Object> payload = new HashMap<>();
        payload.put("matrixSize", size);
        payload.put("matrixA", matrixA);
        payload.put("matrixB", matrixB);

        JobRequest request = new JobRequest(jobId, "MATMUL", payload);

        if (!SocketClient.getInstance().isConnected()) {
            Toast.makeText(requireContext(),
                    "Not connected to Grid. Use the Reconnect button in the toolbar.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        SocketClient.getInstance().sendJob(request);

        // Navigate to Progress screen
        Intent intent = new Intent(requireContext(), ProgressActivity.class);
        intent.putExtra("jobId", jobId);
        intent.putExtra("jobType", "MATMUL");
        startActivity(intent);
    }

    /**
     * Generates a random n×n matrix as a List of Lists (for JSON serialisation).
     */
    private List<List<Double>> generateRandomMatrix(int n) {
        List<List<Double>> matrix = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Double> row = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                row.add(Math.random() * 10.0);
            }
            matrix.add(row);
        }
        return matrix;
    }
}

