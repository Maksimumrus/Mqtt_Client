package com.example.mqttclient.ViewModels;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.mqttclient.Accessory.TopicTreeBuilder;
import com.example.mqttclient.Database.AllTopicsEntity;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Database.AppDatabase;
import com.example.mqttclient.Models.TopicTreeNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AllTopicsViewModel extends AndroidViewModel {
    private TopicRepository repository;
    private LiveData<List<TopicTreeNode>> allTopicsTree;

    public AllTopicsViewModel(Application application) {
        super(application);
        repository = TopicRepository.getInstance(application);
        allTopicsTree = Transformations.switchMap(repository.getCurrentServerUrlLive(), serverUrl ->
                Transformations.map(repository.getAllTopicsForServer(serverUrl), entities ->
                        TopicTreeBuilder.buildTree(entities, true)
                ));
    }

    public LiveData<List<TopicTreeNode>> getAllTopicsTree() {
        return allTopicsTree;
    }

    public void addToFavorites(String topicName) {
        repository.addSubscribedTopic(topicName);
    }

    public void removeFromFavorites(String topicName) {
        repository.removeSubscribedTopic(topicName);
    }

    public void cleanTemporaryTopics(long olderThanMillis) {
        String serverUrl = repository.getCurrentServerUrl();
        if (serverUrl != null) {
            repository.cleanTemporaryTopics(olderThanMillis);
        }
    }
}
