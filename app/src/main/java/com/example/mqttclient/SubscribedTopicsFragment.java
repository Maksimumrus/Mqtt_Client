package com.example.mqttclient;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mqttclient.Accessory.MqttService;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Accessory.UiUtils;
import com.example.mqttclient.Adapters.SubscribedTopicsAdapter;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.ViewModels.SubscribedTopicsViewModel;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class SubscribedTopicsFragment extends BaseTopicsFragment {
    private RecyclerView recyclerView;
    private SearchView searchView;
    private ChipGroup chipStatusFilter;
    private FloatingActionButton fabAddTopics;
    private SubscribedTopicsAdapter adapter;
    private SubscribedTopicsViewModel viewModel;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_subscribedl_topics;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        recyclerView = view.findViewById(R.id.recycler_view);
        searchView = view.findViewById(R.id.search_view);
        chipStatusFilter = view.findViewById(R.id.chip_status_filter);
        fabAddTopics = view.findViewById(R.id.fab_add_topic);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SubscribedTopicsAdapter();
        recyclerView.setAdapter(adapter);

        TopicRepository repository = TopicRepository.getInstance(requireActivity().getApplication());
        viewModel = new ViewModelProvider(this).get(SubscribedTopicsViewModel.class);
        viewModel.init(repository);
        viewModel.getSubscribedTopics().observe(getViewLifecycleOwner(), topics -> adapter.setData(topics));

        adapter.setListener(new SubscribedTopicsAdapter.OnTopicActionListener() {
            @Override public void onTopicClick(Topic topic) {
                TopicDetailActivity.start(getContext(), topic.getName());
            }
            @Override public void onUnsubscribeClick(Topic topic) {
                if (mqttService != null) mqttService.unsubscribe(topic.getName());
                viewModel.unsubscribe(topic.getName());
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { applyFilters(); return true; }
            @Override public boolean onQueryTextChange(String newText) { applyFilters(); return true; }
        });
        chipStatusFilter.setOnCheckedChangeListener((group, checkedId) -> applyFilters());

        fabAddTopics.setOnClickListener(v -> showAddTopicDialog());

        return view;
    }

    @Override
    protected void onMqttServiceReadyExtended(MqttService service) {
        service.setMessageListener(new MqttService.MessageListener() {
            @Override
            public void onMessageArrived(String topic, String payload, long timestamp, boolean retained) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        TopicRepository repo = TopicRepository.getInstance(getActivity().getApplication());
                        repo.updateLastMessage(topic, payload, timestamp, retained);
                    });
                }
            }
            @Override
            public void onTopicDiscovered(String topic, long timestamp, boolean retained) { }
        });
        refreshList();
    }

    @Override
    protected void onServerChanged(String newFullUrl) {
        TopicRepository.getInstance(requireActivity().getApplication()).setCurrentServerUrl(newFullUrl);
    }

    @Override
    protected void refreshList() { }

//    @Override
//    protected void refreshList() {
//        if (viewModel.getSubscribedTopics().getValue() != null) {
//            adapter.setData(viewModel.getSubscribedTopics().getValue());
//        }
//    }

    @Override
    protected void applyFilters() {
        String query = searchView.getQuery() != null ? searchView.getQuery().toString() : "";
        int statusFilter = 0;
        int checkedId = chipStatusFilter.getCheckedChipId();
        if (checkedId == R.id.chip_active) statusFilter = 1;
        else if (checkedId == R.id.chip_inactive) statusFilter = 2;
        adapter.setFilter(query, statusFilter);
    }

    private void showAddTopicDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Подписаться на топик");
        final EditText input = new EditText(getContext());
        input.setHint("например, sensor/temperature");
        builder.setView(input);
        builder.setPositiveButton("Подписаться", (dialog, which) -> {
            String topic = input.getText().toString().trim();
            if (!topic.isEmpty()) {
                if (mqttService != null) mqttService.subscribe(topic);
                viewModel.subscribe(topic);
                UiUtils.showToast(getContext(), "Подписка на " + topic);
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }
}