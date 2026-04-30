package com.example.mqttclient.Database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {MessageEntity.class, AllTopicsEntity.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;
    public abstract MessageDao messageDao();
    public abstract AllTopicsDao allTopicsDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                instance = Room.databaseBuilder(context.getApplicationContext(),
                AppDatabase.class, "mqtt_history.db")
                        .fallbackToDestructiveMigration()
                        .build();
            }
        }
        return instance;
    }
}
