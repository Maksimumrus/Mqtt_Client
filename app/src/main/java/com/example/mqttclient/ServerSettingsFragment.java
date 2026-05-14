package com.example.mqttclient;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mqttclient.Accessory.MqttPrefsManager;
import com.example.mqttclient.Accessory.MqttService;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Accessory.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class ServerSettingsFragment extends Fragment {

    private Spinner serverSpinner;
    private TextInputEditText editHost, editPort, editUsername, editPassword;
    private TextView currentServerStatus;
    private View ledIndicator;

    private List<String> fullServerList = new ArrayList<>();
    private final List<String> displayServerList = new ArrayList<>();
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
        MaterialButton btnAdd = view.findViewById(R.id.btn_add_server);
        MaterialButton btnUpdate = view.findViewById(R.id.btn_update_server);
        MaterialButton btnDelete = view.findViewById(R.id.btn_delete_server);
        MaterialButton btnReconnect = view.findViewById(R.id.btn_reconnect);
        currentServerStatus = view.findViewById(R.id.current_server_status);
        ledIndicator = view.findViewById(R.id.led_indicator);

        setupSpinner();

        btnAdd.setOnClickListener(v -> addServer());
        btnUpdate.setOnClickListener(v -> updateSelectedServer());
        btnDelete.setOnClickListener(v -> deleteSelectedServer());

        btnReconnect.setOnClickListener(v -> {
            MqttService service = MainActivity.getMqttService();
            if (service != null) {
                service.changeBrokerUrl(MqttPrefsManager.getBrokerUrl(requireContext()));
            } else {
                UiUtils.showToast(getContext(), "Сервис не готов");
            }
        });

        setupConnectionStatusListener();
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
            service.setConnectionErrorListener(null);
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

            MqttService.ConnectionErrorListener errorListener = (errorMessage, failedUrl) -> {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        UiUtils.showError(requireContext(), "Ошибка сервера: " + errorMessage);
                    });
                }
            };
            service.setConnectionErrorListener(errorListener);
        }
    }

    private void setupSpinner() {
        fullServerList = MqttPrefsManager.getServerList(requireContext());
        updateDisplayServerList();
        spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, displayServerList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        serverSpinner.setAdapter(spinnerAdapter);

        serverSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < displayServerList.size()) {
                    String selectedDisplay = displayServerList.get(position);
                    String selectedFull = MqttPrefsManager.fullUrl(selectedDisplay);
                    switchToServer(selectedFull);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        serverSpinner.setOnLongClickListener(v -> {
            Object selectedItem = serverSpinner.getSelectedItem();
            if (selectedItem != null) {
                String selectedDisplay = selectedItem.toString();
                String selectedFull = MqttPrefsManager.fullUrl(selectedDisplay);
                fillFieldsFromServer(selectedFull);
                return true;
            }
            return false;
        });

        String currentFull = MqttPrefsManager.getBrokerUrl(requireContext());
        int currentPos = displayServerList.indexOf(MqttPrefsManager.displayUrl(currentFull));
        if (currentPos >= 0) serverSpinner.setSelection(currentPos);
    }

    private void updateDisplayServerList() {
        displayServerList.clear();
        for (String url : fullServerList) {
            displayServerList.add(MqttPrefsManager.displayUrl(url));
        }
    }

    private void switchToServer(String fullUrl) {
        String current = MqttPrefsManager.getBrokerUrl(requireContext());
        if (fullUrl.equals(current)) return;
        applyServerChange(fullUrl);
        clearFields();
    }

    private void refreshServerList() {
        fullServerList = MqttPrefsManager.getServerList(requireContext());
        updateDisplayServerList();
        spinnerAdapter.clear();
        spinnerAdapter.addAll(displayServerList);
        spinnerAdapter.notifyDataSetChanged();
        String currentFull = MqttPrefsManager.getBrokerUrl(requireContext());
        String currentDisplay = MqttPrefsManager.displayUrl(currentFull);
        int currentPos = displayServerList.indexOf(currentDisplay);
        if (currentPos >= 0) {
            serverSpinner.setSelection(currentPos);
        } else if (!displayServerList.isEmpty()) {
            serverSpinner.setSelection(0);
            String newCurrentFull = MqttPrefsManager.fullUrl(displayServerList.get(0));
            MqttPrefsManager.saveBrokerUrl(requireContext(), newCurrentFull);
            TopicRepository.getInstance(requireActivity().getApplication()).setCurrentServerUrl(newCurrentFull);
            MqttService service = MainActivity.getMqttService();
            if (service != null) service.changeBrokerUrl(newCurrentFull);
            updateStatusDisplay();
        }
    }

    private void applyServerChange(String newFullUrl) {
        MqttPrefsManager.saveBrokerUrl(requireContext(), newFullUrl);
        TopicRepository repo = TopicRepository.getInstance(requireActivity().getApplication());
        repo.setCurrentServerUrl(newFullUrl);
        MqttService service = MainActivity.getMqttService();
        if (service != null) {
            service.changeBrokerUrl(newFullUrl);
        } else {
            repo.refreshAllDataForCurrentServer();
        }
        updateStatusDisplay();
        updateSpinnerSelection(newFullUrl);
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
        Object selectedItem = serverSpinner.getSelectedItem();
        if (selectedItem == null) {
            UiUtils.showToast(getContext(), "Сначала выберите сервер из списка");
            return;
        }
        String selectedDisplay = selectedItem.toString();
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
        if (!oldFullUrl.equals(newFullUrl)) {
            MqttPrefsManager.removeServerData(requireContext(), oldFullUrl);
            MqttPrefsManager.addServer(requireContext(), newFullUrl);
        }
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString().trim();
        MqttPrefsManager.saveServerCredentials(requireContext(), newFullUrl, username, password);
        refreshServerList();
        String current = MqttPrefsManager.getBrokerUrl(requireContext());
        if (current.equals(oldFullUrl) || current.equals(newFullUrl)) {
            applyServerChange(newFullUrl);
        }
    }

    private void deleteSelectedServer() {
        Object selectedItem = serverSpinner.getSelectedItem();
        if (selectedItem == null) {
            UiUtils.showToast(getContext(), "Сначала выберите сервер из списка");
            return;
        }
        String selectedDisplay = selectedItem.toString();
        String fullUrl = MqttPrefsManager.fullUrl(selectedDisplay);
        new MaterialAlertDialogBuilder(requireContext())
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
                        List<String> servers = MqttPrefsManager.getServerList(requireContext());
                        String newCurrent = servers.isEmpty() ? MqttPrefsManager.DEFAULT_BROKER : servers.get(0);
                        MqttPrefsManager.saveBrokerUrl(requireContext(), newCurrent);
                        if (service != null) service.changeBrokerUrl(newCurrent);
                        applyServerChange(newCurrent);
                    } else {
                        refreshServerList();
                        updateStatusDisplay();
                    }
                    if (service != null) service.getCurrentStatus();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateSpinnerSelection(String fullUrl) {
        String display = MqttPrefsManager.displayUrl(fullUrl);
        int pos = displayServerList.indexOf(display);
        if (pos >= 0 && serverSpinner.getSelectedItemPosition() != pos) {
            serverSpinner.setSelection(pos);
        }
    }

    private void clearFields() {
        editHost.setText("");
        editPort.setText("");
        editUsername.setText("");
        editPassword.setText("");
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