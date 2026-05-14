package com.example.mqttclient.ViewModels;

import android.app.Application;
import android.util.Log;

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
    public MutableLiveData <String> currentServerUrl = new MutableLiveData<>();
    private LiveData<List<TopicTreeNode>> allTopicsTree;

    public AllTopicsViewModel(Application application) {
        super(application);
        repository = TopicRepository.getInstance(application);
        currentServerUrl.setValue(repository.getCurrentServerUrl());
        allTopicsTree = Transformations.switchMap(repository.getCurrentServerUrlLive(), serverUrl -> {
            Log.d("AllTopicsViewModel", "serverUrl = " + serverUrl);
            return Transformations.map(repository.getAllTopicsForServer(serverUrl), entities -> {
                Log.d("AllTopicsViewModel", "entities size = " + (entities == null ? 0 : entities.size()));
                return TopicTreeBuilder.buildTree(entities, true);
            });
        });
    }

    public void setServerUrl(String serverUrl) {
        if (!serverUrl.equals(currentServerUrl.getValue())) {
            currentServerUrl.setValue(serverUrl);
            repository.setCurrentServerUrl(serverUrl);
        }
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
}
