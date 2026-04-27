package com.example.mqttclient;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mqttclient.Accessory.MqttService;
import com.example.mqttclient.Accessory.UiUtils;
import com.example.mqttclient.Adapters.AllTopicsAdapter;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.ViewModels.AllTopicsViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.Set;

public class AllTopicsFragment extends BaseTopicsFragment {
    private RecyclerView recyclerView;
    private SearchView searchView;
    private CircularProgressIndicator progressBar;
    private FloatingActionButton fabClear;
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
        fabClear = view.findViewById(R.id.fab_clear);

        adapter = new AllTopicsAdapter();
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
        TopicRepository.getInstance(requireActivity().getApplication()).setCurrentServerUrl(newFullUrl);
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
}