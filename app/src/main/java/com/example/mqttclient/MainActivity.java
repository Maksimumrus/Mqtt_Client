package com.example.mqttclient;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mqttclient.Accessory.MqttPrefsManager;
import com.example.mqttclient.Accessory.MqttService;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Accessory.UiUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static MqttService mqttService;
    private static boolean bound = false;

    private AutoCompleteTextView serverSpinner;
    private MaterialButton btnAddServer, btnReconnect;
    private View ledIndicator;
    private TextView statusText;

    private List<String> serverList = new ArrayList<>();
    private ArrayAdapter<String> serverAdapter;

    private AllTopicsFragment allTopicsFragment;
    private SubscribedTopicsFragment subscribedTopicsFragment;

    public interface MqttServiceConsumer {
        void onMqttServiceReady(MqttService service);
    }

    private ServiceConnection connection = new ServiceConnection() {
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

            mqttService.setConnectionErrorListener((errorMessage, failedUrl) -> {
                runOnUiThread(() -> UiUtils.showError(MainActivity.this, "Ошибка: " + errorMessage));
            });

            // Передать сервис фрагментам, если они уже созданы
            if (allTopicsFragment != null && allTopicsFragment.isAdded()) {
                allTopicsFragment.onMqttServiceReady(mqttService);
            }
            if (subscribedTopicsFragment != null && subscribedTopicsFragment.isAdded()) {
                subscribedTopicsFragment.onMqttServiceReady(mqttService);
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

        serverSpinner = findViewById(R.id.spinner_server);
        btnAddServer = findViewById(R.id.btn_add_server);
        btnReconnect = findViewById(R.id.btn_reconnect);
        ledIndicator = findViewById(R.id.led_indicator);
        statusText = findViewById(R.id.connection_status);

        setupServerSpinner();

        btnAddServer.setOnClickListener(v -> showServerDialog(null, null, null));
        btnReconnect.setOnClickListener(v -> {
            if (mqttService != null) {
                mqttService.changeBrokerUrl(MqttPrefsManager.getBrokerUrl(this));
            } else {
                UiUtils.showToast(this, "Сервис не готов");
            }
        });

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

        allTopicsFragment = new AllTopicsFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, allTopicsFragment)
                .commit();

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

    protected void setupServerSpinner() {
        List<String> fullServers = MqttPrefsManager.getServerList(this);
        serverList.clear();
        serverList.addAll(fullServers);
        List<String> displayServers = new ArrayList<>();
        for (String url : fullServers) {
            displayServers.add(MqttPrefsManager.displayUrl(url));
        }
        serverAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, displayServers);
        serverSpinner.setAdapter(serverAdapter);

        String currentFull = MqttPrefsManager.getBrokerUrl(this);
        String currentDisplay = MqttPrefsManager.displayUrl(currentFull);
        serverSpinner.setText(currentDisplay, false);
        serverSpinner.setThreshold(1);

        serverSpinner.setOnClickListener(v -> serverSpinner.showDropDown());
        serverSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String selectedDisplay = displayServers.get(position);
            String selectedFull = MqttPrefsManager.fullUrl(selectedDisplay);
            String currentFullUrl = MqttPrefsManager.getBrokerUrl(this);
            if (!selectedFull.equals(currentFullUrl)) {
                applyServerChange(selectedFull);
            }
        });

        serverSpinner.setOnLongClickListener(v -> {
            showServerManagerDialog();
            return true;
        });
    }

    protected void applyServerChange(String newFullUrl) {
        String oldUrl = MqttPrefsManager.getBrokerUrl(this);
        if (newFullUrl.equals(oldUrl)) return;

        MqttPrefsManager.saveBrokerUrl(this, newFullUrl);
        setupServerSpinner();

        if (mqttService != null) {
            mqttService.changeBrokerUrl(newFullUrl);
        }

        // Обновить репозиторий
        TopicRepository.getInstance(getApplication()).refreshAllDataForCurrentServer();

        // Уведомить оба фрагмента
        notifyFragmentsServerChanged(newFullUrl);
    }

    private void notifyFragmentsServerChanged(String newFullUrl) {
        if (allTopicsFragment != null && allTopicsFragment.isAdded()) {
            allTopicsFragment.onServerChanged(newFullUrl);
            allTopicsFragment.refreshList();
        }
        if (subscribedTopicsFragment != null && subscribedTopicsFragment.isAdded()) {
            subscribedTopicsFragment.onServerChanged(newFullUrl);
            subscribedTopicsFragment.refreshList();
        }
    }

    protected void showServerDialog(@Nullable String oldServerUrl, @Nullable String oldUsername, @Nullable String oldPassword) {
        AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MQTTClient_AlertDialog);
        builder.setTitle(oldServerUrl == null ? "Добавить MQTT сервер" : "Редактировать сервер");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // Поле "Хост"
        TextInputLayout hostLayout = new TextInputLayout(this);
        hostLayout.setHint("Хост (например, broker.emqx.io)");
        EditText inputHost = new EditText(this);
        inputHost.setInputType(InputType.TYPE_CLASS_TEXT);
        hostLayout.addView(inputHost);
        layout.addView(hostLayout);

        // Поле "Порт"
        TextInputLayout portLayout = new TextInputLayout(this);
        portLayout.setHint("Порт (1883 по умолчанию)");
        EditText inputPort = new EditText(this);
        inputPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        portLayout.addView(inputPort);
        layout.addView(portLayout);

        // Логин
        TextInputLayout userLayout = new TextInputLayout(this);
        userLayout.setHint("Логин (опционально)");
        EditText inputUser = new EditText(this);
        userLayout.addView(inputUser);
        layout.addView(userLayout);

        // Пароль
        TextInputLayout passLayout = new TextInputLayout(this);
        passLayout.setHint("Пароль (опционально)");
        EditText inputPass = new EditText(this);
        inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passLayout.addView(inputPass);
        layout.addView(passLayout);

        // Заполняем поля при редактировании
        if (oldServerUrl != null) {
            try {
                URI uri = new URI(oldServerUrl);
                String host = uri.getHost();
                int port = uri.getPort();
                inputHost.setText(host != null ? host : MqttPrefsManager.displayUrl(oldServerUrl));
                if (port != -1) inputPort.setText(String.valueOf(port));
            } catch (URISyntaxException ignored) {}
            if (oldUsername != null) inputUser.setText(oldUsername);
            if (oldPassword != null) inputPass.setText(oldPassword);
        }

        builder.setView(layout);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String host = inputHost.getText().toString().trim();
            String portStr = inputPort.getText().toString().trim();
            String user = inputUser.getText().toString().trim();
            String pass = inputPass.getText().toString().trim();

            if (host.isEmpty()) {
                UiUtils.showToast(this, "Введите хост");
                return;
            }
            int port = 1883;
            if (!portStr.isEmpty()) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    UiUtils.showToast(this, "Неверный порт");
                    return;
                }
            }
            String newServerUrl = "tcp://" + host + ":" + port;
            MqttPrefsManager.addServer(this, newServerUrl);
            if (!user.isEmpty() || !pass.isEmpty()) {
                MqttPrefsManager.saveServerCredentials(this, newServerUrl, user, pass);
            }
            // Переключаемся на новый сервер
            applyServerChange(newServerUrl);
        });

        // Кнопка "Удалить" – только для редактирования существующего сервера
        if (oldServerUrl != null) {
            builder.setNeutralButton("Удалить", (d, w) -> {
                if (mqttService != null) {
                    mqttService.disconnectNow();
                }

                MqttPrefsManager.removeServerData(this, oldServerUrl);
                TopicRepository.getInstance(this.getApplication())
                        .deleteAllDataForServer(oldServerUrl);
                TopicRepository.getInstance(this.getApplication())
                        .clearCacheForServer(oldServerUrl);
                setupServerSpinner();
                String current = MqttPrefsManager.getBrokerUrl(this);
                mqttService.changeBrokerUrl(current);
                applyServerChange(current);
                if (mqttService != null) mqttService.getCurrentStatus();
            });
        }

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    protected void showServerManagerDialog() {
        List<String> fullServers = MqttPrefsManager.getServerList(this);
        List<String> displayServers = new ArrayList<>();
        for (String url : fullServers) {
            displayServers.add(MqttPrefsManager.displayUrl(url));
        }
        String[] items = displayServers.toArray(new String[0]);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MQTTClient_AlertDialog)
                .setTitle("Управление серверами")
                .setItems(items, (dialog, which) -> {
                    String selectedDisplay = items[which];
                    String selectedFull = MqttPrefsManager.fullUrl(selectedDisplay);
                    String username = MqttPrefsManager.getUsernameForServer(this, selectedFull);
                    String password = MqttPrefsManager.getPasswordForServer(this, selectedFull);
                    showServerDialog(selectedFull, username, password);
                })
                .setPositiveButton("Добавить", (d, w) -> showServerDialog(null, null, null))
                .setNegativeButton("Отмена", null)
                .show();
    }

    public static MqttService getMqttService() { return mqttService; }
    public static boolean isMqttServiceBound() { return bound; }
}