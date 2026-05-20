package com.example.mqttclient.Accessory;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.lifecycle.MutableLiveData;

import com.example.mqttclient.Database.AppDatabase;
import com.example.mqttclient.Database.MessageEntity;
import com.example.mqttclient.TopicDetailActivity;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class MqttService extends Service {
    private static final String TAG = "MqttService";
    private static final String CHANNEL_ID = "mqtt_channel";
    public static final String ACTION_CONNECT = "connect";

    private Mqtt3AsyncClient client;
    private String brokerUrl;
    private final IBinder binder = new LocalBinder();
    private MessageListener messageListener;
    private ConnectionStatusListener statusListener;
    private ConnectionErrorListener errorListener;
    private boolean isConnected;
    private final MutableLiveData<ConnectionState> connectionStateLiveData = new MutableLiveData<>();

    public interface MessageListener {
        void onMessageArrived(String topic, String payload, long timestamp, boolean retained);
        void onTopicDiscovered(String topic, long timestamp, boolean retained);
    }

    public interface ConnectionStatusListener {
        void onStatusChanged(String status, boolean isConnected);
    }

    public interface ConnectionErrorListener {
        void onConnectionError(String errorMessage, String failedUrl);
    }

    public class LocalBinder extends Binder {
        public MqttService getService() { return MqttService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        brokerUrl = MqttPrefsManager.getBrokerUrl(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CONNECT.equals(intent.getAction())) {
            connect();
        }
        return START_STICKY;
    }

    private void connect() {
        Log.d(TAG, "Connecting to " + brokerUrl);
        String host;
        int port;
        try {
            URI uri = new URI(brokerUrl);
            host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new URISyntaxException(brokerUrl, "Host not found");
            }
            port = uri.getPort();
            if (port == -1) {
                port = 1883; // стандартный порт MQTT
            }
        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid broker URL: " + brokerUrl, e);
            notifyStatus("Неверный URL сервера: " + brokerUrl, false);
            if (errorListener != null) {
                errorListener.onConnectionError("Неверный формат URL: " + e.getMessage(), brokerUrl);
            }
            return; // Не пытаемся подключаться
        }

        client = MqttClient.builder()
                .useMqttVersion3()
                .identifier("AndroidClient_" + System.currentTimeMillis())
                .serverHost(host)
                .serverPort(port)
                .buildAsync();

        var connectBuilder = client.connectWith()
                .keepAlive(20);

        if(!isPublicBroker(brokerUrl)) {
            String username = MqttPrefsManager.getUsernameForServer(this, brokerUrl);
            String password = MqttPrefsManager.getPasswordForServer(this, brokerUrl);

//            if (brokerUrl.equals(MqttPrefsManager.DEFAULT_BROKER) && username != null) {
//                Log.w(TAG, "Stored credentials for public broker, ignoring them");
//                username = null;
//                password = null;
//            }

            if (username != null && !username.isEmpty()) {
                connectBuilder.simpleAuth()
                        .username(username)
                        .password(password != null ? password.getBytes(StandardCharsets.UTF_8) : new byte[0])
                        .applySimpleAuth();
            }
        }

        notifyStatus("Подключение к " + brokerUrl + "...", false);

        CompletableFuture<Mqtt3ConnAck> future = connectBuilder.send();
        future.whenComplete((connAck, throwable) -> {
            if (throwable != null) {
                Log.e(TAG, "Connection failed", throwable);
                notifyStatus("Отключено", false);
                isConnected = false;
                if (errorListener != null) {
                    errorListener.onConnectionError(throwable.getMessage(), brokerUrl);
                }
            } else {
                Log.d(TAG, "Connected successfully");
                notifyStatus("Подключено", true);
                TopicRepository.getInstance(getApplication()).setCurrentServerUrl(brokerUrl);
                isConnected = true;
                applySubscriptions();
                client.subscribeWith().topicFilter("#").callback(this::handleMessage).send();
                TopicRepository.getInstance(getApplication()).refreshAllDataForCurrentServer();
            }
        });
    }

    public void changeBrokerUrl(String newUrl) {
        Log.d(TAG, "changeBrokerUrl called with: " + newUrl);

        brokerUrl = newUrl;
        MqttPrefsManager.saveBrokerUrl(this, brokerUrl);
        TopicRepository.getInstance(getApplication()).setCurrentServerUrl(brokerUrl);
        notifyStatus("Переключение...", false);

        if (client != null && isConnected) {
            client.disconnect().whenComplete((unused, throwable) -> {
                if (throwable != null) {
                    Log.e(TAG, "Disconnect error", throwable);
                }
                client = null;
                isConnected = false;
                connect();
            });
        } else {
            isConnected = false;
            connect();
        }
    }

    public void disconnectNow() {
        if (client != null) {
            try {
                client.disconnect().get(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                Log.e(TAG, "disconnectNow error", e);
            }
            client = null;
        }
        isConnected = false;
    }

    public void setConnectionErrorListener(ConnectionErrorListener listener) {
        this.errorListener = listener;
    }

    public void getCurrentStatus() {
        if (statusListener != null) {
            String status = isConnected ? "Подключено " : "Отключено";
            statusListener.onStatusChanged(status, isConnected);
        }
    }
    public void setConnectionStatusListener(ConnectionStatusListener listener) {
        this.statusListener = listener;
        if (listener != null) {
            String status = isConnected ? "Подключено" : "Отключено";
            listener.onStatusChanged(status, isConnected);
        }
    }

    private void notifyStatus(String status, boolean connected) {
        if (statusListener != null) statusListener.onStatusChanged(status, connected);
        connectionStateLiveData.postValue(new ConnectionState(status, connected));
    }

    public void subscribe(String topic) {
        if (topic == null || topic.trim().isEmpty()) return;

        topic = topic.trim();
        if (topic.contains("#") && !topic.endsWith("#")) {
            Log.e(TAG, "Invalid topic filter (misplaced #): " + topic);
            UiUtils.showError(this, "Неверный топик: # должен быть в конце");
            return;
        }
        if (topic.contains("+")) {
            String[] segments = topic.split("/");
            for (String seg : segments) {
                if (seg.contains("+") && seg.length() > 1) {
                    Log.e(TAG, "Invalid topic filter (misplaced +): " + topic);
                    UiUtils.showError(this, "Неверный топик: + должен быть отдельным сегментом");
                    return;
                }
            }
        }

        MqttPrefsManager.addSubscribedTopic(this, brokerUrl, topic);
        if (client != null && isConnected) {
            client.subscribeWith().topicFilter(topic).send()
                    .whenComplete((subAck, throwable) -> {
                        if (throwable != null) Log.e(TAG, "Subscribe error", throwable);
                    });
        }
    }

    public void unsubscribe(String topic) {
        MqttPrefsManager.removeSubscribedTopic(this, brokerUrl, topic);
        if (client != null && isConnected) {
            client.unsubscribeWith().topicFilter(topic).send()
                    .whenComplete((unsubAck, throwable) -> {
                        if (throwable != null) Log.e(TAG, "Unsubscribe error", throwable);
                    });
        }
    }

    private void handleMessage(Mqtt3Publish publish) {
        String topic = publish.getTopic().toString();
        String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
        long timestamp = System.currentTimeMillis();
        boolean retained = publish.isRetain();

        long withinMs = 2000;
        List<MessageEntity> dup = AppDatabase.getInstance(this).messageDao()
                .findDuplicate(topic, brokerUrl, payload, retained ? 1 : 0, timestamp - withinMs);
        if (!dup.isEmpty()) {
            return;
        }

        if (messageListener != null) {
            messageListener.onTopicDiscovered(topic, timestamp, retained);
        }

        MessageEntity entity = new MessageEntity();
        entity.topic = topic;
        entity.payload = payload;
        entity.timestamp = timestamp;
        entity.qos = publish.getQos().getCode();
        entity.retained = retained ? 1 : 0;
        entity.serverUrl = brokerUrl;
        AppDatabase.getInstance(this).messageDao().insert(entity);

        Log.d(TAG, "Message arrived: " + topic + " payload=" + payload);

        TopicRepository.getInstance(getApplication()).addDiscoveredTopic(topic, timestamp, retained);

        TopicRepository repo = TopicRepository.getInstance(getApplication());
        List<MessageEntity> lastAny = AppDatabase.getInstance(this).messageDao()
                .getLastMessage(topic, brokerUrl);
        boolean shouldUpdateLastMessage = true;
        boolean isDuplicateRetained = false;

        if (retained && !lastAny.isEmpty()) {
            if (lastAny.get(0).payload.equals(payload)) {
                shouldUpdateLastMessage = false;
                isDuplicateRetained = true;
            }
        }
        if (shouldUpdateLastMessage) {
            repo.updateTopicLastMessage(topic, payload, timestamp, retained);
        } else {
            repo.updateTopicLastSeen(topic, timestamp);
        }

        Set<String> subscribed = MqttPrefsManager.getSubscribedTopicsSet(this, brokerUrl);
        if (subscribed.contains(topic)) {
            boolean shouldMarkUnread = !retained || (retained && !isDuplicateRetained);
            if (shouldMarkUnread) {
                repo.markHasUnread(topic);
                if (!retained && MqttPrefsManager.areNotificationsEnabled(this)) {
                    showNotification(topic, payload);
                }
            }
        }

        if (messageListener != null) {
            Log.d(TAG, "Calling onTopicDiscovered for " + topic);
            messageListener.onMessageArrived(topic, payload, timestamp, retained);
        }
    }

    public void setMessageListener(MessageListener listener) { this.messageListener = listener; }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    private void showNotification(String topic, String payload) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Intent intent = new Intent(this, TopicDetailActivity.class);
        intent.putExtra("topic", topic);
        intent.putExtra("server", brokerUrl);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, topic.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String shortPayload = payload.length() > 100 ? payload.substring(0,100) : payload;
        String content = String.format("%s", shortPayload);
        String bigTextContent = String.format("Сообщение:\n%s", payload);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Новое сообщение в " + topic)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigTextContent))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify(topic.hashCode(), builder.build());
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "MQTT уведомления", NotificationManager.IMPORTANCE_HIGH);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private boolean isPublicBroker(String brokerUrl) {
        return brokerUrl.contains("broker.emqx.io") ||
                brokerUrl.contains("test.mosquitto.org") ||
                brokerUrl.contains("broker.hivemq.com");
    }

    private void applySubscriptions() {
        if (client == null || !isConnected) return;
        Set<String> topics = MqttPrefsManager.getSubscribedTopicsSet(this, brokerUrl);
        for (String topic : topics) {
            client.subscribeWith()
                    .topicFilter(topic)
                    .send()
                    .whenComplete((subAck, throwable) -> {
                        if (throwable != null) Log.e(TAG, "Subscribe failed: " + topic, throwable);
                    });
        }
    }

    public static class ConnectionState {
        public final String statusText;
        public final boolean isConnected;
        public ConnectionState(String statusText, boolean isConnected) {
            this.statusText = statusText;
            this.isConnected = isConnected;
        }
    }
}