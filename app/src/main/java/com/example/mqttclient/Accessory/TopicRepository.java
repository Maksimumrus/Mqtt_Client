package com.example.mqttclient.Accessory;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.mqttclient.Database.AllTopicsEntity;
import com.example.mqttclient.Database.AppDatabase;
import com.example.mqttclient.Database.MessageEntity;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.Models.TopicTreeNode;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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
        }
    }

    private void updateSubscribedList() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<AllTopicsEntity> allTopics = AppDatabase.getInstance(app).allTopicsDao()
                    .getAllTopicsForServerSync(currentServerUrl); // нужен синхронный метод
            Map<String, Boolean> unreadMap = new HashMap<>();
            for (AllTopicsEntity e : allTopics) {
                unreadMap.put(e.topicName, e.isHasUnread());
            }

            List<Topic> list = new ArrayList<>();
            Set<String> topics = getSubscribedTopicsSet();
            for (String t : topics) {
                Topic topic = new Topic(t);
                // последнее сообщение (как было)
                List<MessageEntity> lastMsgs = AppDatabase.getInstance(app).messageDao()
                        .getLastMessages(t, currentServerUrl, 1);
                if (!lastMsgs.isEmpty()) {
                    MessageEntity msg = lastMsgs.get(0);
                    topic.setLastMessage(msg.payload);
                    topic.setLastMessageTime(new Date(msg.timestamp));
                    topic.setHasRetained(msg.retained == 1);
                    boolean active = (msg.retained == 0) && (System.currentTimeMillis() - msg.timestamp < 5 * 60 * 1000);
                    topic.setActive(active);
                } else {
                    topic.setLastMessage(null);
                    topic.setLastMessageTime(null);
                    topic.setHasRetained(false);
                    topic.setActive(false);
                }
                topic.setUnread(unreadMap.getOrDefault(t, false));
                list.add(topic);
            }
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
            // Получаем последнее сообщение из БД для этого топика
            List<MessageEntity> lastMsg = AppDatabase.getInstance(app).messageDao()
                    .getLastMessages(topic, currentServerUrl, 1);
            String lastMessageText = null;
            long lastMsgTimestamp = 0;
            if (!lastMsg.isEmpty()) {
                MessageEntity msg = lastMsg.get(0);
                lastMessageText = msg.payload;
                lastMsgTimestamp = msg.timestamp;
            }

            AllTopicsEntity entity = new AllTopicsEntity(topic, timestamp, retained, currentServerUrl);
            if (lastMessageText != null) {
                entity.setLastMessage(lastMessageText);
                entity.setLastMessageTimestamp(lastMsgTimestamp);
            }
//            markHasUnread(topic);

            AllTopicsEntity existing = getTopicEntitySync(topic);
            if (existing != null) {
                existing.lastSeenTimestamp = Math.max(existing.lastSeenTimestamp, timestamp);
                existing.hasRetained = existing.hasRetained || retained;
                boolean oldUnread = existing.hasUnread;
                if (lastMsgTimestamp > existing.getLastMessageTimestamp()) {
                    existing.setLastMessage(lastMessageText);
                    existing.setLastMessageTimestamp(lastMsgTimestamp);
                }
                AppDatabase.getInstance(app).allTopicsDao().update(existing);
                existing.hasUnread = oldUnread;
                AppDatabase.getInstance(app).allTopicsDao().update(existing);
            } else {
                AppDatabase.getInstance(app).allTopicsDao().insert(entity);
            }
        });
    }

    private AllTopicsEntity getTopicEntitySync(String topic) {
        List<AllTopicsEntity> list = AppDatabase.getInstance(app).allTopicsDao()
                .getAllTopicsForServer(currentServerUrl).getValue();
        if (list != null) {
            for (AllTopicsEntity e : list) {
                if (e.topicName.equals(topic)) return e;
            }
        }
        return null;
    }

    public void updateTopicLastMessage(String topic, String payload, long timestamp, boolean isRetained) {
        executor.execute(() -> {
            if (isRetained) {
                List<AllTopicsEntity> existing = AppDatabase.getInstance(app).allTopicsDao()
                        .getTopic(topic, currentServerUrl);
                if (existing != null && !existing.isEmpty()) {
                    AllTopicsEntity e = existing.get(0);
                    if (!payload.equals(e.lastMessage)) {
                        e.lastMessage = payload;
                        e.lastMessageTimestamp = timestamp;
                        AppDatabase.getInstance(app).allTopicsDao().update(e);
                    }
                } else {
                    AllTopicsEntity entity = new AllTopicsEntity(topic, timestamp, true, currentServerUrl);
                    entity.setLastMessage(payload);
                    entity.setLastMessageTimestamp(timestamp);
                    AppDatabase.getInstance(app).allTopicsDao().insert(entity);
                }
            } else {
                AppDatabase.getInstance(app).allTopicsDao()
                        .updateLastMessage(topic, currentServerUrl, payload, timestamp);
                if (getSubscribedTopicsSet().contains(topic)) {
                    updateSubscribedList();
                }
            }
        });
    }

    public void updateTopicLastSeen(String topic, long timestamp) {
        executor.execute(() -> {
            AppDatabase.getInstance(app).allTopicsDao()
                    .updateLastSeen(topic, currentServerUrl, timestamp);
        });
    }

    public void getLastNonRetainedMessageForTopicAsync(String topic, Consumer<List<MessageEntity>> callback) {
        executor.execute(() -> {
            List<MessageEntity> result = AppDatabase.getInstance(app).messageDao()
                    .getLastNonRetainedMessage(topic, currentServerUrl);
            if (callback != null) callback.accept(result);
        });
    }

    public LiveData<List<AllTopicsEntity>> getAllTopicsLive() {
        return AppDatabase.getInstance(app).allTopicsDao().getAllTopicsForServer(currentServerUrl);
    }

    public LiveData<List<AllTopicsEntity>> getAllTopicsForServer(String serverUrl) {
        return AppDatabase.getInstance(app).allTopicsDao().getAllTopicsForServer(serverUrl);
    }

    public LiveData<List<MessageEntity>> getMessagesForTopic(String topic, int limit) {
        return AppDatabase.getInstance(app).messageDao().getLastMessagesLive(topic, currentServerUrl, limit);
    }

    public List<MessageEntity> getLastMessageForTopic(String topic) {
        return AppDatabase.getInstance(app).messageDao()
                .getLastMessages(topic, currentServerUrl, 1);
    }

    public void getLastMessageForTopicAsync(String topic, java.util.function.Consumer<List<MessageEntity>> callback) {
        executor.execute(() -> {
            List<MessageEntity> result = AppDatabase.getInstance(app).messageDao()
                    .getLastMessage(topic, currentServerUrl);
            if (callback != null) callback.accept(result);
        });
    }

    public void deleteAllDataForServer(String serverUrl) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(app);
            db.messageDao().deleteAllForServer(serverUrl);
            db.allTopicsDao().deleteAllForServer(serverUrl);
        });
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

    public void clearTopicHistory(String topic) {
        executor.execute(() -> {
            AppDatabase.getInstance(app).messageDao().clearTopicHistory(topic, currentServerUrl);
        });
    }

    public void refreshTopicLastMessage(String topic) {
        executor.execute(() -> {
            List<MessageEntity> last = AppDatabase.getInstance(app).messageDao()
                    .getLastMessage(topic, currentServerUrl);
            if (last != null && !last.isEmpty()) {
                MessageEntity msg = last.get(0);
                updateTopicLastMessage(topic, msg.payload, msg.timestamp, msg.retained == 1);
            } else {
                // Сообщений нет – очищаем lastMessage
                AppDatabase.getInstance(app).allTopicsDao()
                        .updateLastMessage(topic, currentServerUrl, null, 0);
                if (getSubscribedTopicsSet().contains(topic)) {
                    updateSubscribedList();
                }
            }
        });
    }

    public void cleanTemporaryTopics(long olderThanMillis) {
        executor.execute(() -> {
            long threshold = System.currentTimeMillis() - olderThanMillis;
            // Удаляем топики, у которых hasRetained == 0 и lastSeenTimestamp < threshold
            AppDatabase.getInstance(app).allTopicsDao().deleteTemporaryTopics(currentServerUrl, threshold);
        });
    }

    public void markHasUnread(String topic) {
        executor.execute(() -> {
            AppDatabase.getInstance(app).allTopicsDao().setHasUnread(topic, currentServerUrl);
            if (getSubscribedTopicsSet().contains(topic)) {
                updateSubscribedList();
            }
            AppDatabase.getInstance(app).allTopicsDao().getAllTopicsForServerSync(currentServerUrl);
        });
    }

    public void clearHasUnread(String topic) {
        executor.execute(() -> {
            AppDatabase.getInstance(app).allTopicsDao().clearHasUnread(topic, currentServerUrl);
            if (getSubscribedTopicsSet().contains(topic)) {
                updateSubscribedList();
            }
        });
    }

    public LiveData<List<TopicTreeNode>> getAllTopicsTree() {
        return Transformations.map(getAllTopicsLive(), entities -> {
            if (entities == null) return new ArrayList<>();
            return TopicTreeBuilder.buildTree(entities, true);
        });
    }

    public LiveData<List<TopicTreeNode>> getSubscribedTopicsTree() {
        return Transformations.map(getSubscribedTopics(), topics -> {
            if (topics == null) return new ArrayList<>();
            return TopicTreeBuilder.buildTree(topics, false);
        });
    }

    public void clearCacheForServer(String serverUrl) {
        if (currentServerUrl.equals(serverUrl)) {
            topicMap.clear();
            loadSubscribedTopics();
            // Немедленно очищаем LiveData, чтобы UI обновился
            subscribedTopicsLive.postValue(new ArrayList<>());
            updateSubscribedList(); // перезагрузит подписки для нового сервера
        }
    }

    public void refreshSubscribedTopics() {
        updateSubscribedList();
    }

    public void refreshAllDataForCurrentServer() {
        executor.execute(() -> {
            loadSubscribedTopics();
            updateSubscribedList();
        });
    }
}
