package com.offloadhpc.mobile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.offloadhpc.mobile.R;
import com.offloadhpc.mobile.model.JobRequest;
import com.offloadhpc.mobile.network.SocketClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hash Crack tab — lets the user enter a target hash,
 * select algorithm and max-length, then submit a hash
 * cracking job to the Broker.
 */
public class HashCrackFragment extends Fragment {

    private EditText editTargetHash;
    private Spinner spinnerAlgorithm;
    private Spinner spinnerMaxLength;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_hashcrack, container, false);

        editTargetHash = view.findViewById(R.id.editTargetHash);
        spinnerAlgorithm = view.findViewById(R.id.spinnerAlgorithm);
        spinnerMaxLength = view.findViewById(R.id.spinnerMaxLength);
        Button btnSubmit = view.findViewById(R.id.btnSubmitHashCrack);

        // Algorithm spinner: MD5, SHA-1
        ArrayAdapter<String> algoAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[] { "MD5", "SHA-1" });
        algoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAlgorithm.setAdapter(algoAdapter);

        // Max length spinner: 3, 4, 5
        ArrayAdapter<String> lenAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[] { "3", "4", "5" });
        lenAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaxLength.setAdapter(lenAdapter);

        btnSubmit.setOnClickListener(v -> submitHashCrackJob());

        return view;
    }

    private void submitHashCrackJob() {
        String targetHash = editTargetHash.getText().toString().trim();
        if (targetHash.isEmpty()) {
            Toast.makeText(requireContext(),
                    "Please enter a target hash", Toast.LENGTH_SHORT).show();
            return;
        }

        String algorithm = spinnerAlgorithm.getSelectedItem().toString();
        int maxLength = Integer.parseInt(spinnerMaxLength.getSelectedItem().toString());
        String jobId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("targetHash", targetHash);
        payload.put("algorithm", algorithm);
        payload.put("charset", "abcdefghijklmnopqrstuvwxyz");
        payload.put("maxLength", maxLength);

        JobRequest request = new JobRequest(jobId, "HASH_CRACK", payload);

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
        intent.putExtra("jobType", "HASH_CRACK");
        startActivity(intent);
    }
}
