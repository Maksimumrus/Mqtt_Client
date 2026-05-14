package com.example.mqttclient;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.mqttclient.Accessory.MqttPrefsManager;
import com.example.mqttclient.Accessory.MqttService;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Accessory.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class ServerSettingsFragment extends Fragment {

    private AutoCompleteTextView serverSpinner;
    private TextInputEditText editHost, editPort, editUsername, editPassword;
    private MaterialButton btnAdd, btnUpdate, btnDelete, btnReconnect;
    private TextView currentServerStatus;
    private View ledIndicator;

    private List<String> fullServerList = new ArrayList<>();
    private List<String> displayServerList = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    private MqttService.ConnectionStatusListener statusListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_server_settings, container, false);

        serverSpinner = view.findViewById(R.id.server_spinner);
        editHost = view.findViewById(R.id.edit_host);
        editPort = view.findViewById(R.id.edit_port);
        editUsername = view.findViewById(R.id.edit_username);
        editPassword = view.findViewById(R.id.edit_password);
        btnAdd = view.findViewById(R.id.btn_add_server);
        btnUpdate = view.findViewById(R.id.btn_update_server);
        btnDelete = view.findViewById(R.id.btn_delete_server);
        btnReconnect = view.findViewById(R.id.btn_reconnect);
        currentServerStatus = view.findViewById(R.id.current_server_status);
        ledIndicator = view.findViewById(R.id.led_indicator);

        setupSpinnerListeners();
        updateServerList();

        serverSpinner.setOnItemClickListener((parent, v, position, id) -> {
            String selectedDisplay = displayServerList.get(position);
            String selectedFull = MqttPrefsManager.fullUrl(selectedDisplay);
            fillFieldsFromServer(selectedFull);
        });

        btnAdd.setOnClickListener(v -> addServer());
        btnUpdate.setOnClickListener(v -> updateSelectedServer());
        btnDelete.setOnClickListener(v -> deleteSelectedServer());

        // Переподключение
        btnReconnect.setOnClickListener(v -> {
            MqttService service = MainActivity.getMqttService();
            if (service != null) {
                service.changeBrokerUrl(MqttPrefsManager.getBrokerUrl(requireContext()));
            } else {
                UiUtils.showToast(getContext(), "Сервис не готов");
            }
        });

        // Обновление статуса подключения через слушатель
        MqttService service = MainActivity.getMqttService();
        if (service != null) {
            service.setConnectionStatusListener((status, isConnected) -> {
                requireActivity().runOnUiThread(() -> {
                    ledIndicator.setBackgroundResource(isConnected ? R.drawable.circle_green : R.drawable.circle_red);
                    currentServerStatus.setText(isConnected ? "Сервер: " + MqttPrefsManager.displayUrl(MqttPrefsManager.getBrokerUrl(requireContext())) : "Отключено");
                });
            });
            service.getCurrentStatus();
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateStatusDisplay();
        setupConnectionStatusListener();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        MqttService service = MainActivity.getMqttService();
        if (service != null && statusListener != null) {
            service.setConnectionStatusListener(null);
        }
    }

    private void setupConnectionStatusListener() {
        MqttService service = MainActivity.getMqttService();
        if (service != null) {
            statusListener = (status, isConnected) -> {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        ledIndicator.setBackgroundResource(isConnected ? R.drawable.circle_green : R.drawable.circle_red);
                        currentServerStatus.setText(isConnected ? "Сервер: " + MqttPrefsManager.displayUrl(MqttPrefsManager.getBrokerUrl(requireContext())) : "Отключено");
                    });
                }
            };
            service.setConnectionStatusListener(statusListener);
            service.getCurrentStatus();
        }
    }

    private void setupSpinner() {
        fullServerList = MqttPrefsManager.getServerList(requireContext());
        displayServerList.clear();
        for (String url : fullServerList) {
            displayServerList.add(MqttPrefsManager.displayUrl(url));
        }
        spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, displayServerList);
        serverSpinner.setAdapter(spinnerAdapter);
        serverSpinner.setThreshold(1);
        serverSpinner.setOnClickListener(v -> serverSpinner.showDropDown());
        serverSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String selectedDisplay = displayServerList.get(position);
            String selectedFull = MqttPrefsManager.fullUrl(selectedDisplay);
//            String currentFull = MqttPrefsManager.getBrokerUrl(requireContext());
            applyServerChange(selectedFull);
            UiUtils.showToast(getContext(), "Переключено на " + selectedDisplay);
            fillFieldsFromServer(selectedFull);
        });

        String currentFull = MqttPrefsManager.getBrokerUrl(requireContext());
        serverSpinner.setText(MqttPrefsManager.displayUrl(currentFull), false);
        fillFieldsFromServer(currentFull);
    }

    private void fillFieldsFromServer(String fullUrl) {
        try {
            URI uri = new URI(fullUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            editHost.setText(host != null ? host : "");
            editPort.setText(port != -1 ? String.valueOf(port) : "1883");
        } catch (URISyntaxException e) {
            editHost.setText(MqttPrefsManager.displayUrl(fullUrl));
            editPort.setText("1883");
        }
        String username = MqttPrefsManager.getUsernameForServer(requireContext(), fullUrl);
        String password = MqttPrefsManager.getPasswordForServer(requireContext(), fullUrl);
        editUsername.setText(username != null ? username : "");
        editPassword.setText(password != null ? password : "");
    }

    private void setupSpinnerListeners() {
        serverSpinner.setOnClickListener(v -> serverSpinner.showDropDown());
        serverSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String selectedDisplay = displayServerList.get(position);
            String selectedFull = MqttPrefsManager.fullUrl(selectedDisplay);
            String currentFull = MqttPrefsManager.getBrokerUrl(requireContext());
            if (!selectedFull.equals(currentFull)) {
                applyServerChange(selectedFull);
            } else {
                fillFieldsFromServer(selectedFull);
            }
        });
    }

    private void updateServerList() {
        fullServerList = MqttPrefsManager.getServerList(requireContext());
        displayServerList.clear();
        for (String url : fullServerList) {
            displayServerList.add(MqttPrefsManager.displayUrl(url));
        }
        if (spinnerAdapter == null) {
            spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, displayServerList);
            serverSpinner.setAdapter(spinnerAdapter);
        } else {
            spinnerAdapter.clear();
            spinnerAdapter.addAll(displayServerList);
            spinnerAdapter.notifyDataSetChanged();
        }
        serverSpinner.setThreshold(1);
        String currentFull = MqttPrefsManager.getBrokerUrl(requireContext());
        serverSpinner.setText(MqttPrefsManager.displayUrl(currentFull), false);
        fillFieldsFromServer(currentFull);
    }

    private void addServer() {
        String host = editHost.getText().toString().trim();
        String portStr = editPort.getText().toString().trim();
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
        MqttPrefsManager.addServer(requireContext(), newServerUrl);
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString().trim();
        if (!username.isEmpty() || !password.isEmpty()) {
            MqttPrefsManager.saveServerCredentials(requireContext(), newServerUrl, username, password);
        }
        applyServerChange(newServerUrl);
    }

    private void updateSelectedServer() {
        String selectedDisplay = serverSpinner.getText().toString();
        if (selectedDisplay.isEmpty()) {
            UiUtils.showToast(getContext(), "Сначала выберите сервер из списка");
            return;
        }
        String oldFullUrl = MqttPrefsManager.fullUrl(selectedDisplay);
        String host = editHost.getText().toString().trim();
        String portStr = editPort.getText().toString().trim();
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
        String newFullUrl = "tcp://" + host + ":" + port;

        // Если изменился URL – нужно удалить старый и добавить новый
        if (!oldFullUrl.equals(newFullUrl)) {
            MqttPrefsManager.removeServerData(requireContext(), oldFullUrl);
            MqttPrefsManager.addServer(requireContext(), newFullUrl);
        }
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString().trim();
        MqttPrefsManager.saveServerCredentials(requireContext(), newFullUrl, username, password);

        // Если текущий активный сервер был изменён – переключаем
        String current = MqttPrefsManager.getBrokerUrl(requireContext());
        if (current.equals(oldFullUrl) || current.equals(newFullUrl)) {
            applyServerChange(newFullUrl);
        } else {
            // Просто обновляем список
            setupSpinner();
        }
    }

    private void deleteSelectedServer() {
        String selectedDisplay = serverSpinner.getText().toString();
        if (selectedDisplay.isEmpty()) {
            UiUtils.showToast(getContext(), "Сначала выберите сервер из списка");
            return;
        }
        String fullUrl = MqttPrefsManager.fullUrl(selectedDisplay);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Удалить сервер")
                .setMessage("Удалить сервер " + selectedDisplay + " и все связанные данные?")
                .setPositiveButton("Удалить", (d, w) -> {
                    MqttService service = MainActivity.getMqttService();
                    if (service != null) service.disconnectNow();

                    MqttPrefsManager.removeServerData(requireContext(), fullUrl);
                    TopicRepository.getInstance(requireActivity().getApplication())
                            .deleteAllDataForServer(fullUrl);
                    TopicRepository.getInstance(requireActivity().getApplication())
                            .clearCacheForServer(fullUrl);

                    String current = MqttPrefsManager.getBrokerUrl(requireContext());
                    if (fullUrl.equals(current)) {
                        // Переключиться на первый доступный
                        List<String> servers = MqttPrefsManager.getServerList(requireContext());
                        String newCurrent = servers.isEmpty() ? MqttPrefsManager.DEFAULT_BROKER : servers.get(0);
                        MqttPrefsManager.saveBrokerUrl(requireContext(), newCurrent);
                        if (service != null) service.changeBrokerUrl(newCurrent);
                        applyServerChange(newCurrent);
                    } else {
                        setupSpinner();
                        updateStatusDisplay();
                    }
                    if (service != null) service.getCurrentStatus();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void applyServerChange(String newFullUrl) {
        MqttPrefsManager.saveBrokerUrl(requireContext(), newFullUrl);
        updateServerList();

        TopicRepository repo = TopicRepository.getInstance(requireActivity().getApplication());
        repo.setCurrentServerUrl(newFullUrl);   // <-- добавить эту строку

        MqttService service = MainActivity.getMqttService();
        if (service != null) {
            service.changeBrokerUrl(newFullUrl);
        } else {
            // Если сервис ещё не готов, всё равно перезагружаем данные
            repo.refreshAllDataForCurrentServer();
        }
        updateStatusDisplay();
    }

    private void updateStatusDisplay() {
        String current = MqttPrefsManager.getBrokerUrl(requireContext());
        String display = MqttPrefsManager.displayUrl(current);
        currentServerStatus.setText("Сервер: " + display);
        MqttService service = MainActivity.getMqttService();
        if (service != null) {
            service.getCurrentStatus();
        } else {
            ledIndicator.setBackgroundResource(R.drawable.circle_red);
        }
    }
}