package com.example.mqttclient.Database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "all_topics")
public class AllTopicsEntity {
    @PrimaryKey
    @NonNull
    public String topicName;
    public long lastSeenTimestamp;
    public boolean hasRetained;
    public String serverUrl;

    public AllTopicsEntity(String topicName, long lastSeenTimestamp, boolean hasRetained, String serverUrl) {
        this.topicName = topicName;
        this.lastSeenTimestamp = lastSeenTimestamp;
        this.hasRetained = hasRetained;
        this.serverUrl = serverUrl;
    }

    @NonNull
    public String getTopicName() {
        return topicName;
    }
    public boolean isHasRetained() {
        return hasRetained;
    }
}
