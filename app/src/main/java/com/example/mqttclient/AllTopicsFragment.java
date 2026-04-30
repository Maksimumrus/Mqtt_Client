package com.example.mqttclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mqttclient.Accessory.MqttService;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Accessory.UiUtils;
import com.example.mqttclient.Adapters.AllTopicsAdapter;
import com.example.mqttclient.Adapters.BaseTopicsAdapter;
import com.example.mqttclient.Database.AllTopicsEntity;
import com.example.mqttclient.Models.TopicTreeNode;
import com.example.mqttclient.ViewModels.AllTopicsViewModel;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.HashSet;
import java.util.Set;

public class AllTopicsFragment extends BaseTopicsFragment {
    private RecyclerView recyclerView;
    private SearchView searchView;
    private CircularProgressIndicator progressBar;
    private AllTopicsAdapter adapter;
    private AllTopicsViewModel viewModel;
    private Set<String> currentSubscriptions;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_all_topics;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        recyclerView = view.findViewById(R.id.recycler_view);
        searchView = view.findViewById(R.id.search_view);
        progressBar = view.findViewById(R.id.progress_bar);

        adapter = new AllTopicsAdapter(currentSubscriptions);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(AllTopicsViewModel.class);
        viewModel.getAllTopicsTree().observe(getViewLifecycleOwner(), roots -> {
            progressBar.setVisibility(View.GONE);
            adapter.setTreeRoots(roots);
            loadExpandedState();
        });

        adapter.setListener(new BaseTopicsAdapter.OnTreeNodeClickListener() {
            @Override
            public void onLeafClick(TopicTreeNode node, Object data) {
                AllTopicsEntity entity = (AllTopicsEntity) data;
                TopicDetailActivity.start(getContext(), entity.topicName);
            }
            @Override
            public void onGroupClick(TopicTreeNode node) {
                saveExpandedState();
            }
            @Override
            public void onActionClick(TopicTreeNode node, Object data) {
                AllTopicsEntity entity = (AllTopicsEntity) data;
                boolean isSubscribed = currentSubscriptions.contains(entity.topicName);
                if (isSubscribed) {
                    if (mqttService != null) mqttService.unsubscribe(entity.topicName);
                    viewModel.removeFromFavorites(entity.topicName);
                    UiUtils.showToast(getContext(), "Удалено из избранного: " + entity.topicName);
                } else {
                    if (mqttService != null) mqttService.subscribe(entity.topicName);
                    viewModel.addToFavorites(entity.topicName);
                    UiUtils.showToast(getContext(), "Добавлено в избранное: " + entity.topicName);
                }
                refreshSubscriptions();
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { applyFilters(); return true; }
            @Override public boolean onQueryTextChange(String newText) { applyFilters(); return true; }
        });

        return view;
    }

    private void loadExpandedState() {
        SharedPreferences prefs = getContext().getSharedPreferences("tree_state_all", Context.MODE_PRIVATE);
        Set<String> groups = prefs.getStringSet("expanded_groups", new HashSet<>());
        Set<String> leaves = prefs.getStringSet("expanded_leaves", new HashSet<>());
        adapter.saveExpandedState(groups, leaves);
    }

    private void saveExpandedState() {
        SharedPreferences prefs = getContext().getSharedPreferences("tree_state_all", Context.MODE_PRIVATE);
        prefs.edit()
                .putStringSet("expanded_groups", adapter.getExpandedGroups())
                .putStringSet("expanded_leaves", adapter.getExpandedLeaves())
                .apply();
    }

    @Override
    protected void onMqttServiceReadyExtended(MqttService service) {
        // Подписываемся на обнаружение топиков
        service.setMessageListener(new MqttService.MessageListener() {
            @Override
            public void onMessageArrived(String topic, String payload, long timestamp, boolean retained) { }

            @Override
            public void onTopicDiscovered(String topic, long timestamp, boolean retained) {
                if (getActivity() != null) {
                    TopicRepository.getInstance(getActivity().getApplication())
                            .addDiscoveredTopic(topic, timestamp, retained);
                }
            }
        });
        refreshList();
    }

    @Override
    protected void onServerChanged(String newFullUrl) {
        viewModel.setServerUrl(newFullUrl);
        refreshSubscriptions();
    }

    @Override
    protected void refreshList() {
        currentSubscriptions = TopicRepository.getInstance(getActivity().getApplication()).getSubscribedTopicsSet();
        adapter.setSubscribedTopics(currentSubscriptions);
    }

//    @Override
//    protected void refreshList() {
//        currentSubscriptions = TopicRepository.getInstance(getActivity().getApplication()).getSubscribedTopicsSet();
//        adapter.setSubscribedTopics(currentSubscriptions);
//        if (viewModel.getAllTopics().getValue() != null) {
//            adapter.setTopics(viewModel.getAllTopics().getValue());
//        }
//    }

    @Override
    protected void applyFilters() {
        String query = searchView.getQuery() != null ? searchView.getQuery().toString() : "";
        adapter.setFilter(query);
    }

    private void refreshSubscriptions() {
        currentSubscriptions = TopicRepository.getInstance(getActivity().getApplication()).getSubscribedTopicsSet();
        adapter.setSubscribedTopics(currentSubscriptions);
    }
}