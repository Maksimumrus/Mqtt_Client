package com.example.mqttclient.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Models.Topic;

import java.util.List;

public class SubscribedTopicsViewModel extends ViewModel {
    private TopicRepository repository;
    private final MutableLiveData<List<Topic>> subscribedTopics = new MutableLiveData<>();

    public void init(TopicRepository repo) {
        this.repository = repo;
        repo.getSubscribedTopics().observeForever(subscribedTopics::setValue);
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