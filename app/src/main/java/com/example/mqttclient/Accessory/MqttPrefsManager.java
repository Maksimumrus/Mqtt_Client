package com.example.mqttclient.Accessory;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MqttPrefsManager {
    private static final String PREF_NAME = "mqtt_prefs";
    private static final String KEY_BROKER_URL = "broker_url";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    public static final String DEFAULT_BROKER = "tcp://broker.emqx.io:1883";

    public static void saveBrokerUrl(Context context, String url) {
        url = normalizeUrl(url);
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_BROKER_URL, url).apply();
    }

    public static String getBrokerUrl(Context context) {
        String url = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BROKER_URL, DEFAULT_BROKER);
        return normalizeUrl(url);
    }

    public static void saveServerList(Context context, List<String> list) {
        String json = new Gson().toJson(list);
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString("server_list", json).apply();
    }

    public static List<String> getServerList(Context context) {
        String json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString("server_list", null);
        if (json == null) {
            List<String> defaults = new ArrayList<>();
            defaults.add(DEFAULT_BROKER);
            defaults.add("tcp://test.mosquitto.org:1883");
            defaults.add("tcp://broker.hivemq.com:1883");
            return defaults;
        }
        Type type = new TypeToken<ArrayList<String>>(){}.getType();
        return new Gson().fromJson(json, type);
    }

    public static void saveServerCredentials(Context context, String serverUrl, String username, String password) {
        serverUrl = normalizeUrl(serverUrl);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(serverUrl + "_user", username)
                .putString(serverUrl + "_pass", password)
                .apply();
    }

    public static String getUsernameForServer(Context context, String serverUrl) {
        serverUrl = normalizeUrl(serverUrl);
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(serverUrl + "_user", null);
    }

    public static String getPasswordForServer(Context context, String serverUrl) {
        serverUrl = normalizeUrl(serverUrl);
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(serverUrl + "_pass", null);
    }

    public static void addServer(Context context, String serverUrl) {
        List<String> list = getServerList(context);
        serverUrl = normalizeUrl(serverUrl);
        if (!list.contains(serverUrl)) {
            list.add(serverUrl);
            saveServerList(context, list);
        }
    }

    public static void removeServerData(Context context, String serverUrl) {
        serverUrl = normalizeUrl(serverUrl);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.remove(getSubscribedTopicsKey(serverUrl));
        editor.remove(serverUrl + "_user");
        editor.remove(serverUrl + "_pass");

        List<String> servers = getServerList(context);
        if (servers.remove(serverUrl)) {
            String json = new Gson().toJson(servers);
            editor.putString("server_list", json);
        }

        String current = getBrokerUrl(context);
        if (serverUrl.equals(current)) {
            String newCurrent = servers.isEmpty() ? DEFAULT_BROKER : servers.get(0);
            editor.putString(KEY_BROKER_URL, newCurrent);
        }
        editor.apply();
    }

    private static void saveTopicsSet(Context context, String serverUrl, Set<String> topics) {
        String json = new Gson().toJson(topics);
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(getSubscribedTopicsKey(serverUrl), json).apply();
    }

    private static String getSubscribedTopicsKey(String serverUrl) {
        return "subscribed_topics_" + normalizeUrl(serverUrl);
    }

    public static Set<String> getSubscribedTopicsSet(Context context, String serverUrl) {
        String json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(getSubscribedTopicsKey(serverUrl), null);
        if (json == null) return new HashSet<>();
        Type type = new TypeToken<HashSet<String>>(){}.getType();
        return new Gson().fromJson(json, type);
    }

    public static void addSubscribedTopic(Context context, String serverUrl, String topic) {
        Set<String> topics = getSubscribedTopicsSet(context, serverUrl);
        topics.add(topic);
        saveTopicsSet(context, serverUrl, topics);
    }

    public static void removeSubscribedTopic(Context context, String serverUrl, String topic) {
        Set<String> topics = getSubscribedTopicsSet(context, serverUrl);
        topics.remove(topic);
        saveTopicsSet(context, serverUrl, topics);
    }

    public static void cleanInvalidTopics(Context context, String serverUrl) {
        Set<String> topics = getSubscribedTopicsSet(context, serverUrl);
        Set<String> valid = new HashSet<>();
        for (String t : topics) {
            if (t == null || t.isEmpty()) continue;
            if (t.contains("#") && !t.endsWith("#")) continue;
            valid.add(t);
        }
        if (valid.size() != topics.size()) {
            saveTopicsSet(context, serverUrl, valid);
        }
    }


    public static boolean areNotificationsEnabled(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }

    public static String normalizeUrl(String url) {
        if (url == null) return null;
        url = url.trim();
        if (url.startsWith("mqtt://")) {
            url = url.substring(7);
        }
        if (!url.startsWith("tcp://") && !url.startsWith("ssl://")) {
            url = "tcp://" + url;
        }
        url = url.replace("tcp:///", "tcp://");
        url = url.replace("ssl:///", "ssl://");
        return url;
    }
    public static String displayUrl(String fullUrl) {
        if (fullUrl == null) return "";
        String result = fullUrl;
        if (result.startsWith("tcp://")) result = result.substring(6);
        if (result.startsWith("ssl://")) result = result.substring(6);
        return result;
    }

    public static String fullUrl(String display) {
        if (display == null) return "";
        if (display.startsWith("tcp://") || display.startsWith("ssl://")) return display;
        return "tcp://" + display;
    }
}
