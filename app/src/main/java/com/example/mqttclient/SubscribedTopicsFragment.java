package com.example.mqttclient;

import static com.example.mqttclient.Accessory.MqttPrefsManager.displayUrl;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mqttclient.Accessory.MqttPrefsManager;
import com.example.mqttclient.Accessory.MqttService;
import com.example.mqttclient.Accessory.UiUtils;
import com.example.mqttclient.Adapters.SubscribedTopicsAdapter;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.ViewModels.SubscribedTopicsViewModel;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class SubscribedTopicsFragment extends Fragment implements MainActivity.MqttServiceConsumer {
    protected SubscribedTopicsAdapter adapter;
    protected SubscribedTopicsViewModel viewModel;
    protected MqttService mqttService;

    protected RecyclerView recyclerView;
    protected SearchView searchView;
    protected ChipGroup chipStatusFilter;
    protected AutoCompleteTextView serverSpinner;
    protected TextView statusView;
    protected Button btnAddServer;
    protected Button btnReconnect;
    protected FloatingActionButton fabAddTopics;

    protected ArrayAdapter<String> serverAdapter;
    protected List<String> serverList;
    protected String pendingServerSwitch = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_subscribedl_topics, container, false);
        recyclerView = view.findViewById(R.id.recycler_view);
        searchView = view.findViewById(R.id.search_view);
        chipStatusFilter = view.findViewById(R.id.chip_status_filter);
        serverSpinner = view.findViewById(R.id.spinner_server);
        statusView = view.findViewById(R.id.connection_status);
        btnAddServer = view.findViewById(R.id.btn_add_server);
        btnReconnect = view.findViewById(R.id.btn_reconnect);
        fabAddTopics = view.findViewById(R.id.fab_add_topic);

        loadServerSpinner();
        btnAddServer.setOnClickListener(v -> showServerDialog(null, null, null));
        btnReconnect.setOnClickListener(v -> {
            if (mqttService != null) {
                if (statusView != null) statusView.setText("Переподключение...");
                mqttService.changeBrokerUrl(MqttPrefsManager.getBrokerUrl(getContext()));
            } else {
                UiUtils.showToast(getContext(), "Сервис не готов");
            }
        });

        fabAddTopics.setOnClickListener(v -> showAddTopicDialog());

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SubscribedTopicsAdapter();
        recyclerView.setAdapter(adapter);

        TopicRepository repository = TopicRepository.getInstance(requireActivity().getApplication());
        viewModel = new ViewModelProvider(this).get(SubscribedTopicsViewModel.class);
        viewModel.init(repository);

        // Наблюдаем за подписанными топиками
        viewModel.getSubscribedTopics().observe(getViewLifecycleOwner(), topics -> {
            adapter.setData(topics);
        });

        adapter.setListener(new SubscribedTopicsAdapter.OnTopicActionListener() {
            @Override public void onTopicClick(Topic topic) {
                TopicDetailActivity.start(getContext(), topic.getName());
            }
            @Override public void onUnsubscribeClick(Topic topic) {
                if (mqttService != null) mqttService.unsubscribe(topic.getName());
                viewModel.unsubscribe(topic.getName());
                refreshList();
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { applyFilters(); return true; }
            @Override public boolean onQueryTextChange(String newText) { applyFilters(); return true; }
        });
        chipStatusFilter.setOnCheckedChangeListener((group, checkedId) -> applyFilters());

        return view;
    }

    @Override
    public void onMqttServiceReady(MqttService service) {
        this.mqttService = service;
        // Слушатель статуса подключения
        service.setConnectionStatusListener((status, isConnected) -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                String shortUrl = MqttPrefsManager.displayUrl(MqttPrefsManager.getBrokerUrl(getContext()));
                String displayStatus = isConnected ? "Подключено к " + shortUrl : "Отключено";
                TextView statusView = getView().findViewById(R.id.connection_status);
                View ledIndicator = getView().findViewById(R.id.led_indicator);
                statusView.setText(displayStatus);
                if (ledIndicator != null) {
                    ledIndicator.setBackgroundResource(isConnected ? R.drawable.circle_green : R.drawable.circle_red);
                }
            });
        });

//        service.setMessageListener((topic, payload, timestamp, retained) -> {
//            if (getActivity() == null) return;
//            getActivity().runOnUiThread(() -> {
//                TopicRepository repo = TopicRepository.getInstance(getActivity().getApplication());
//                repo.updateLastMessage(topic, payload, timestamp, retained);
//            });
//        });

        service.setMessageListener(new MqttService.MessageListener() {
            @Override
            public void onMessageArrived(String topic, String payload, long timestamp, boolean retained) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    TopicRepository repo = TopicRepository.getInstance(getActivity().getApplication());
                    repo.updateLastMessage(topic, payload, timestamp, retained);
                });
            }
            @Override
            public void onTopicDiscovered(String topic, long timestamp, boolean retained) { }
        });

        if (pendingServerSwitch != null) {
            mqttService.changeBrokerUrl(pendingServerSwitch);
            pendingServerSwitch = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity && MainActivity.isMqttServiceBound()) {
            MqttService service = MainActivity.getMqttService();
            if (service != null) {
                onMqttServiceReady(service);
            }
        }
        if (serverSpinner != null) loadServerSpinner();
    }

    protected void loadServerSpinner() {
        List<String> fullServers = MqttPrefsManager.getServerList(getContext());
        List<String> displayServers = new ArrayList<>();
        for (String url : fullServers) {
            displayServers.add(MqttPrefsManager.displayUrl(url));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_dropdown_item_1line, displayServers);
        serverSpinner.setAdapter(adapter);

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

    private void showServerDialog(@Nullable String oldServerUrl, @Nullable String oldUsername, @Nullable String oldPassword) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(oldServerUrl == null ? "Добавить MQTT сервер" : "Редактировать сервер");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // Поле "Хост" (без tcp://)
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

        // Если редактируем существующий — заполняем поля
        if (oldServerUrl != null) {
            try {
                URI uri = new URI(oldServerUrl);
                String host = uri.getHost();
                int port = uri.getPort();
                inputHost.setText(host != null ? host : displayUrl(oldServerUrl));
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
            // Добавляем в список
            MqttPrefsManager.addServer(getContext(), newServerUrl);
            if (!user.isEmpty() || !pass.isEmpty()) {
                MqttPrefsManager.saveServerCredentials(getContext(), newServerUrl, user, pass);
            }
            loadServerSpinner();
            // Если это был текущий сервер — переключаемся
            serverSpinner.setText(newServerUrl, false);
            // Вызываем переключение через обработчик
            applyServerChange(newServerUrl);
        });

        builder.setNeutralButton("Удалить", (d, w) -> {
            // Удаляем данные сервера из SharedPreferences
            MqttPrefsManager.removeServerData(getContext(), oldServerUrl);
            // Удаляем сообщения и топики из БД
            TopicRepository.getInstance(requireActivity().getApplication())
                    .deleteAllDataForServer(oldServerUrl);

            // Обновляем спиннер и текущий сервер
            loadServerSpinner();
            String current = MqttPrefsManager.getBrokerUrl(getContext());
            applyServerChange(current);
            refreshList();
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    // Вспомогательный метод для смены сервера
    private void applyServerChange(String newServerUrl) {
        String current = MqttPrefsManager.getBrokerUrl(getContext());
        if (!newServerUrl.equals(current)) {
            MqttPrefsManager.saveBrokerUrl(getContext(), newServerUrl);
            TopicRepository repo = TopicRepository.getInstance(requireActivity().getApplication());
            repo.setCurrentServerUrl(newServerUrl);
            if (mqttService != null) {
                mqttService.changeBrokerUrl(newServerUrl);
            }
            MqttPrefsManager.saveBrokerUrl(getContext(), newServerUrl);
            refreshList();
        }
    }

    private void showServerManagerDialog() {
        List<String> servers = MqttPrefsManager.getServerList(getContext());
        String[] items = servers.toArray(new String[0]);
        new AlertDialog.Builder(getContext())
                .setTitle("Управление серверами")
                .setItems(items, (dialog, which) -> {
                    String selectedUrl = servers.get(which);
                    // Получаем сохранённые для этого URL логин/пароль
                    String username = MqttPrefsManager.getUsernameForServer(getContext(), selectedUrl);
                    String password = MqttPrefsManager.getPasswordForServer(getContext(), selectedUrl);
                    // Показываем единый диалог редактирования
                    showServerDialog(selectedUrl, username, password);
                })
                .setPositiveButton("Добавить", (d, w) -> showServerDialog(null, null, null))
                .setNegativeButton("Отмена", null)
                .show();
    }

    protected void applyFilters() {
        String query = searchView.getQuery() != null ? searchView.getQuery().toString() : "";
        int statusFilter = 0;
        int checkedId = chipStatusFilter.getCheckedChipId();
        if (checkedId == R.id.chip_active) statusFilter = 1;
        else if (checkedId == R.id.chip_inactive) statusFilter = 2;
        adapter.setFilter(query, statusFilter);
    }

    protected void showAddTopicDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Подписаться на топик");
        final EditText input = new EditText(getContext());
        input.setHint("например, sensor/temperature");
        builder.setView(input);
        builder.setPositiveButton("Подписаться", (dialog, which) -> {
            String topic = input.getText().toString().trim();
            if (!topic.isEmpty()) {
                if (mqttService != null) {
                    mqttService.subscribe(topic);
                }
                // Также добавляем в репозиторий, чтобы топик появился в списке "Мои подписки"
                TopicRepository repo = TopicRepository.getInstance(requireActivity().getApplication());
                repo.addSubscribedTopic(topic);
                // Обновляем список (через ViewModel)
                viewModel.subscribe(topic);
                // Если мы во вкладке "Все топики" – нужно обновить список всех топиков
                // (но топик всё равно будет виден только после получения первого сообщения)
                UiUtils.showToast(getContext(), "Подписка на " + topic);
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void refreshList() {
        LiveData<List<Topic>> topicsLive = viewModel.getSubscribedTopics();
        if (topicsLive.getValue() != null) {
            adapter.setData(topicsLive.getValue());
        }
    }
}