package com.example.mqttclient.Database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

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

    public AllTopicsEntity(String topicName, long lastSeenTimestamp, boolean hasRetained, String serverUrl) {
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
    public boolean isHasRetained() {
        return hasRetained;
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

    public void setHasUnread(boolean hasUnread) {
        this.hasUnread = hasUnread;
    }
}
