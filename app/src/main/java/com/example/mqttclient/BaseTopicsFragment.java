package com.example.mqttclient;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.mqttclient.Accessory.MqttPrefsManager;
import com.example.mqttclient.Accessory.MqttService;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Accessory.UiUtils;
import com.google.android.material.textfield.TextInputLayout;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Абстрактный базовый фрагмент для отображения списков топиков (Все топики / Мои подписки).
 * Содержит общую логику:
 * - выбора/добавления/удаления серверов
 * - отображения статуса подключения
 * - работы с MQTT сервисом
 */
public abstract class BaseTopicsFragment extends Fragment implements MainActivity.MqttServiceConsumer {

    protected MqttService mqttService;
    protected AutoCompleteTextView serverSpinner;
    protected List<String> serverList = new ArrayList<>();
    protected String pendingServerSwitch = null;
    protected Button btnAddServer;
    protected Button btnReconnect;
    protected TextView statusView;
    protected View ledIndicator;
    protected ArrayAdapter<String> serverAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutResourceId(), container, false);
        initCommonViews(view);
        setupServerSpinner();
        return view;
    }

    /**
     * Возвращает идентификатор layout-файла для фрагмента.
     * Дочерние классы должны переопределить.
     */
    protected abstract int getLayoutResourceId();

    /**
     * Инициализирует общие View (спиннер сервера, кнопки, статус).
     */
    private void initCommonViews(View view) {
        serverSpinner = view.findViewById(R.id.spinner_server);
        btnAddServer = view.findViewById(R.id.btn_add_server);
        btnReconnect = view.findViewById(R.id.btn_reconnect);
        statusView = view.findViewById(R.id.connection_status);
        ledIndicator = view.findViewById(R.id.led_indicator);

        btnAddServer.setOnClickListener(v -> showServerDialog(null, null, null));
        btnReconnect.setOnClickListener(v -> {
            if (mqttService != null) {
                mqttService.changeBrokerUrl(MqttPrefsManager.getBrokerUrl(getContext()));
            } else {
                UiUtils.showToast(getContext(), "Сервис не готов");
            }
        });
    }

    /**
     * Настраивает спиннер для выбора сервера (отображает короткие имена).
     */
    protected void setupServerSpinner() {
        List<String> fullServers = MqttPrefsManager.getServerList(getContext());
        List<String> displayServers = new ArrayList<>();
        for (String url : fullServers) {
            displayServers.add(MqttPrefsManager.displayUrl(url));
        }
        serverAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, displayServers);
        serverSpinner.setAdapter(serverAdapter);

        String currentFull = MqttPrefsManager.getBrokerUrl(getContext());
        String currentDisplay = MqttPrefsManager.displayUrl(currentFull);
        serverSpinner.setText(currentDisplay, false);
        serverSpinner.setThreshold(1);

        serverSpinner.setOnClickListener(v -> serverSpinner.showDropDown());
        serverSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String selectedDisplay = displayServers.get(position);
            String selectedFull = MqttPrefsManager.fullUrl(selectedDisplay);
            String currentFullUrl = MqttPrefsManager.getBrokerUrl(getContext());
            if (!selectedFull.equals(currentFullUrl)) {
                applyServerChange(selectedFull);
            }
        });

        serverSpinner.setOnLongClickListener(v -> {
            showServerManagerDialog();
            return true;
        });
    }

    /**
     * Применяет смену сервера: сохраняет в настройках, обновляет репозиторий и MQTT сервис.
     * @param newFullUrl полный URL нового сервера (с префиксом tcp://)
     */

    protected void applyServerChange(String newFullUrl) {
        String current = MqttPrefsManager.getBrokerUrl(getContext());
        if (!newFullUrl.equals(current)) {
            MqttPrefsManager.saveBrokerUrl(getContext(), newFullUrl);
            setupServerSpinner();
            onServerChanged(newFullUrl);

            if (mqttService != null) {
                mqttService.changeBrokerUrl(newFullUrl);
                // Принудительно запросить текущий статус
                mqttService.getCurrentStatus();
                pendingServerSwitch = null;
            } else {
                pendingServerSwitch = newFullUrl;
            }
        }
    }

//    protected void applyServerChange(String newFullUrl) {
//        String current = MqttPrefsManager.getBrokerUrl(getContext());
//        if (!newFullUrl.equals(current)) {
//            MqttPrefsManager.saveBrokerUrl(getContext(), newFullUrl);
//            setupServerSpinner();                 // обновляем спиннер
//            onServerChanged(newFullUrl);          // уведомляем дочерний фрагмент
//
//            if (mqttService != null) {
//                mqttService.changeBrokerUrl(newFullUrl);
//                pendingServerSwitch = null;
//            } else {
//                pendingServerSwitch = newFullUrl; // отложенное переключение
//            }
//
//            refreshList();                        // принудительное обновление списка
//        }
//    }

    /**
     * Уведомляет дочерний фрагмент о смене сервера (чтобы обновить ViewModel и т.п.).
     */
    protected abstract void onServerChanged(String newFullUrl);

    /**
     * Диалог добавления/редактирования сервера.
     * @param oldServerUrl полный URL редактируемого сервера (null для добавления)
     * @param oldUsername логин (может быть null)
     * @param oldPassword пароль (может быть null)
     */
    protected void showServerDialog(@Nullable String oldServerUrl, @Nullable String oldUsername, @Nullable String oldPassword) {
        AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext(), R.style.ThemeOverlay_MQTTClient_AlertDialog);
        builder.setTitle(oldServerUrl == null ? "Добавить MQTT сервер" : "Редактировать сервер");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // Поле "Хост"
        TextInputLayout hostLayout = new TextInputLayout(getContext());
        hostLayout.setHint("Хост (например, broker.emqx.io)");
        EditText inputHost = new EditText(getContext());
        inputHost.setInputType(InputType.TYPE_CLASS_TEXT);
        hostLayout.addView(inputHost);
        layout.addView(hostLayout);

        // Поле "Порт"
        TextInputLayout portLayout = new TextInputLayout(getContext());
        portLayout.setHint("Порт (1883 по умолчанию)");
        EditText inputPort = new EditText(getContext());
        inputPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        portLayout.addView(inputPort);
        layout.addView(portLayout);

        // Логин
        TextInputLayout userLayout = new TextInputLayout(getContext());
        userLayout.setHint("Логин (опционально)");
        EditText inputUser = new EditText(getContext());
        userLayout.addView(inputUser);
        layout.addView(userLayout);

        // Пароль
        TextInputLayout passLayout = new TextInputLayout(getContext());
        passLayout.setHint("Пароль (опционально)");
        EditText inputPass = new EditText(getContext());
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
                UiUtils.showToast(getContext(), "Введите хост");
                return;
            }
            int port = 1883;
            if (!portStr.isEmpty()) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    UiUtils.showToast(getContext(), "Неверный порт");
                    return;
                }
            }
            String newServerUrl = "tcp://" + host + ":" + port;
            MqttPrefsManager.addServer(getContext(), newServerUrl);
            if (!user.isEmpty() || !pass.isEmpty()) {
                MqttPrefsManager.saveServerCredentials(getContext(), newServerUrl, user, pass);
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

                MqttPrefsManager.removeServerData(getContext(), oldServerUrl);
                TopicRepository.getInstance(requireActivity().getApplication())
                        .deleteAllDataForServer(oldServerUrl);
                TopicRepository.getInstance(requireActivity().getApplication())
                        .clearCacheForServer(oldServerUrl);
                setupServerSpinner();
                String current = MqttPrefsManager.getBrokerUrl(getContext());
                mqttService.changeBrokerUrl(current);
                applyServerChange(current);
                if (mqttService != null) mqttService.getCurrentStatus();
            });
        }

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    /**
     * Диалог управления серверами (отображает список и позволяет добавить/редактировать).
     */
    protected void showServerManagerDialog() {
        List<String> fullServers = MqttPrefsManager.getServerList(getContext());
        List<String> displayServers = new ArrayList<>();
        for (String url : fullServers) {
            displayServers.add(MqttPrefsManager.displayUrl(url));
        }
        String[] items = displayServers.toArray(new String[0]);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext(), R.style.ThemeOverlay_MQTTClient_AlertDialog)
                .setTitle("Управление серверами")
                .setItems(items, (dialog, which) -> {
                    String selectedDisplay = items[which];
                    String selectedFull = MqttPrefsManager.fullUrl(selectedDisplay);
                    String username = MqttPrefsManager.getUsernameForServer(getContext(), selectedFull);
                    String password = MqttPrefsManager.getPasswordForServer(getContext(), selectedFull);
                    showServerDialog(selectedFull, username, password);
                })
                .setPositiveButton("Добавить", (d, w) -> showServerDialog(null, null, null))
                .setNegativeButton("Отмена", null)
                .show();
    }

    @Override
    public void onMqttServiceReady(MqttService service) {
        this.mqttService = service;

        service.setConnectionStatusListener((status, isConnected) -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                String shortUrl = MqttPrefsManager.displayUrl(MqttPrefsManager.getBrokerUrl(getContext()));
                String displayStatus = isConnected ? "Подключено к " + shortUrl : "Отключено";
                if (statusView != null) statusView.setText(displayStatus);
                if (ledIndicator != null) {
                    ledIndicator.setBackgroundResource(isConnected ? R.drawable.circle_green : R.drawable.circle_red);
                }
            });
        });

        service.setConnectionErrorListener((errorMessage, failedUrl) -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                String lowerMsg = errorMessage.toLowerCase();
                // Показываем диалог только при ошибках аутентификации, неверного хоста/порта
                if (lowerMsg.contains("authentication") || lowerMsg.contains("credentials") ||
                        lowerMsg.contains("not authorized") || lowerMsg.contains("connection refused") ||
                        lowerMsg.contains("unknown host")) {
                    String username = MqttPrefsManager.getUsernameForServer(getContext(), failedUrl);
                    String password = MqttPrefsManager.getPasswordForServer(getContext(), failedUrl);
                    showServerDialog(failedUrl, username, password);
                } else {
                    UiUtils.showError(getContext(), "Ошибка подключения: " + errorMessage);
                }
            });
        });

        if (pendingServerSwitch != null) {
            service.changeBrokerUrl(pendingServerSwitch);
            pendingServerSwitch = null;
        }

        onMqttServiceReadyExtended(service);
    }

    /**
     * Дочерний фрагмент переопределяет для добавления кастомных слушателей сообщений и т.п.
     */
    protected abstract void onMqttServiceReadyExtended(MqttService service);

    /**
     * Обновляет список (должен быть реализован в дочернем).
     */
    protected abstract void refreshList();

    /**
     * Применяет фильтры (поиск, статус) – дочерний фрагмент определяет свою логику.
     */
    protected abstract void applyFilters();

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity && MainActivity.isMqttServiceBound()) {
            MqttService service = MainActivity.getMqttService();
            if (service != null) {
                onMqttServiceReady(service);
                refreshList();
            }
        }
        setupServerSpinner();
        refreshList();
    }
}