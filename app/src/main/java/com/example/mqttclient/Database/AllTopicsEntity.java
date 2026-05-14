package com.example.mqttclient.Database;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "all_topics",
        primaryKeys = {"topicName", "serverUrl"})
public class AllTopicsEntity {
    @NonNull
    public String topicName;
    public long lastSeenTimestamp;
    public String lastMessage;
    public long lastMessageTimestamp;
    public boolean hasRetained;
    @NonNull
    public String serverUrl;
    public boolean hasUnread;

    public AllTopicsEntity(@NonNull String topicName, long lastSeenTimestamp, boolean hasRetained, @NonNull String serverUrl) {
        this.topicName = topicName;
        this.lastSeenTimestamp = lastSeenTimestamp;
        this.lastMessage = null;
        this.lastMessageTimestamp = 0;
        this.hasRetained = hasRetained;
        this.serverUrl = serverUrl;
    }

    @NonNull
    public String getTopicName() {
        return topicName;
    }
    public String getLastMessage() {
        return lastMessage;
    }
    public long getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }
    public boolean isHasUnread() {
        return hasUnread;
    }
    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }
    public void setLastMessageTimestamp(long lastMessageTimestamp) {
        this.lastSeenTimestamp = lastMessageTimestamp;
    }
}
