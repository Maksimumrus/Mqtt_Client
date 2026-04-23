package com.example.mqttclient.Accessory;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mqttclient.Database.AllTopicsEntity;
import com.example.mqttclient.Database.AppDatabase;
import com.example.mqttclient.Database.MessageEntity;
import com.example.mqttclient.Models.Topic;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TopicRepository {
    private static TopicRepository instance;
    private Application app;
    private MutableLiveData<List<Topic>> subscribedTopicsLive = new MutableLiveData<>(new ArrayList<>());
    private Map<String, Topic> topicMap = new HashMap<>();
    private Set<String> subscribedTopics = new HashSet<>();
    private String currentServerUrl;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private TopicRepository(Application app) {
        this.app = app;
        this.currentServerUrl = MqttPrefsManager.getBrokerUrl(app);
        loadSubscribedTopics();
        updateSubscribedList();
    }

    public static synchronized TopicRepository getInstance(Application app) {
        if (instance == null) instance = new TopicRepository(app);
        return instance;
    }

    public String getCurrentServerUrl() {
        return currentServerUrl;
    }

    public void setCurrentServerUrl(String serverUrl) {
        if (serverUrl.equals(currentServerUrl)) return;
        currentServerUrl = serverUrl;
        loadSubscribedTopics();
        updateSubscribedList();
        // Очищаем карту топиков, чтобы загрузить новые
        topicMap.clear();
    }

    private void loadSubscribedTopics() {
        subscribedTopics = MqttPrefsManager.getSubscribedTopicsSet(app, currentServerUrl);
    }

    public Set<String> getSubscribedTopicsSet() {
        return MqttPrefsManager.getSubscribedTopicsSet(app, currentServerUrl);
    }

    public Topic getTopicByName(String name) {
        Topic topic = topicMap.get(name);
        if (topic == null) {
            topic = new Topic(name);
            topicMap.put(name, topic);
        }
        return topic;
    }

    public void addSubscribedTopic(String topic) {
        if (subscribedTopics.add(topic)) {
            MqttPrefsManager.addSubscribedTopic(app, currentServerUrl, topic);
            updateSubscribedList();
        }
    }

    public void removeSubscribedTopic(String topic) {
        if (subscribedTopics.remove(topic)) {
            MqttPrefsManager.removeSubscribedTopic(app, currentServerUrl, topic);
            updateSubscribedList();
            // Очистить историю? По желанию
        }
    }

    private void updateSubscribedList() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<Topic> list = new ArrayList<>();
            Set<String> topics = getSubscribedTopicsSet();
            for (String t : topics) {
                Topic topic = topicMap.get(t);
                if (topic == null) {
                    topic = new Topic(t);
                    topicMap.put(t, topic);
                }
                List<MessageEntity> lastMsgs = AppDatabase.getInstance(app).messageDao()
                        .getLastMessages(t, currentServerUrl, 1);
                if (!lastMsgs.isEmpty()) {
                    MessageEntity msg = lastMsgs.get(0);
                    topic.setLastMessage(msg.payload);
                    topic.setLastMessageTime(new Date(msg.timestamp));
                    topic.setClientId(msg.clientId);
                    topic.setHasRetained(msg.retained == 1);
                    boolean active = (msg.retained == 0) && (System.currentTimeMillis() - msg.timestamp < 5 * 60 * 1000);
                    topic.setActive(active);
                } else {
                    topic.setActive(false);
                }
                list.add(topic);
            }
            // postValue безопасен из любого потока
            subscribedTopicsLive.postValue(list);
        });
    }

    public void updateLastMessage(String topic, String payload, long timestamp, boolean isRetained) {
        Topic t = topicMap.get(topic);
        if (t == null) {
            t = new Topic(topic);
            topicMap.put(topic, t);
        }

        t.setLastMessage(payload);
        t.setHasRetained(isRetained);
//        t.setLastMessageTime(new Date(timestamp));
//        t.setClientId(clientId);
//        t.setActive(isActive(timestamp));

        if (!isRetained) {
            t.setLastMessageTime(new Date(timestamp));
            t.setActive((System.currentTimeMillis() - timestamp) < 5 * 60 * 1000);
        }
        // Если retained – не обновляем время и не меняем активность
        if (getSubscribedTopicsSet().contains(topic)) {
            updateSubscribedList();
        }
    }

    public void addDiscoveredTopic(String topic, long timestamp, boolean retained) {
        executor.execute(() -> {
            AllTopicsEntity entity = new AllTopicsEntity(topic, timestamp, retained, currentServerUrl);
            // Проверяем, существует ли уже такой топик для этого сервера
            // Используем insert с IGNORE, а затем обновляем время
            AppDatabase.getInstance(app).allTopicsDao().insert(entity);
            // Обновляем время последнего появления
            AllTopicsEntity existing = getTopicEntitySync(topic);
            if (existing != null && existing.lastSeenTimestamp < timestamp) {
                existing.lastSeenTimestamp = timestamp;
                AppDatabase.getInstance(app).allTopicsDao().update(existing);
            }
        });
    }

    private AllTopicsEntity getTopicEntitySync(String topic) {
        // Вспомогательный синхронный метод – вызывается только в executor
        List<AllTopicsEntity> list = AppDatabase.getInstance(app).allTopicsDao()
                .getAllTopicsForServer(currentServerUrl).getValue();
        if (list != null) {
            for (AllTopicsEntity e : list) {
                if (e.topicName.equals(topic)) return e;
            }
        }
        return null;
    }

    public LiveData<List<AllTopicsEntity>> getAllTopicsLive() {
        return AppDatabase.getInstance(app).allTopicsDao().getAllTopicsForServer(currentServerUrl);
    }

    public LiveData<List<AllTopicsEntity>> getAllTopicsForServer(String serverUrl) {
        return AppDatabase.getInstance(app).allTopicsDao().getAllTopicsForServer(serverUrl);
    }

    public void clearAllTopicsForServer() {
        executor.execute(() -> {
            AppDatabase.getInstance(app).allTopicsDao().deleteAllForServer(currentServerUrl);
        });
    }

    private boolean isActive(long lastTimestamp) {
        long diffMinutes = (System.currentTimeMillis() - lastTimestamp) / (60_000);
        return diffMinutes < 5;
    }

    public LiveData<List<Topic>> getSubscribedTopics() { return subscribedTopicsLive; }

    private List<String> getAllTopicsFromDatabase() {
        // Выполнить запрос SELECT DISTINCT topic FROM messages
        return AppDatabase.getInstance(app).messageDao().getDistinctTopics(currentServerUrl);
    }

    public LiveData<List<MessageEntity>> getMessagesForTopic(String topic, int limit) {
        return AppDatabase.getInstance(app).messageDao().getLastMessagesLive(topic, currentServerUrl, limit);
    }

    public List<MessageEntity> getLastMessageForTopic(String topic) {
        return AppDatabase.getInstance(app).messageDao()
                .getLastMessages(topic, currentServerUrl, 1);
    }

    public void clearTopicHistory(String topic) {
        AppDatabase.getInstance(app).messageDao().clearTopicHistory(topic, currentServerUrl);
    }

    public void cleanTemporaryTopics(long olderThanMillis) {
        executor.execute(() -> {
            long threshold = System.currentTimeMillis() - olderThanMillis;
            // Удаляем топики, у которых hasRetained == 0 и lastSeenTimestamp < threshold
            AppDatabase.getInstance(app).allTopicsDao().deleteTemporaryTopics(currentServerUrl, threshold);
        });
    }
}
