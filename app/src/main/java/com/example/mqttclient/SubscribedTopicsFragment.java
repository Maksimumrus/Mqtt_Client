package com.example.mqttclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mqttclient.Accessory.MqttService;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Accessory.UiUtils;
import com.example.mqttclient.Adapters.BaseTopicsAdapter;
import com.example.mqttclient.Adapters.SubscribedTopicsAdapter;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.Models.TopicTreeNode;
import com.example.mqttclient.ViewModels.SubscribedTopicsViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SubscribedTopicsFragment extends BaseTopicsFragment {
    private RecyclerView recyclerView;
    private SearchView searchView;
    private ChipGroup chipStatusFilter;
    private FloatingActionButton fabAddTopics;
    private SubscribedTopicsAdapter adapter;
    private SubscribedTopicsViewModel viewModel;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_subscribed_topics;
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

        viewModel = new ViewModelProvider(this).get(SubscribedTopicsViewModel.class);
        viewModel.init(TopicRepository.getInstance(requireActivity().getApplication()));
        viewModel.getSubscribedTopics().observe(getViewLifecycleOwner(), topics -> {
            adapter.setData(topics);
            loadExpandedState();
        });

        adapter.setListener(new BaseTopicsAdapter.OnTreeNodeClickListener() {
            @Override
            public void onLeafClick(TopicTreeNode node, Object data) {
                Topic topic = (Topic) data;
                TopicDetailActivity.start(getContext(), topic.getName());
            }
            @Override
            public void onGroupClick(TopicTreeNode node) {
                saveExpandedState();
            }
            @Override
            public void onActionClick(TopicTreeNode node, Object data) {
                Topic topic = (Topic) data;
                if (mqttService != null) mqttService.unsubscribe(topic.getName());
                viewModel.unsubscribe(topic.getName());
                UiUtils.showToast(getContext(), "Удалено из избранного: " + topic.getName());
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { applyFilters(); return true; }
            @Override public boolean onQueryTextChange(String newText) { applyFilters(); return true; }
        });

        chipStatusFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            for (int i = 0; i < group.getChildCount(); i++) {
                Chip chip = (Chip) group.getChildAt(i);
                if (chip.getId() == checkedId) {
                    chip.setChipBackgroundColor(ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.primary)));
                    chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                } else {
                    chip.setChipBackgroundColor(ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.surface)));
                    chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                }
            }
            applyFilters();
        });
        chipStatusFilter.check(R.id.chip_all);

        fabAddTopics.setOnClickListener(v -> showAddTopicDialog());
        loadExpandedState();

        return view;
    }

    private void loadExpandedState() {
        SharedPreferences prefs = requireContext().getSharedPreferences("tree_state_subscribed", Context.MODE_PRIVATE);
        Set<String> groups = prefs.getStringSet("expanded_groups", new HashSet<>());
        Set<String> leaves = prefs.getStringSet("expanded_leaves", new HashSet<>());
        adapter.saveExpandedState(groups, leaves);
    }

    private void saveExpandedState() {
        SharedPreferences prefs = requireContext().getSharedPreferences("tree_state_subscribed", Context.MODE_PRIVATE);
        prefs.edit()
                .putStringSet("expanded_groups", adapter.getExpandedGroups())
                .putStringSet("expanded_leaves", adapter.getExpandedLeaves())
                .apply();
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
        viewModel.refresh();
    }

    @Override
    protected void refreshList() {
        // Принудительно перезапрашиваем текущий список подписок у репозитория
        List<Topic> topics = TopicRepository.getInstance(requireActivity().getApplication())
                .getSubscribedTopics().getValue();
        if (topics != null) {
            adapter.setData(topics);
            loadExpandedState();
        }
    }

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
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
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