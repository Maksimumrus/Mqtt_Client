package com.example.mqttclient;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SearchView;
import android.widget.Spinner;
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

import java.util.List;

public class SubscribedTopicsFragment extends Fragment implements MainActivity.MqttServiceConsumer {
    protected SubscribedTopicsAdapter adapter;
    protected SubscribedTopicsViewModel viewModel;
    protected MqttService mqttService;

    protected RecyclerView recyclerView;
    protected SearchView searchView;
    protected ChipGroup chipStatusFilter;
    protected AutoCompleteTextView serverSpinner;
    protected Button btnChangeServer;
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
        btnChangeServer = view.findViewById(R.id.btn_change_server);
        btnReconnect = view.findViewById(R.id.btn_reconnect);
        fabAddTopics = view.findViewById(R.id.fab_add_topic);

        loadServerSpinner();
        btnChangeServer.setOnClickListener(v -> showAddServerDialog());
        btnReconnect.setOnClickListener(v -> {
            if (mqttService != null) {
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
                TextView statusView = getView().findViewById(R.id.connection_status);
                View ledIndicator = getView().findViewById(R.id.led_indicator);
                if (statusView != null) {
                    statusView.setText(status);
                }
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
        serverList = MqttPrefsManager.getServerList(getContext());
        String current = MqttPrefsManager.getBrokerUrl(getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, serverList);
        serverSpinner.setAdapter(adapter);
        serverSpinner.setText(current, false);
        serverSpinner.setThreshold(1);
        serverSpinner.setOnClickListener(v -> serverSpinner.showDropDown());
        serverSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String selected = serverList.get(position);
            String currentUrl = MqttPrefsManager.getBrokerUrl(getContext());
            if (!selected.equals(currentUrl)) {
                MqttPrefsManager.saveBrokerUrl(getContext(), selected);
                TopicRepository repo = TopicRepository.getInstance(requireActivity().getApplication());
                repo.setCurrentServerUrl(selected);
                if (mqttService != null) {
                    mqttService.changeBrokerUrl(selected);
                }
            }
        });

        serverSpinner.setOnLongClickListener(v -> {
            showServerManagerDialog();
            return true;
        });
    }

    protected void showAddServerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Добавить MQTT сервер");
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50,20,50,20);
        EditText inputUrl = new EditText(getContext());
        inputUrl.setHint("tcp://192.168.1.100:1883");
        layout.addView(inputUrl);
        EditText inputUser = new EditText(getContext());
        inputUser.setHint("Логин (опционально)");
        layout.addView(inputUser);
        EditText inputPass = new EditText(getContext());
        inputPass.setHint("Пароль (опционально)");
        inputPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputPass);
        builder.setView(layout);
        builder.setPositiveButton("Добавить", (dialog, which) -> {
            String newServer = inputUrl.getText().toString().trim();
            String user = inputUser.getText().toString().trim();
            String pass = inputPass.getText().toString().trim();
            if (!newServer.isEmpty()) {
                MqttPrefsManager.addServer(getContext(), newServer);
                if (!user.isEmpty() || !pass.isEmpty())
                    MqttPrefsManager.saveServerCredentials(getContext(), newServer, user, pass);
                loadServerSpinner();
                serverSpinner.setSelection(serverList.size() - 1);
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showServerManagerDialog() {
        List<String> servers = MqttPrefsManager.getServerList(getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, servers);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Управление серверами");
        builder.setAdapter(adapter, (dialog, which) -> {
            // Редактирование или удаление
            String selected = servers.get(which);
            showEditServerDialog(selected, which);
        });
        builder.setPositiveButton("Добавить", (d, w) -> showAddServerDialog());
        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    private void showEditServerDialog(String server, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Редактировать сервер");
        final EditText input = new EditText(getContext());
        input.setText(server);
        builder.setView(input);
        builder.setPositiveButton("Сохранить", (d, w) -> {
            String newServer = input.getText().toString().trim();
            if (!newServer.isEmpty()) {
                List<String> servers = MqttPrefsManager.getServerList(getContext());
                servers.set(position, newServer);
                MqttPrefsManager.saveServerList(getContext(), servers);
                loadServerSpinner();
            }
        });
        builder.setNeutralButton("Удалить", (d, w) -> {
            List<String> servers = MqttPrefsManager.getServerList(getContext());
            servers.remove(position);
            MqttPrefsManager.saveServerList(getContext(), servers);
            loadServerSpinner();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
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