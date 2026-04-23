package com.example.mqttclient.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.Accessory.TopicRepository;

import java.util.List;

public class SubscribedTopicsViewModel extends ViewModel {
    private TopicRepository repository;
    private LiveData<List<Topic>> subscribedTopics;

    public void init(TopicRepository repo) {
        this.repository = repo;
        subscribedTopics = repo.getSubscribedTopics();
    }

    public LiveData<List<Topic>> getSubscribedTopics() { return subscribedTopics; }

    public void subscribe(String topic) { repository.addSubscribedTopic(topic); }
    public void unsubscribe(String topic) { repository.removeSubscribedTopic(topic); }
}