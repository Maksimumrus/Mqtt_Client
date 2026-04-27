package com.example.mqttclient;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.example.mqttclient.Accessory.MqttPrefsManager;
import com.example.mqttclient.Accessory.MqttService;
import com.example.mqttclient.Accessory.TopicRepository;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private static MqttService mqttService;
    private static boolean bound = false;

    public interface MqttServiceConsumer {
        void onMqttServiceReady(MqttService service);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MqttService.LocalBinder binder = (MqttService.LocalBinder) service;
            mqttService = binder.getService();
            bound = true;

            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (fragment instanceof MqttServiceConsumer) {
                ((MqttServiceConsumer) fragment).onMqttServiceReady(mqttService);
            }
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

        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_all_topics) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new AllTopicsFragment()).commit();
                return true;
            } else if (item.getItemId() == R.id.nav_my_topics) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new SubscribedTopicsFragment()).commit();
                return true;
            }
            return false;
        });
        nav.setSelectedItemId(R.id.nav_all_topics);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        MqttPrefsManager.cleanInvalidTopics(this, MqttPrefsManager.getBrokerUrl(this));

        Intent intent = new Intent(this, MqttService.class);
        intent.setAction(MqttService.ACTION_CONNECT);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (bound) unbindService(connection);
        super.onDestroy();
    }

    public static MqttService getMqttService() { return mqttService; }
    public static boolean isMqttServiceBound() { return bound; }
}