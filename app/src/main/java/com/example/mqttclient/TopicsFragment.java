package com.example.mqttclient;

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
import com.example.mqttclient.Adapters.BaseTopicsAdapter;
import com.example.mqttclient.Adapters.TopicsAdapter;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.Models.TopicTreeNode;
import com.example.mqttclient.ViewModels.SubscribedTopicsViewModel;

import java.util.List;

public class TopicsFragment extends BaseTopicsFragment {
    private SearchView searchView;
    private TopicsAdapter adapter;
    private SubscribedTopicsViewModel viewModel;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_topics;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        searchView = view.findViewById(R.id.search_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TopicsAdapter();
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(SubscribedTopicsViewModel.class);
        viewModel.init(TopicRepository.getInstance(requireActivity().getApplication()));
        viewModel.getSubscribedTopics().observe(getViewLifecycleOwner(), topics -> {
            adapter.setData(topics);
//            loadExpandedState();
        });

        adapter.setListener(new BaseTopicsAdapter.OnTreeNodeClickListener() {
            @Override
            public void onLeafClick(TopicTreeNode node, Object data) {
                Topic topic = (Topic) data;
                TopicDetailActivity.start(getContext(), topic.getName());
            }
            @Override
            public void onGroupClick(TopicTreeNode node) {
//                saveExpandedState();
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

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        String currentServer = TopicRepository.getInstance(requireActivity().getApplication()).getCurrentServerUrl();
        TopicRepository.getInstance(requireActivity().getApplication()).setCurrentServerUrl(currentServer);
        viewModel.refresh();
        refreshList();
    }

//    private void loadExpandedState() {
//        SharedPreferences prefs = requireContext().getSharedPreferences("tree_state_subscribed", Context.MODE_PRIVATE);
//        Set<String> groups = prefs.getStringSet("expanded_groups", new HashSet<>());
//        Set<String> leaves = prefs.getStringSet("expanded_leaves", new HashSet<>());
//        adapter.saveExpandedState(groups, leaves);
//        adapter.initExpandedLeaves(leaves);
//    }
//
//    private void saveExpandedState() {
//        SharedPreferences prefs = requireContext().getSharedPreferences("tree_state_subscribed", Context.MODE_PRIVATE);
//        prefs.edit()
//                .putStringSet("expanded_groups", adapter.getExpandedGroups())
//                .putStringSet("expanded_leaves", adapter.getExpandedLeaves())
//                .apply();
//    }

    @Override
    protected void onMqttServiceReadyExtended(MqttService service) {
        service.setMessageListener(new MqttService.MessageListener() {
            @Override
            public void onMessageArrived(String topic, String payload, long timestamp, boolean retained) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        TopicRepository repo = TopicRepository.getInstance(getActivity().getApplication());
                        repo.updateLastMessage(topic, payload, timestamp, retained);
                        repo.markHasUnread(topic);
                    });
                }
            }
            @Override
            public void onTopicDiscovered(String topic, long timestamp, boolean retained) { }
        });
        refreshList();
    }

    @Override
    protected void refreshList() {
        List<Topic> topics = TopicRepository.getInstance(requireActivity().getApplication())
                .getSubscribedTopics().getValue();
        if (topics != null) {
            adapter.setData(topics);
//            loadExpandedState();
        }
    }

    @Override
    protected void applyFilters() {
        String query = searchView.getQuery() != null ? searchView.getQuery().toString() : "";
        adapter.setFilter(query);
    }
}