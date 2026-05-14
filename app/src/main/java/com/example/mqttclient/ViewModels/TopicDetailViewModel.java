package com.example.mqttclient.ViewModels;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mqttclient.Database.MessageEntity;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.Accessory.TopicRepository;

import java.util.Date;
import java.util.List;

public class TopicDetailViewModel extends AndroidViewModel {
    private LiveData<List<MessageEntity>> messagesLiveData;

    public TopicDetailViewModel(Application application) {
        super(application);
    }

    public void init(TopicRepository repo, String topicName) {
        this.messagesLiveData = repo.getMessagesForTopic(topicName, 10);
    }

    public LiveData<List<MessageEntity>> getMessagesLiveData() {
        return messagesLiveData;
    }
}
