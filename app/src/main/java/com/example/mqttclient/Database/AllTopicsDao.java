package com.example.mqttclient.Database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AllTopicsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AllTopicsEntity topic);

    @Update
    void update(AllTopicsEntity topic);

    @Query("SELECT * FROM all_topics WHERE serverUrl = :serverUrl ORDER BY topicName ASC")
    LiveData<List<AllTopicsEntity>> getAllTopicsForServer(String serverUrl);

    @Query("SELECT * FROM all_topics WHERE topicName = :topic AND serverUrl = :serverUrl LIMIT 1")
    List<AllTopicsEntity> getTopic(String topic, String serverUrl);

    @Query("SELECT * FROM all_topics WHERE serverUrl = :serverUrl")
    List<AllTopicsEntity> getAllTopicsForServerSync(String serverUrl);

    @Query("UPDATE all_topics SET lastSeenTimestamp = :timestamp WHERE topicName = :topic AND serverUrl = :serverUrl")
    void updateLastSeen(String topic, String serverUrl, long timestamp);

    @Query("UPDATE all_topics SET lastMessage = :lastMessage, lastMessageTimestamp = :timestamp WHERE topicName = :topicName AND serverUrl = :serverUrl")
    void updateLastMessage(String topicName, String serverUrl, String lastMessage, long timestamp);

    @Query("UPDATE all_topics SET hasUnread = 1 WHERE topicName = :topicName AND serverUrl = :serverUrl")
    void setHasUnread(String topicName, String serverUrl);

    @Query("UPDATE all_topics SET hasUnread = 0 WHERE topicName = :topicName AND serverUrl = :serverUrl")
    void clearHasUnread(String topicName, String serverUrl);

    @Query("DELETE FROM all_topics WHERE serverUrl = :serverUrl")
    void deleteAllForServer(String serverUrl);
}
