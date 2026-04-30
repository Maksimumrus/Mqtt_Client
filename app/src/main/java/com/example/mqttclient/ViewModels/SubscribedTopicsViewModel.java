package com.example.mqttclient.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.mqttclient.Accessory.TopicTreeBuilder;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Models.TopicTreeNode;

import java.util.ArrayList;
import java.util.List;

public class SubscribedTopicsViewModel extends ViewModel {
    private TopicRepository repository;
    private MutableLiveData<List<Topic>> subscribedTopics = new MutableLiveData<>();

    public void init(TopicRepository repo) {
        this.repository = repo;
        // Подписываемся на изменения в репозитории
        repo.getSubscribedTopics().observeForever(topics -> {
            subscribedTopics.setValue(topics);
        });
    }

    public LiveData<List<Topic>> getSubscribedTopics() {
        return repository.getSubscribedTopics();
    }

    public void refresh() {
        if (repository != null) {
            repository.refreshSubscribedTopics();
        }
    }

    public void subscribe(String topic) { repository.addSubscribedTopic(topic); }
    public void unsubscribe(String topic) { repository.removeSubscribedTopic(topic); }
}