package com.example.mqttclient;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mqttclient.Accessory.MqttPrefsManager;
import com.example.mqttclient.Accessory.MqttService;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {
    private static MqttService mqttService;
    private static boolean bound = false;

    private View ledIndicator;
    private TextView statusText;

    public interface MqttServiceConsumer {
        void onMqttServiceReady(MqttService service);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MqttService.LocalBinder binder = (MqttService.LocalBinder) service;
            mqttService = binder.getService();
            bound = true;

            mqttService.setConnectionStatusListener((status, isConnected) -> {
                runOnUiThread(() -> {
                    statusText.setText(status);
                    ledIndicator.setBackgroundResource(isConnected ? R.drawable.circle_green : R.drawable.circle_red);
                });
            });
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            mqttService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ledIndicator = findViewById(R.id.led_indicator);
        statusText = findViewById(R.id.connection_status);
        MaterialButton btnSettings = findViewById(R.id.btn_settings);

        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new TopicsFragment())
                    .commit();

            MqttPrefsManager.cleanInvalidTopics(this, MqttPrefsManager.getBrokerUrl(this));

            Intent intent = new Intent(this, MqttService.class);
            intent.setAction(MqttService.ACTION_CONNECT);
            startService(intent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MqttService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public static MqttService getMqttService() { return mqttService; }
    public static boolean isMqttServiceBound() { return bound; }
}