package com.example.mqttclient;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import com.example.mqttclient.Accessory.UiUtils;
import com.example.mqttclient.Adapters.MessageAdapter;
import com.example.mqttclient.Database.AppDatabase;
import com.example.mqttclient.Database.MessageEntity;
import com.example.mqttclient.Accessory.TopicRepository;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.ViewModels.TopicDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Date;
import java.util.List;

public class TopicDetailActivity extends AppCompatActivity {

    private static final String EXTRA_TOPIC = "topic";

    private String topicName;
    private MqttService mqttService;
    private TopicRepository repository;
    private MessageAdapter messageAdapter;
    private TopicDetailViewModel viewModel;

    private TextView topicNameHeader;
    private MaterialButton btnBack, btnAdd, btnClear;
    private Chip statusValue;
    private TextView lastMessageTime;
    private RecyclerView messagesRecycler;

    public static void start(Context context, String topicName) {
        Intent intent = new Intent(context, TopicDetailActivity.class);
        intent.putExtra(EXTRA_TOPIC, topicName);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topic_details);

        if (savedInstanceState == null) {
            TopicRepository.getInstance(getApplication()).clearHasUnread(topicName);
        }

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
        observeData();
        updateSubscriptionButton();
        TopicRepository.getInstance(getApplication()).clearHasUnread(topicName);
    }

    private void initViews() {
        topicNameHeader = findViewById(R.id.topic_name_header);
        btnBack = findViewById(R.id.btn_back);
        btnAdd = findViewById(R.id.btn_add_server);
        btnClear = findViewById(R.id.btn_clear);
        statusValue = findViewById(R.id.status_value);
        lastMessageTime = findViewById(R.id.last_message_time);
        messagesRecycler = findViewById(R.id.messages_recycler);

        topicNameHeader.setText(topicName);
        btnBack.setOnClickListener(v -> finish());

        btnAdd.setOnClickListener(v -> {
            boolean isSubscribed = repository.getSubscribedTopicsSet().contains(topicName);
            if (isSubscribed) {
                if (MainActivity.getMqttService() != null)
                    MainActivity.getMqttService().unsubscribe(topicName);
                repository.removeSubscribedTopic(topicName);
                btnAdd.setText("Подписаться");
                UiUtils.showToast(this, "Удалено из избранного: " + topicName);
                btnAdd.setIcon(getDrawable(R.drawable.ic_add));
            } else {
                if (MainActivity.getMqttService() != null)
                    MainActivity.getMqttService().subscribe(topicName);
                repository.addSubscribedTopic(topicName);
                UiUtils.showToast(this, "Добавлено в избранное: " + topicName);
                btnAdd.setText("Отписаться");
                btnAdd.setIcon(getDrawable(R.drawable.ic_check));
            }
        });

        btnClear.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Очистить историю")
                    .setMessage("Удалить все сообщения этого топика?")
                    .setPositiveButton("Удалить", (d, w) -> {
                        repository.clearTopicHistory(topicName);
                        repository.refreshTopicLastMessage(topicName); // ← добавить
                        Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });
    }

    private void setupRecyclerView() {
        messagesRecycler.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new MessageAdapter();
        messagesRecycler.setAdapter(messageAdapter);
    }

    private void observeData() {
        viewModel.getMessagesLiveData().observe(this, messages -> {
            messageAdapter.setMessages(messages);
            if (messages != null && !messages.isEmpty()) {
                messagesRecycler.scrollToPosition(0);
                // Обновляем информацию о топике по последнему сообщению
                MessageEntity lastMsg = messages.get(0);
//                updateTopicInfoFromMessage(lastMsg);
            } else {
                // Сообщений нет – сбрасываем статус
                statusValue.setText("Неактивен");
                statusValue.setBackgroundColor(getColor(android.R.color.darker_gray));
                lastMessageTime.setText("Последнее сообщение: нет");
            }
        });
    }

    private void updateTopicInfoFromMessage(MessageEntity msg) {
        boolean active = (msg.retained == 0) &&
                (System.currentTimeMillis() - msg.timestamp < 5 * 60 * 1000);
        statusValue.setText(active ? "Активен" : "Неактивен");
        statusValue.setChipBackgroundColor(ColorStateList.valueOf(active ? getColor(android.R.color.holo_green_dark) : getColor(android.R.color.darker_gray)));

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault());
        lastMessageTime.setText("Последнее сообщение: " + sdf.format(new Date(msg.timestamp)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void updateSubscriptionButton() {
        boolean isSubscribed = repository.getSubscribedTopicsSet().contains(topicName);
        btnAdd.setText(isSubscribed ? "Отписаться" : "Подписаться");
        btnAdd.setIcon(isSubscribed ?
                getDrawable(R.drawable.ic_check) : getDrawable(R.drawable.ic_add));
    }

    private void refreshStatus() {
        repository.getLastNonRetainedMessageForTopicAsync(topicName, nonRetained -> {
            runOnUiThread(() -> {
                if (nonRetained != null && !nonRetained.isEmpty()) {
                    MessageEntity lastNormal = nonRetained.get(0);
                    boolean active = (System.currentTimeMillis() - lastNormal.timestamp < 5 * 60 * 1000);
                    statusValue.setText(active ? "Активен" : "Неактивен");
                    statusValue.setChipBackgroundColor(ColorStateList.valueOf(active ?
                            getColor(android.R.color.holo_green_dark) : getColor(android.R.color.darker_gray)));
                } else {
                    statusValue.setText("Неактивен");
                    statusValue.setChipBackgroundColor(ColorStateList.valueOf(getColor(android.R.color.darker_gray)));
                }
            });
        });
        repository.getLastMessageForTopicAsync(topicName, any -> {
            runOnUiThread(() -> {
                if (any != null && !any.isEmpty()) {
                    updateTopicInfoFromMessage(any.get(0));
                }
            });
        });
    }
}