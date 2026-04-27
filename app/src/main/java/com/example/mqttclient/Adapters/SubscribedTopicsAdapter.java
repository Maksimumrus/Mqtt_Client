package com.example.mqttclient.Adapters;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.R;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SubscribedTopicsAdapter extends RecyclerView.Adapter<SubscribedTopicsAdapter.ViewHolder> {

    private List<Topic> topics = new ArrayList<>();
    private OnTopicActionListener listener;

    private String filterQuery = "";
    private int statusFilter = 0; // 0-все, 1-активные, 2-неактивные

    private List<Topic> originalTopics = new ArrayList<>();

    public interface OnTopicActionListener {
        void onTopicClick(Topic topic);
        void onUnsubscribeClick(Topic topic);
    }

    public void setListener(OnTopicActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<Topic> newTopics) {
        this.originalTopics = newTopics != null ? newTopics : new ArrayList<>();
        applyFilters();
    }

    public void setFilter(String query, int status) {
        this.filterQuery = (query == null) ? "" : query.toLowerCase();
        this.statusFilter = status;
        applyFilters();
    }

    private void applyFilters() {
        List<Topic> filtered = new ArrayList<>();
        for (Topic t : originalTopics) {
            // Фильтр по поисковому запросу (название топика или последнее сообщение)
            if (!filterQuery.isEmpty()) {
                boolean matchName = t.getName().toLowerCase().contains(filterQuery);
                boolean matchMsg = t.getLastMessage() != null &&
                        t.getLastMessage().toLowerCase().contains(filterQuery);
                if (!matchName && !matchMsg) continue;
            }
            // Фильтр по статусу
            if (statusFilter == 1 && !t.isActive()) continue;
            if (statusFilter == 2 && t.isActive()) continue;

            filtered.add(t);
        }
        this.topics = filtered;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_topic, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Topic topic = topics.get(position);
        holder.bind(topic);
    }

    @Override
    public int getItemCount() {
        return topics.size();
    }

    // Внутренний класс ViewHolder
    class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView topicName, lastMessage, timestamp, status;
//        TextView clientId;
        MaterialButton btnAction;
//        ImageView ivRetainedIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            topicName = itemView.findViewById(R.id.topic_name);
            lastMessage = itemView.findViewById(R.id.last_message);
            timestamp = itemView.findViewById(R.id.timestamp);
            status = itemView.findViewById(R.id.status);
//            clientId = itemView.findViewById(R.id.client_id);
            btnAction = itemView.findViewById(R.id.btn_action);
//            ivRetainedIndicator = itemView.findViewById(R.id.iv_retained_indicator);
        }

        void bind(Topic topic) {
            topicName.setText(topic.getName());

            String lastMsg = topic.getLastMessage();
            lastMessage.setText(lastMsg != null ? lastMsg : "нет сообщений");

            View statusIndicator = itemView.findViewById(R.id.status_indicator);
            statusIndicator.setBackgroundResource(topic.isActive() ? R.drawable.circle_green : R.drawable.circle_red);

            if (topic.getLastMessageTime() != null) {
                String timeStr = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                        .format(topic.getLastMessageTime());
                timestamp.setText(timeStr);
            } else {
                timestamp.setText("");
            }

            boolean active = false;
            if (topic.getLastMessageTime() != null && !topic.isHasRetained()) {
                long diff = System.currentTimeMillis() - topic.getLastMessageTime().getTime();
                active = diff < 5 * 60 * 1000; // 5 минут
            }

            statusIndicator.setBackgroundResource(active ? R.drawable.circle_green : R.drawable.circle_red);
            status.setText(active ? "Активен" : "Неактивен");

//            boolean active = topic.isActive();
//            status.setTextColor(active ? Color.GREEN : Color.GRAY);

//            String cid = topic.getClientId();
//            clientId.setText("ID: " + (cid != null ? cid : "?"));

//            if (topic.isHasRetained()) {
//                ivRetainedIndicator.setImageResource(R.drawable.ic_check);
//            } else {
//                ivRetainedIndicator.setImageResource(R.drawable.ic_temporary);
//            }
//            ivRetainedIndicator.setVisibility(View.VISIBLE);

            btnAction.setIconResource(android.R.drawable.ic_menu_delete);
            btnAction.setOnClickListener(v -> {
                if (listener != null) listener.onUnsubscribeClick(topic);
            });

            cardView.setOnClickListener(v -> {
                if (listener != null) listener.onTopicClick(topic);
            });

//            // Цвет карточки в зависимости от активности
//            int bgColor = active ? 0x2200AA00 : 0xFFFFFF;
//            cardView.setCardBackgroundColor(bgColor);
        }
    }
}
