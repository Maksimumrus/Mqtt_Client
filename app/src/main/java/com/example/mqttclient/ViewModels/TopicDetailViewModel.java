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

    private TopicRepository repository;
    private String topicName;

    private MutableLiveData<Topic> topicLiveData = new MutableLiveData<>();
    private LiveData<List<MessageEntity>> messagesLiveData;

    public TopicDetailViewModel(Application application) {
        super(application);
    }

    public void init(TopicRepository repo, String topicName) {
        this.repository = repo;
        this.topicName = topicName;
        this.messagesLiveData = repository.getMessagesForTopic(topicName, 10);
    }

    public LiveData<Topic> getTopicLiveData() {
        return topicLiveData;
    }

    public LiveData<List<MessageEntity>> getMessagesLiveData() {
        return messagesLiveData;
    }

    public void refreshTopicInfo() {
        Topic topic = repository.getTopicByName(topicName);
        // Загружаем последнее сообщение из БД
        List<MessageEntity> lastMsg = repository.getLastMessageForTopic(topicName);
        if (lastMsg != null && !lastMsg.isEmpty()) {
            MessageEntity msg = lastMsg.get(0);
            topic.setLastMessage(msg.payload);
            topic.setLastMessageTime(new Date(msg.timestamp));
            topic.setClientId(msg.clientId);
            // Активен только если сообщение НЕ retained и свежее 5 минут
            boolean active = (msg.retained == 0) && (System.currentTimeMillis() - msg.timestamp < 5 * 60 * 1000);
            topic.setActive(active);
        } else {
            topic.setActive(false);
            topic.setLastMessage(null);
            topic.setLastMessageTime(null);
        }
        topicLiveData.setValue(topic);
    }
}
