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
 * K-Means Clustering tab — lets the user configure cluster count,
 * data points, and iterations. Generates random 2D data and submits
 * a K-Means clustering job to the Broker.
 */
public class KMeansFragment extends Fragment {

    private Spinner spinnerK;
    private Spinner spinnerN;
    private Spinner spinnerIterations;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_kmeans, container, false);

        spinnerK = view.findViewById(R.id.spinnerK);
        spinnerN = view.findViewById(R.id.spinnerN);
        spinnerIterations = view.findViewById(R.id.spinnerIterations);
        Button btnSubmit = view.findViewById(R.id.btnSubmitKMeans);

        // K spinner: 2, 3, 4, 5
        ArrayAdapter<String> kAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[] { "2", "3", "4", "5" });
        kAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerK.setAdapter(kAdapter);

        // N spinner: 50, 100, 200, 500
        ArrayAdapter<String> nAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[] { "50", "100", "200", "500" });
        nAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerN.setAdapter(nAdapter);
        spinnerN.setSelection(1); // default 100

        // Iterations spinner: 5, 10, 20, 50
        ArrayAdapter<String> iterAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[] { "5", "10", "20", "50" });
        iterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerIterations.setAdapter(iterAdapter);
        spinnerIterations.setSelection(1); // default 10

        btnSubmit.setOnClickListener(v -> submitKMeansJob());

        return view;
    }

    private void submitKMeansJob() {
        int K = Integer.parseInt(spinnerK.getSelectedItem().toString());
        int N = Integer.parseInt(spinnerN.getSelectedItem().toString());
        int iterations = Integer.parseInt(spinnerIterations.getSelectedItem().toString());
        String jobId = UUID.randomUUID().toString();

        // Generate random 2D data points with K natural clusters
        List<List<Double>> dataPoints = generateClusteredData(N, K);

        Map<String, Object> payload = new HashMap<>();
        payload.put("K", K);
        payload.put("iterations", iterations);
        payload.put("dataPoints", dataPoints);

        JobRequest request = new JobRequest(jobId, "KMEANS", payload);

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
        intent.putExtra("jobType", "KMEANS");
        startActivity(intent);
    }

    /**
     * Generate N random 2D data points arranged in K natural clusters.
     * Each cluster is centered at a random position with Gaussian noise.
     */
    private List<List<Double>> generateClusteredData(int N, int K) {
        List<List<Double>> points = new ArrayList<>();
        Random random = new Random(42);

        // Generate cluster centers
        double[][] centers = new double[K][2];
        for (int k = 0; k < K; k++) {
            centers[k][0] = random.nextDouble() * 100.0; // x in [0, 100]
            centers[k][1] = random.nextDouble() * 100.0; // y in [0, 100]
        }

        // Generate points around each center
        int pointsPerCluster = N / K;
        for (int k = 0; k < K; k++) {
            int count = (k == K - 1) ? (N - points.size()) : pointsPerCluster;
            for (int i = 0; i < count; i++) {
                List<Double> point = new ArrayList<>();
                point.add(centers[k][0] + random.nextGaussian() * 5.0);
                point.add(centers[k][1] + random.nextGaussian() * 5.0);
                points.add(point);
            }
        }

        return points;
    }
}
