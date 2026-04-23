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

    @Query("DELETE FROM all_topics WHERE serverUrl = :serverUrl")
    void deleteAllForServer(String serverUrl);

    @Query("DELETE FROM all_topics WHERE serverUrl = :serverUrl AND hasRetained = 0 AND lastSeenTimestamp < :threshold")
    void deleteTemporaryTopics(String serverUrl, long threshold);
}
