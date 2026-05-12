package com.example.mqttclient.Database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.Date;
import java.util.List;

@Dao
public interface MessageDao {
    @Insert
    void insert(MessageEntity message);

    @Query("SELECT * FROM messages WHERE topic = :topic AND serverUrl = :serverUrl ORDER BY timestamp DESC LIMIT :limit")
    List<MessageEntity> getLastMessages(String topic, String serverUrl, int limit);

    @Query("SELECT DISTINCT topic FROM messages WHERE serverUrl = :serverUrl")
    List<String> getDistinctTopics(String serverUrl);

//    @Query("SELECT * FROM messages WHERE topic = :topic ORDER BY timestamp DESC")
//    List<MessageEntity> getAllMessagesForTopic(String topic);

    @Query("SELECT * FROM messages WHERE topic = :topic AND serverUrl = :serverUrl ORDER BY timestamp DESC LIMIT :limit")
    LiveData<List<MessageEntity>> getLastMessagesLive(String topic, String serverUrl, int limit);

    @Query("SELECT * FROM messages WHERE topic = :topic AND serverUrl = :serverUrl " +
            "AND payload = :payload AND retained = :retained AND timestamp > :since LIMIT 1")
    List<MessageEntity> findDuplicate(String topic, String serverUrl, String payload, int retained, long since);

    @Query("SELECT * FROM messages WHERE topic = :topic AND serverUrl = :serverUrl " +
            "AND retained = 0 ORDER BY timestamp DESC LIMIT 1")
    List<MessageEntity> getLastNonRetainedMessage(String topic, String serverUrl);

    @Query("SELECT * FROM messages WHERE topic = :topic AND serverUrl = :serverUrl " +
            "ORDER BY timestamp DESC LIMIT 1")
    List<MessageEntity> getLastMessage(String topic, String serverUrl);

    @Query("DELETE FROM messages WHERE topic = :topic AND serverUrl = :serverUrl")
    void clearTopicHistory(String topic, String serverUrl);

    @Query("DELETE FROM messages WHERE serverUrl = :serverUrl")
    void deleteAllForServer(String serverUrl);

    @Query("DELETE FROM messages WHERE topic = :topic AND timestamp < :beforeTime")
    void deleteOldMessages(String topic, long beforeTime);
}
