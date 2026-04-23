package com.offloadhpc.mobile.ui;

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
import com.offloadhpc.mobile.R;
import com.offloadhpc.mobile.network.SocketClient;

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
                Toast.makeText(MainActivity.this,
                        "Connected to Broker: " + SocketClient.getInstance().getBrokerHost(),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDisconnected(String reason) {
                Toast.makeText(MainActivity.this,
                        "Disconnected: " + reason, Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, R.string.searching_broker, Toast.LENGTH_SHORT).show();
            SocketClient.getInstance().disconnect();
            // In UDP discovery mode, the SocketClient expects brokerHost to be null
            // to trigger a new discovery phase.
            SocketClient.getInstance().setBrokerAddress(null, 9000);
            startBrokerConnection();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SocketClient.getInstance().disconnect();
    }
}
