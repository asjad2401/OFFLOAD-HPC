package com.offloadhpc.mobile.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.offloadhpc.mobile.R;
import com.offloadhpc.mobile.network.SocketClient;

/**
 * Main entry-point Activity.
 * Hosts a TabLayout + ViewPager2 with two tabs: MatMul and Hash Crack.
 * Initiates the TCP connection to the Broker on launch.
 */
public class MainActivity extends AppCompatActivity {

    private static final String[] TAB_TITLES = { "MatMul", "Hash Crack" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ── ViewPager + Tabs ────────────────────────────────────────
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(TAB_TITLES[position])).attach();

        // ── Connect to Broker ───────────────────────────────────────
        SocketClient client = SocketClient.getInstance();
        client.setConnectionCallback(new SocketClient.ConnectionCallback() {
            @Override
            public void onConnected() {
                Toast.makeText(MainActivity.this,
                        "Connected to Broker", Toast.LENGTH_SHORT).show();
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
    protected void onDestroy() {
        super.onDestroy();
        SocketClient.getInstance().disconnect();
    }
}
