package com.example.mqttclient.Adapters;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SubscribedTopicsAdapter extends RecyclerView.Adapter<SubscribedTopicsAdapter.ViewHolder> {

    private List<Topic> topics = new ArrayList<>();
    private OnTopicActionListener listener;

    private String filterQuery = "";
    private int statusFilter = 0;

    private List<Topic> originalTopics = new ArrayList<>();
    private Set<Integer> expandedPositions = new HashSet<>();

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
        expandedPositions.clear();
        for (int i = 0; i < topics.size(); i++) expandedPositions.add(i);
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
        boolean isExpanded = expandedPositions.contains(position);
        holder.bind(topic, isExpanded, position);
    }

    @Override
    public int getItemCount() {
        return topics.size();
    }

    // Внутренний класс ViewHolder
    class ViewHolder extends RecyclerView.ViewHolder {
        ImageButton btnExpand, btnAction;
        TextView topicName;
        TextView lastMessageCompact, lastMessageExpanded;
        TextView timestamp, statusText;
        View retained;
        View expandedContent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            btnExpand = itemView.findViewById(R.id.btn_expand);
            topicName = itemView.findViewById(R.id.topic_name);
            lastMessageCompact = itemView.findViewById(R.id.last_message_compact);
            lastMessageExpanded = itemView.findViewById(R.id.last_message_expanded);
            timestamp = itemView.findViewById(R.id.timestamp);
            statusText = itemView.findViewById(R.id.status);
            btnAction = itemView.findViewById(R.id.btn_action);
            retained = itemView.findViewById(R.id.retained);
            expandedContent = itemView.findViewById(R.id.expanded_content);
        }

        void bind(Topic topic, boolean isExpanded, int position) {
            topicName.setText(topic.getName());

            String msg = topic.getLastMessage();
            String displayMsg = (msg != null && !msg.isEmpty()) ? msg : "нет сообщений";
            lastMessageCompact.setText(displayMsg);
            lastMessageExpanded.setText(displayMsg);

            // Время последнего сообщения
            if (topic.getLastMessageTime() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
                timestamp.setText(sdf.format(topic.getLastMessageTime()));
            } else {
                timestamp.setText("");
            }

            retained.setBackgroundResource(topic.isHasRetained() ? R.drawable.circle_green : R.drawable.ic_circle);

            boolean active = topic.isActive();
            statusText.setText(active ? "Активен" : "Неактивен");
            statusText.setTextColor(active ?
                    ContextCompat.getColor(itemView.getContext(), R.color.green) :
                    ContextCompat.getColor(itemView.getContext(), R.color.red));

            // Кнопка отписки
            btnAction.setImageResource(android.R.drawable.ic_menu_delete);
            btnAction.setOnClickListener(v -> {
                if (listener != null) listener.onUnsubscribeClick(topic);
            });

            btnExpand.setImageResource(isExpanded ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_right);
            btnExpand.setOnClickListener(v -> {
                if (isExpanded) expandedPositions.remove(position);
                else expandedPositions.add(position);
                notifyItemChanged(position);
            });

            if (isExpanded) {
                expandedContent.setVisibility(View.VISIBLE);
                lastMessageCompact.setVisibility(View.GONE);
            } else {
                expandedContent.setVisibility(View.GONE);
                lastMessageCompact.setVisibility(View.VISIBLE);
            }

            // Клик по карточке
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTopicClick(topic);
            });
        }
    }
}
