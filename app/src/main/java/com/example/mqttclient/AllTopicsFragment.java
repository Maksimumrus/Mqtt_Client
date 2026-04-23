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
import android.widget.ProgressBar;
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
import com.example.mqttclient.Adapters.AllTopicsAdapter;
import com.example.mqttclient.Database.AllTopicsEntity;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.ViewModels.AllTopicsViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.Set;

public class AllTopicsFragment extends Fragment implements MainActivity.MqttServiceConsumer {
    private MqttService mqttService;

    private RecyclerView recyclerView;
    protected SearchView searchView;
    protected Spinner serverSpinner;
    protected Button btnChangeServer;
    protected Button btnReconnect;
    private ProgressBar progressBar;
    private FloatingActionButton fabClear;

    private AllTopicsAdapter adapter;
    private AllTopicsViewModel viewModel;
    private Set<String> currentSubscriptions;

    protected ArrayAdapter<String> serverAdapter;
    protected List<String> serverList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_topics, container, false);
        recyclerView = view.findViewById(R.id.recycler_view);
        searchView = view.findViewById(R.id.search_view);
        serverSpinner = view.findViewById(R.id.spinner_server);
        btnChangeServer = view.findViewById(R.id.btn_change_server);
        btnReconnect = view.findViewById(R.id.btn_reconnect);
        progressBar = view.findViewById(R.id.progress_bar);
        fabClear = view.findViewById(R.id.fab_clear);

        loadServerSpinner();
        btnChangeServer.setOnClickListener(v -> showAddServerDialog());
        btnReconnect.setOnClickListener(v -> {
            if (mqttService != null) {
                mqttService.changeBrokerUrl(MqttPrefsManager.getBrokerUrl(getContext()));
            }
            else {
                UiUtils.showToast(getContext(), "Сервис не готов");
            }
        });

        serverSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                String selected = serverList.get(position);
                String current = MqttPrefsManager.getBrokerUrl(getContext());
                if (!selected.equals(current)) {
                    MqttPrefsManager.saveBrokerUrl(getContext(), selected);

                    TopicRepository repo = TopicRepository.getInstance(requireActivity().getApplication());
                    repo.setCurrentServerUrl(selected);

                    if (mqttService != null) {
                        mqttService.changeBrokerUrl(selected);
                    }
                    viewModel.setServerUrl(selected);
                    refreshList();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        adapter = new AllTopicsAdapter();
        currentSubscriptions = TopicRepository.getInstance(getActivity().getApplication()).getSubscribedTopicsSet();
        adapter.setSubscribedTopics(currentSubscriptions);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(AllTopicsViewModel.class);

        viewModel.getAllTopics().observe(getViewLifecycleOwner(), topics -> {
            progressBar.setVisibility(View.GONE);
            adapter.setTopics(topics);
        });

        adapter.setListener(new AllTopicsAdapter.OnTopicActionListener() {
            @Override
            public void onTopicClick(String topicName) {
                TopicDetailActivity.start(getContext(), topicName);
            }

            @Override
            public void onAddToFavoritesClick(String topicName) {
                if (mqttService != null) {
                    mqttService.subscribe(topicName);
                }
                viewModel.addToFavorites(topicName);
                adapter.updateSubscriptionStatus(topicName, true);
                UiUtils.showToast(getContext(), "Добавлено в избранное: " + topicName);
            }

            @Override
            public void onRemoveFromFavoritesClick(String topicName) {
                // отписываемся
                if (mqttService != null) {
                    mqttService.unsubscribe(topicName);
                }
                viewModel.removeFromFavorites(topicName);
                adapter.updateSubscriptionStatus(topicName, false);
                UiUtils.showToast(getContext(), "Удалено из избранного: " + topicName);
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { applyFilters(); return true; }
            @Override public boolean onQueryTextChange(String newText) { applyFilters(); return true; }
        });

        fabClear.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setTitle("Удалить временные топики")
                    .setMessage("Удалить все топики без retained-сообщений, которые не обновлялись более часа?")
                    .setPositiveButton("Удалить", (d, which) -> {
                        viewModel.cleanTemporaryTopics(60 * 60 * 1000);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        return view;
    }

    @Override
    public void onMqttServiceReady(MqttService service) {
        this.mqttService = service;

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

        // Подписываемся на обнаружение топиков (через MqttService)
        service.setMessageListener(new MqttService.MessageListener() {
            @Override
            public void onMessageArrived(String topic, String payload, long timestamp, boolean retained) {
                // не нужно здесь ничего, т.к. обновление происходит через TopicRepository
            }

            @Override
            public void onTopicDiscovered(String topic, long timestamp, boolean retained) {
                // Сохраняем обнаруженный топик в БД
                if (getActivity() != null) {
                    TopicRepository.getInstance(getActivity().getApplication())
                            .addDiscoveredTopic(topic, timestamp, retained);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity && MainActivity.isMqttServiceBound()) {
            MqttService service = MainActivity.getMqttService();
            if (service != null) {
                onMqttServiceReady(service);
                viewModel.setServerUrl(MqttPrefsManager.getBrokerUrl(getContext()));
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
        adapter.setFilter(query);
    }

    private void refreshList() {
        currentSubscriptions = TopicRepository.getInstance(getActivity().getApplication()).getSubscribedTopicsSet();
        adapter.setSubscribedTopics(currentSubscriptions);
        LiveData<List<AllTopicsEntity>> topicsLive = viewModel.getAllTopics();
        if (topicsLive.getValue() != null) {
            adapter.setTopics(topicsLive.getValue());
        }
    }
}