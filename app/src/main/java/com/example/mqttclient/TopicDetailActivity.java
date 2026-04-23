package com.example.mqttclient;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mqttclient.Accessory.MqttPrefsManager;
import com.example.mqttclient.Accessory.MqttService;
import com.example.mqttclient.Adapters.MessageAdapter;
import com.example.mqttclient.Database.AppDatabase;
import com.example.mqttclient.Database.MessageEntity;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.ViewModels.TopicDetailViewModel;

import java.util.Date;

public class TopicDetailActivity extends AppCompatActivity {

    private static final String EXTRA_TOPIC = "topic";

    private String topicName;
    private MqttService mqttService;
    private TopicRepository repository;
    private MessageAdapter messageAdapter;
    private TopicDetailViewModel viewModel;

    private TextView topicNameHeader;
    private ImageButton btnBack;
    private TextView statusValue;
//    private TextView clientValue;
    private TextView lastMessageTime;
    private RecyclerView messagesRecycler;
    private EditText publishInput;
    private Button publishBtn;

    public static void start(Context context, String topicName) {
        Intent intent = new Intent(context, TopicDetailActivity.class);
        intent.putExtra(EXTRA_TOPIC, topicName);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topic_details);

        topicName = getIntent().getStringExtra(EXTRA_TOPIC);
        if (topicName == null) {
            finish();
            return;
        }

        String serverUrl = getIntent().getStringExtra("server");
        if (serverUrl != null && !serverUrl.equals(MqttPrefsManager.getBrokerUrl(this))) {
            // Переключаем сервер в репозитории и сервисе
            MqttPrefsManager.saveBrokerUrl(this, serverUrl);
            TopicRepository.getInstance(getApplication()).setCurrentServerUrl(serverUrl);
            if (MainActivity.getMqttService() != null) {
                MainActivity.getMqttService().changeBrokerUrl(serverUrl);
            }
        }

        // Инициализация ViewModel и репозитория
        repository = TopicRepository.getInstance(getApplication());
        viewModel = new ViewModelProvider(this).get(TopicDetailViewModel.class);
        viewModel.init(repository, topicName);

        // Получаем MQTT сервис из MainActivity (если активность привязана)
        if (getParent() instanceof MainActivity) {
            mqttService = ((MainActivity) getParent()).getMqttService();
        } else {
            getApplication();
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(topicName);
        }

        initViews();
        setupRecyclerView();
        observeData();          // данные придут из LiveData автоматически
        setupPublishButton();
    }

    private void initViews() {
        topicNameHeader = findViewById(R.id.topic_name_header);
        btnBack = findViewById(R.id.btn_back);
        statusValue = findViewById(R.id.status_value);
//        clientValue = findViewById(R.id.client_value);
        lastMessageTime = findViewById(R.id.last_message_time);
        messagesRecycler = findViewById(R.id.messages_recycler);
        publishInput = findViewById(R.id.publish_input);
        publishBtn = findViewById(R.id.publish_btn);

        topicNameHeader.setText(topicName);
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        messagesRecycler.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new MessageAdapter();
        messagesRecycler.setAdapter(messageAdapter);
    }

    private void observeData() {
        // Наблюдаем за списком сообщений
        viewModel.getMessagesLiveData().observe(this, messages -> {
            messageAdapter.setMessages(messages);
            if (messages != null && !messages.isEmpty()) {
                messagesRecycler.scrollToPosition(messages.size() - 1);
                // Обновляем информацию о топике по последнему сообщению
                MessageEntity lastMsg = messages.get(messages.size() - 1);
                updateTopicInfoFromMessage(lastMsg);
            } else {
                // Сообщений нет – сбрасываем статус
                statusValue.setText("Неактивен");
                statusValue.setTextColor(getColor(android.R.color.darker_gray));
                lastMessageTime.setText("Последнее сообщение: нет");
            }
        });
    }

    private void updateTopicInfoFromMessage(MessageEntity msg) {
        boolean active = (msg.retained == 0) &&
                (System.currentTimeMillis() - msg.timestamp < 5 * 60 * 1000);
        statusValue.setText(active ? "Активен" : "Неактивен");
        statusValue.setTextColor(active ?
                getColor(android.R.color.holo_green_dark) :
                getColor(android.R.color.darker_gray));

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault());
        lastMessageTime.setText("Последнее сообщение: " + sdf.format(new Date(msg.timestamp)));
    }

    private void updateTopicInfo(Topic topic) {
        boolean active = topic.isActive();
        statusValue.setText(active ? "Активен" : "Неактивен");
        statusValue.setTextColor(active ?
                getColor(android.R.color.holo_green_dark) :
                getColor(android.R.color.darker_gray));

//        String cid = topic.getClientId();
//        clientValue.setText(cid != null ? cid : "unknown");

        if (topic.getLastMessageTime() != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault());
            lastMessageTime.setText("Последнее сообщение: " + sdf.format(topic.getLastMessageTime()));
        } else {
            lastMessageTime.setText("Последнее сообщение: нет");
        }
    }

    private void loadTopicInfo() {
        viewModel.refreshTopicInfo();
        viewModel.getMessagesLiveData();
    }

    private void setupPublishButton() {
        publishBtn.setOnClickListener(v -> {
            String payload = publishInput.getText().toString().trim();
            if (TextUtils.isEmpty(payload)) {
                Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show();
                return;
            }
            if (mqttService != null) {
                mqttService.publish(topicName, payload);
                publishInput.setText("");
                Toast.makeText(this, "Опубликовано", Toast.LENGTH_SHORT).show();
                // После публикации можно обновить историю: новое сообщение придёт через MQTT,
                // но можно добавить его сразу в БД для отзывчивости
                addLocalMessage(payload);
            } else {
                Toast.makeText(this, "MQTT сервис не доступен", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addLocalMessage(String payload) {
        // Опционально: сразу сохранить отправленное сообщение в БД,
        // чтобы оно отобразилось мгновенно, даже до получения от брокера
        MessageEntity entity = new MessageEntity();
        entity.topic = topicName;
        entity.payload = payload;
        entity.timestamp = System.currentTimeMillis();
        entity.qos = 1;
        entity.retained = 0;
        entity.clientId = "this_app";
        AppDatabase.getInstance(this).messageDao().insert(entity);
        viewModel.getMessagesLiveData(); // перезагрузить список
    }
}