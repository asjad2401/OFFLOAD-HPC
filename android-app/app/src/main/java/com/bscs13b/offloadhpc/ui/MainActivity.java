package com.bscs13b.offloadhpc.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.bscs13b.offloadhpc.R;
import com.bscs13b.offloadhpc.network.SocketClient;

/**
 * Main entry-point Activity.
 * Hosts a TabLayout + ViewPager2 with four tabs:
 * MatMul, Hash Crack, Image Proc, and K-Means.
 * v2.0 — initiates TCP connection to the Broker on launch.
 */
public class MainActivity extends AppCompatActivity {

    private static final String[] TAB_TITLES = { "MatMul", "Hash Crack", "Image Proc", "K-Means" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ── Toolbar ──────────────────────────────────────────────────
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // ── ViewPager + Tabs ────────────────────────────────────────
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(TAB_TITLES[position])).attach();

        // ── Connect to Broker ───────────────────────────────────────
        startBrokerConnection();
    }

    private void startBrokerConnection() {
        SocketClient client = SocketClient.getInstance();
        client.setConnectionCallback(new SocketClient.ConnectionCallback() {
            @Override
            public void onConnected() {
                String host = SocketClient.getInstance().getBrokerHost();
                int port = SocketClient.getInstance().getBrokerPort();
                Toast.makeText(MainActivity.this,
                        "Connected to Grid\nBroker: " + host + ":" + port,
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onDisconnected(String reason) {
                Toast.makeText(MainActivity.this,
                        "Disconnected from Grid: " + reason +
                        "\nUse Reconnect to find a new broker.",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this,
                        error, Toast.LENGTH_LONG).show();
            }
        });
        client.connect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_reconnect) {
            if (SocketClient.getInstance().isConnected()) {
                String host = SocketClient.getInstance().getBrokerHost();
                Toast.makeText(this,
                        "Already connected to grid at " + host,
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            Toast.makeText(this, "Searching for Grid Broker...", Toast.LENGTH_SHORT).show();
            // Disconnect and reset discovery state for a fresh scan
            SocketClient.getInstance().disconnect();
            SocketClient.getInstance().setBrokerAddress(null, 9000);
            startBrokerConnection();
            return true;
        } else if (item.getItemId() == R.id.action_privacy) {
            // Replace the URL below with your actual GitHub Gist Raw URL
            String privacyUrl = "https://gist.githubusercontent.com/asjad2401/ccc03211e05dc414e2aa577410a2ae13/raw/b8d3efaeb37089eaeb8d02da152c0a78424255c8/privacy-policy.md";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl));
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Do NOT disconnect here — SocketClient is a singleton that must
        // survive activity lifecycle. Under memory pressure, Android may
        // destroy MainActivity while ProgressActivity is waiting for results.
        // The socket will be cleaned up when the app process exits.
    }
}

