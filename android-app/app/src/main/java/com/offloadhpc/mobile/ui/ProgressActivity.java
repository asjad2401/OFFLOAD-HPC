package com.offloadhpc.mobile.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.graphics.Bitmap;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.offloadhpc.mobile.R;
import com.offloadhpc.mobile.compute.LocalCompute;
import com.offloadhpc.mobile.ui.ImageProcFragment;
import com.offloadhpc.mobile.network.MessageListener;
import com.offloadhpc.mobile.network.SocketClient;

import java.util.List;

/**
 * Displays real-time progress of a submitted job and shows the
 * final result when the Broker returns JOB_RESULT.
 *
 * All UI updates are dispatched on the main thread by SocketClient.
 */
public class ProgressActivity extends AppCompatActivity implements MessageListener {

    private String jobId;
    private String jobType;

    private TextView tvJobId;
    private TextView tvJobType;
    private TextView tvProgressText;
    private ProgressBar progressBar;
    private TextView tvResult;
    private ScrollView scrollResult;
    private ImageView ivResultImage;

    private long startTime;
    private Double localProcessingSeconds = null;
    private String gridResultText = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        // ── Retrieve extras ─────────────────────────────────────────
        jobId = getIntent().getStringExtra("jobId");
        jobType = getIntent().getStringExtra("jobType");

        // ── Bind views ──────────────────────────────────────────────
        tvJobId = findViewById(R.id.tvJobId);
        tvJobType = findViewById(R.id.tvJobType);
        tvProgressText = findViewById(R.id.tvProgressText);
        progressBar = findViewById(R.id.progressBar);
        tvResult = findViewById(R.id.tvResult);
        scrollResult = findViewById(R.id.scrollResult);
        ivResultImage = findViewById(R.id.ivResultImage);

        startTime = System.currentTimeMillis();

        tvJobId.setText("Job ID: " + jobId);
        String jobTypeLabel;
        switch (jobType) {
            case "MATMUL":
                jobTypeLabel = "Matrix Multiplication";
                break;
            case "HASH_CRACK":
                jobTypeLabel = "Hash Crack";
                break;
            case "IMAGE_PROC":
                jobTypeLabel = "Image Processing";
                break;
            case "KMEANS":
                jobTypeLabel = "K-Means Clustering";
                break;
            default:
                jobTypeLabel = jobType;
        }
        tvJobType.setText("Job Type: " + jobTypeLabel);
        tvProgressText.setText("Uploading task to Grid...");

        // ── Register for incoming messages ──────────────────────────
        SocketClient.getInstance().setMessageListener(this);

        // ── Start simulated local compute for comparison ─────────────
        if ("IMAGE_PROC".equals(jobType)) {
            final List<Integer> pixels = ImageProcFragment.currentPixelData;
            final String operation = ImageProcFragment.currentOperation;
            final int width = getIntent().getIntExtra("imageWidth", 32);
            final int height = getIntent().getIntExtra("imageHeight", 32);
            if (pixels != null && operation != null) {
                new Thread(() -> {
                    double time = LocalCompute.runLocalImageProc(pixels, width, height, operation);
                    runOnUiThread(() -> {
                        localProcessingSeconds = time;
                        updateImageProcUIAfterBoth();
                    });
                }).start();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't null the listener if we're just rotating; only on real finish
        if (isFinishing()) {
            SocketClient.getInstance().setMessageListener(null);
        }
    }

    // ── MessageListener callbacks (called on main thread) ───────────

    @Override
    public void onJobAck(String ackJobId, int totalSubTasks, String status) {
        if (!ackJobId.equals(jobId))
            return;

        progressBar.setMax(totalSubTasks);
        progressBar.setProgress(0);
        tvProgressText.setText("0 of " + totalSubTasks + " sub-tasks complete");
    }

    @Override
    public void onProgressUpdate(String progressJobId, int completedSubTasks, int totalSubTasks) {
        if (!progressJobId.equals(jobId))
            return;

        progressBar.setMax(totalSubTasks);
        progressBar.setProgress(completedSubTasks);
        tvProgressText.setText(completedSubTasks + " of " + totalSubTasks + " sub-tasks complete");
    }

    @Override
    public void onJobResult(String resultJobId, String status, String resultJson) {
        if (!resultJobId.equals(jobId))
            return;

        // Hide progress, show result
        progressBar.setVisibility(View.GONE);
        tvProgressText.setText("Job completed — " + status);
        scrollResult.setVisibility(View.VISIBLE);
        ivResultImage.setVisibility(View.GONE);

        switch (jobType) {
            case "MATMUL":
                displayMatMulResult(resultJson);
                break;
            case "HASH_CRACK":
                displayHashCrackResult(resultJson);
                break;
            case "IMAGE_PROC":
                displayImageProcResult(resultJson);
                break;
            case "KMEANS":
                displayKMeansResult(resultJson);
                break;
            default:
                tvResult.setText("Result: " + resultJson);
        }
    }

    @Override
    public void onConnectionError(String errorMessage) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
    }

    // ── Result display helpers ──────────────────────────────────────

    private void displayMatMulResult(String resultJson) {
        try {
            JsonObject obj = JsonParser.parseString(resultJson).getAsJsonObject();
            JsonArray matrixC = obj.getAsJsonArray("matrixC");

            int rows = matrixC.size();
            int cols = matrixC.get(0).getAsJsonArray().size();

            double elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0;

            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDDA5 Total Processing Time: ").append(String.format("%.2f", elapsedSeconds))
                    .append("s\n\n");
            sb.append("Result Matrix C: ").append(rows).append(" × ").append(cols).append("\n\n");
            sb.append("Top-left 3×3 corner:\n");

            int previewRows = Math.min(3, rows);
            int previewCols = Math.min(3, cols);

            for (int i = 0; i < previewRows; i++) {
                JsonArray row = matrixC.get(i).getAsJsonArray();
                for (int j = 0; j < previewCols; j++) {
                    sb.append(String.format("%10.2f", row.get(j).getAsDouble()));
                    if (j < previewCols - 1)
                        sb.append("  ");
                }
                sb.append("\n");
            }

            tvResult.setText(sb.toString());
        } catch (Exception e) {
            tvResult.setText("MatMul result received but could not parse:\n" + resultJson);
        }
    }

    private void displayHashCrackResult(String resultJson) {
        try {
            JsonObject obj = JsonParser.parseString(resultJson).getAsJsonObject();
            double elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
            String timeStr = "\n\n\uD83D\uDDA5 Total Processing Time: " + String.format("%.2f", elapsedSeconds) + "s";

            if (obj.has("crackedValue") && !obj.get("crackedValue").isJsonNull()) {
                String cracked = obj.get("crackedValue").getAsString();
                String algorithm = obj.has("algorithm") ? obj.get("algorithm").getAsString() : "Unknown";
                tvResult.setText("🔓 Cracked!\n\nValue: " + cracked + "\nAlgorithm: " + algorithm + timeStr);
            } else {
                tvResult.setText("❌ Not Found\n\nThe password was not found within the search space." + timeStr);
            }
        } catch (Exception e) {
            tvResult.setText("Hash Crack result received but could not parse:\n" + resultJson);
        }
    }

    private void displayImageProcResult(String resultJson) {
        tvResult.setText("Receiving processed image from Grid... Please wait.");
        new Thread(() -> {
            try {
                JsonObject obj = JsonParser.parseString(resultJson).getAsJsonObject();
                int width = obj.get("width").getAsInt();
                int height = obj.get("height").getAsInt();
                JsonArray pixels = obj.getAsJsonArray("processedPixels");

                int[] pixelArray = new int[pixels.size()];
                for (int i = 0; i < pixels.size(); i++) {
                    pixelArray[i] = pixels.get(i).getAsInt();
                }
                Bitmap bmp = Bitmap.createBitmap(pixelArray, width, height, Bitmap.Config.ARGB_8888);

                runOnUiThread(() -> {
                    double elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
                    ivResultImage.setImageBitmap(bmp);
                    ivResultImage.setVisibility(View.VISIBLE);

                    StringBuilder sb = new StringBuilder();
                    sb.append("\uD83D\uDDBC Image Processing Complete!\n\n");
                    sb.append("\uD83D\uDDA5 Total Grid Assembly Time: ").append(String.format("%.2f", elapsedSeconds))
                            .append("s\n\n");
                    sb.append("Dimensions: ").append(width).append(" × ").append(height).append("\n");

                    gridResultText = sb.toString();
                    updateImageProcUIAfterBoth();
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvResult
                        .setText("Image processing result received but could not parse:\n" + e.getMessage()));
            }
        }).start();
    }

    private void updateImageProcUIAfterBoth() {
        if (gridResultText == null) {
            // UI will wait until Grid is done to show the final side-by-side
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(gridResultText);

        if (localProcessingSeconds != null) {
            sb.append("\n📱 Local Mobile Compute Time: ").append(String.format("%.2f", localProcessingSeconds))
                    .append("s\n");

            double gridSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
            if (localProcessingSeconds > gridSeconds) {
                double speedup = localProcessingSeconds / gridSeconds;
                // Don't show extreme speedups for tiny operations, only if it's meaningful
                if (localProcessingSeconds > 0.1) {
                    sb.append("\n⚡ Grid was ").append(String.format("%.1f", speedup)).append("x faster!");
                }
            }
        } else {
            sb.append("\n📱 Local Mobile Compute is still running... (heavy CPU load)\n");
        }
        tvResult.setText(sb.toString());
    }

    private void displayKMeansResult(String resultJson) {
        try {
            JsonObject obj = JsonParser.parseString(resultJson).getAsJsonObject();
            int K = obj.get("K").getAsInt();
            int iterations = obj.get("iterations").getAsInt();
            JsonArray centroids = obj.getAsJsonArray("centroids");

            double elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0;

            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDCCA K-Means Clustering Complete!\n\n");
            sb.append("\uD83D\uDDA5 Total Processing Time: ").append(String.format("%.2f", elapsedSeconds))
                    .append("s\n\n");
            sb.append("Clusters: ").append(K).append("\n");
            sb.append("Iterations: ").append(iterations).append("\n\n");
            sb.append("Final Centroids:\n");

            for (int k = 0; k < centroids.size(); k++) {
                JsonArray centroid = centroids.get(k).getAsJsonArray();
                sb.append("  Cluster ").append(k + 1).append(": (");
                for (int d = 0; d < centroid.size(); d++) {
                    if (d > 0)
                        sb.append(", ");
                    sb.append(String.format("%.2f", centroid.get(d).getAsDouble()));
                }
                sb.append(")\n");
            }

            tvResult.setText(sb.toString());
        } catch (Exception e) {
            tvResult.setText("K-Means result received but could not parse:\n" + resultJson);
        }
    }
}
