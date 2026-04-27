package com.example.mqttclient.ViewModels;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.mqttclient.Database.AllTopicsEntity;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Database.AppDatabase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AllTopicsViewModel extends AndroidViewModel {
    private TopicRepository repository;
    private MutableLiveData <String> currentServerUrl = new MutableLiveData<>();
    private LiveData<List<AllTopicsEntity>> allTopics;
    private Application app;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public AllTopicsViewModel(Application application) {
        super(application);
        repository = TopicRepository.getInstance(application);
        currentServerUrl.setValue(repository.getCurrentServerUrl());
        allTopics = Transformations.switchMap(currentServerUrl, serverUrl ->
                repository.getAllTopicsForServer(serverUrl));
    }

    public void setServerUrl(String serverUrl) {
        if (!serverUrl.equals(currentServerUrl.getValue())) {
            currentServerUrl.setValue(serverUrl);
        }
    }

    public LiveData<List<AllTopicsEntity>> getAllTopics() {
        return allTopics;
    }

    public void addToFavorites(String topicName) {
        repository.addSubscribedTopic(topicName);
    }

    public void removeFromFavorites (String topicName) {
        repository.removeSubscribedTopic(topicName);
    }

    public void cleanTemporaryTopics(long olderThanMillis) {
        executor.execute(() -> {
            long threshold = System.currentTimeMillis() - olderThanMillis;
            String serverUrl = currentServerUrl.getValue(); // получить реальное значение
            if (serverUrl != null) {
                AppDatabase.getInstance(app).allTopicsDao().deleteTemporaryTopics(serverUrl, threshold);
            }
        });
    }
}
