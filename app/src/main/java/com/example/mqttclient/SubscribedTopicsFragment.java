package com.example.mqttclient;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class SubscribedTopicsFragment extends Fragment implements MainActivity.MqttServiceConsumer {
    protected SubscribedTopicsAdapter adapter;
    protected SubscribedTopicsViewModel viewModel;
    protected MqttService mqttService;

    protected RecyclerView recyclerView;
    protected SearchView searchView;
    protected RadioGroup radioStatus;
    protected Spinner serverSpinner;
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
        radioStatus = view.findViewById(R.id.radio_status);
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

        serverSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = serverList.get(position);
                String current = MqttPrefsManager.getBrokerUrl(getContext());
                if (!selected.equals(current)) {
                    MqttPrefsManager.saveBrokerUrl(getContext(), selected);
                    // Обновляем репозиторий
                    TopicRepository repo = TopicRepository.getInstance(requireActivity().getApplication());
                    repo.setCurrentServerUrl(selected);
                    if (mqttService != null) {
                        mqttService.changeBrokerUrl(selected);
                    }
                    // Перезагружаем адаптер
                    refreshList();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

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
        radioStatus.setOnCheckedChangeListener((group, checkedId) -> applyFilters());

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
                if (statusView != null) {
                    statusView.setText("Статус: " + status);
                    statusView.setBackgroundColor(isConnected ?
                            getResources().getColor(android.R.color.holo_green_light) :
                            getResources().getColor(android.R.color.holo_orange_light));
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
        serverAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, serverList);
        serverAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        serverSpinner.setAdapter(serverAdapter);
        int pos = serverList.indexOf(current);
        if (pos >= 0) serverSpinner.setSelection(pos);
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

    protected void applyFilters() {
        String query = searchView.getQuery() != null ? searchView.getQuery().toString() : "";
        int statusFilter = 0;
        if (radioStatus.getCheckedRadioButtonId() == R.id.radio_active) statusFilter = 1;
        else if (radioStatus.getCheckedRadioButtonId() == R.id.radio_inactive) statusFilter = 2;
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