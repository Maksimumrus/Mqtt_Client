package com.example.mqttclient.Database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "messages")
public class MessageEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String topic;
    public String payload;
    public long timestamp;
    public String clientId;
    public int qos;
    public int retained;
    public String serverUrl;
}
