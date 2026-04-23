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

import com.example.mqttclient.Database.AppDatabase;
import com.example.mqttclient.Database.MessageEntity;
import com.example.mqttclient.TopicDetailActivity;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

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
    private boolean isConnected;

    public interface MessageListener {
        void onMessageArrived(String topic, String payload, long timestamp, boolean retained);
        void onTopicDiscovered(String topic, long timestamp, boolean retained);
    }

    public interface ConnectionStatusListener {
        void onStatusChanged(String status, boolean isConnected);
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
        String host = brokerUrl.replace("tcp://", "").replace("ssl://", "");
        String[] parts = host.split(":");
        String serverHost = parts[0];
        int serverPort = (parts.length > 1) ? Integer.parseInt(parts[1]) : 1883;

        client = MqttClient.builder()
                .useMqttVersion3()
                .identifier("AndroidClient_" + System.currentTimeMillis())
                .serverHost(serverHost)
                .serverPort(serverPort)
                .buildAsync();

        var connectBuilder = client.connectWith()
                .keepAlive(20);

        String username = MqttPrefsManager.getUsernameForServer(this, brokerUrl);
        String password = MqttPrefsManager.getPasswordForServer(this, brokerUrl);
        if (username != null && !username.isEmpty()) {
            connectBuilder.simpleAuth()
                    .username(username)
                    .password(password != null ? password.getBytes(StandardCharsets.UTF_8) : new byte[0])
                    .applySimpleAuth();
        }

        notifyStatus("Подключение к " + brokerUrl + "...", false);

        CompletableFuture<Mqtt3ConnAck> future = connectBuilder.send();
        future.whenComplete((connAck, throwable) -> {
            if (throwable != null) {
                Log.e(TAG, "Connection failed", throwable);
                notifyStatus("Ошибка: " + throwable.getMessage(), false);
                isConnected = false;
            } else {
                Log.d(TAG, "Connected successfully");
                notifyStatus("Подключено к " + brokerUrl, true);
                Set<String> topics = MqttPrefsManager.getSubscribedTopicsSet(MqttService.this, brokerUrl);
                isConnected = true;
                client.subscribeWith().topicFilter("#").callback(this::handleMessage).send();
                for (String topic : topics) {
                    subscribe(topic);
                }
            }
        });
    }

    public void changeBrokerUrl(String newUrl) {
        if (newUrl.equals(brokerUrl)) return;
        brokerUrl = newUrl;
        MqttPrefsManager.saveBrokerUrl(this, brokerUrl);
        notifyStatus("Переподключение...", false);
        if (client != null) {
            client.disconnect();
        }
        isConnected = false;
        connect();

//        Set<String> topics = MqttPrefsManager.getSubscribedTopicsSet(this, brokerUrl);
//        for (String topic : topics) subscribe(topic);
    }

    private void handleMessage(Mqtt3Publish publish) {
        String topic = publish.getTopic().toString();
        String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
        long timestamp = System.currentTimeMillis();
        boolean retained = publish.isRetain();

        if (messageListener != null) {
            messageListener.onTopicDiscovered(topic, timestamp, retained);
        }

        if (retained) {
            // Проверяем, не дубликат ли уже сохранённого retained
            List<MessageEntity> existing = AppDatabase.getInstance(this).messageDao()
                    .getLastMessages(topic, brokerUrl, 1);
            if (!existing.isEmpty() && existing.get(0).payload.equals(payload)) {
                // То же самое retained – игнорируем полностью
                return;
            }
            // Новое retained – сохраняем, но уведомление НЕ показываем
            MessageEntity entity = new MessageEntity();
            entity.topic = topic;
            entity.payload = payload;
            entity.timestamp = timestamp;
            entity.qos = publish.getQos().getCode();
            entity.retained = 1;
            entity.serverUrl = brokerUrl;
            AppDatabase.getInstance(this).messageDao().insert(entity);

            // Уведомление для retained не отправляем!
            if (messageListener != null) {
                messageListener.onMessageArrived(topic, payload, timestamp, true);
                messageListener.onTopicDiscovered(topic, timestamp, retained);
            }
            return;
        }

        MessageEntity entity = new MessageEntity();
        entity.topic = topic;
        entity.payload = payload;
        entity.timestamp = timestamp;
        entity.qos = publish.getQos().getCode();
        entity.retained = 0;
        entity.serverUrl = brokerUrl;
        AppDatabase.getInstance(this).messageDao().insert(entity);

        if (MqttPrefsManager.areNotificationsEnabled(this) &&
                MqttPrefsManager.getSubscribedTopicsSet(this, brokerUrl).contains(topic)) {
            showNotification(topic, payload);
        }
        if (messageListener != null) {
            messageListener.onMessageArrived(topic, payload, timestamp, false);
        }
    }

    public void subscribe(String topic) {
        if (topic == null || topic.trim().isEmpty()) return;

        topic = topic.trim();
        // Проверка валидности wildcard
        if (topic.contains("#") && !topic.endsWith("#")) {
            Log.e(TAG, "Invalid topic filter (misplaced #): " + topic);
            UiUtils.showError(this, "Неверный топик: # должен быть в конце");
            return;
        }
        if (topic.contains("+")) {
            // Проверка, что + не внутри сегмента
            String[] segments = topic.split("/");
            for (String seg : segments) {
                if (seg.contains("+") && seg.length() > 1) {
                    Log.e(TAG, "Invalid topic filter (misplaced +): " + topic);
                    UiUtils.showError(this, "Неверный топик: + должен быть отдельным сегментом");
                    return;
                }
            }
        }

        if (client != null) {
            String finalTopic = topic;
            client.subscribeWith()
                    .topicFilter(topic)
                    .callback(this::handleMessage)
                    .send()
                    .whenComplete((subAck, throwable) -> {
                        if (throwable != null) Log.e(TAG, "Subscribe failed", throwable);
                        else Log.d(TAG, "Subscribed to " + finalTopic);
                    });
        }
        MqttPrefsManager.addSubscribedTopic(this, brokerUrl, topic);
    }

    public void unsubscribe(String topic) {
        if (client != null) {
            client.unsubscribeWith()
                    .topicFilter(topic)
                    .send()
                    .whenComplete((unsubAck, throwable) -> {
                        if (throwable != null) Log.e(TAG, "Unsubscribe failed", throwable);
                        else Log.d(TAG, "Unsubscribed from " + topic);
                    });
        }
        MqttPrefsManager.removeSubscribedTopic(this, brokerUrl, topic);
    }

    public void publish(String topic, String payload) {
        if (client != null) {
            client.publishWith()
                    .topic(topic)
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .send();
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    private void showNotification(String topic, String payload) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Intent для открытия детального окна
        Intent intent = new Intent(this, TopicDetailActivity.class);
        intent.putExtra("topic", topic);
        intent.putExtra("server", brokerUrl); // передаём текущий сервер
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, topic.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String displayServer = brokerUrl.replace("tcp://", ""); // убираем префикс
        String shortPayload = payload.length() > 100 ? payload.substring(0,100) : payload;
        String content = String.format("[%s] %s", displayServer, shortPayload);
        String bigTextContent = String.format("Сервер: %s\nСообщение:\n%s", displayServer, payload);

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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "MQTT уведомления", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    public void setMessageListener(MessageListener listener) { this.messageListener = listener; }
    public void setConnectionStatusListener(ConnectionStatusListener listener) {
        this.statusListener = listener;
        if (listener != null) {
            // сразу отправить текущий статус
            String status = isConnected ? "Подключено к " + brokerUrl : "Отключено";
            listener.onStatusChanged(status, isConnected);
        }
    }

    private void notifyStatus(String status, boolean connected) {
        if (statusListener != null) statusListener.onStatusChanged(status, connected);
    }
}