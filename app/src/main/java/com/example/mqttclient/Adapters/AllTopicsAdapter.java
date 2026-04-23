package com.example.mqttclient.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mqttclient.Database.AllTopicsEntity;
import com.example.mqttclient.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AllTopicsAdapter extends RecyclerView.Adapter<AllTopicsAdapter.ViewHolder> {
    private List<AllTopicsEntity> topics = new ArrayList<>();
    private Set<String> subscribedTopics = new HashSet<>();
    private OnTopicActionListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());

    private String filterQuery = "";
    private List<AllTopicsEntity> originalTopics = new ArrayList<>();

    public interface OnTopicActionListener {
        void onTopicClick(String topicName);
        void onAddToFavoritesClick(String topicName);
        void onRemoveFromFavoritesClick(String topicName);
    }

    public void setListener(OnTopicActionListener listener) {
        this.listener = listener;
    }

    public void setTopics(List<AllTopicsEntity> newTopics) {
        this.originalTopics = newTopics != null ? newTopics : new ArrayList<>();
        applyFilters();
//        notifyDataSetChanged();
    }

    public void setFilter(String query) {
        this.filterQuery = (query == null) ? "" : query.toLowerCase();
        applyFilters();
    }

    private void applyFilters() {
        List<AllTopicsEntity> filtered = new ArrayList<>();
        for (AllTopicsEntity t : originalTopics) {
            if (!filterQuery.isEmpty()) {
                boolean matchName = t.getTopicName().toLowerCase().contains(filterQuery);
                if (!matchName) continue;
            }
            filtered.add(t);
        }
        this.topics = filtered;
        notifyDataSetChanged();
    }

    public void setSubscribedTopics(Set<String> subscribed) {
        this.subscribedTopics = subscribed != null ? subscribed : new HashSet<>();
        notifyDataSetChanged();
    }

    public void updateSubscriptionStatus(String topicName, boolean isSubscribed) {
        if (isSubscribed) {
            subscribedTopics.add(topicName);
        } else {
            subscribedTopics.remove(topicName);
        }
        int position = findPosition(topicName);
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

    private int findPosition(String topicName) {
        for (int i = 0; i < topics.size(); i++) {
            if (topics.get(i).topicName.equals(topicName)) {
                return i;
            }
        }
        return -1;
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
        AllTopicsEntity topic = topics.get(position);
        holder.bind(topic);
    }

    @Override
    public int getItemCount() {
        return topics.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView topicName, lastMessage, timestamp, status;
        ImageButton btnAction;
        ImageView ivRetainedIndicator;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            topicName = itemView.findViewById(R.id.topic_name);
            lastMessage = itemView.findViewById(R.id.last_message);
            timestamp = itemView.findViewById(R.id.timestamp);
            status = itemView.findViewById(R.id.status);
            btnAction = itemView.findViewById(R.id.btn_action);
            ivRetainedIndicator = itemView.findViewById(R.id.iv_retained_indicator);
        }

        void bind(AllTopicsEntity topic) {
            topicName.setText(topic.topicName);
            lastMessage.setText("Последнее: " + dateFormat.format(topic.lastSeenTimestamp));
            timestamp.setVisibility(View.GONE);
            status.setVisibility(View.GONE);

            if (topic.isHasRetained()) {
                ivRetainedIndicator.setImageResource(R.drawable.ic_check);
            } else {
                ivRetainedIndicator.setImageResource(R.drawable.ic_temporary);
            }
            ivRetainedIndicator.setVisibility(View.VISIBLE);

            boolean isSubscribed = subscribedTopics.contains(topic.topicName);
            btnAction.setImageResource(isSubscribed ? android.R.drawable.checkbox_on_background : android.R.drawable.ic_input_add);
            if (isSubscribed) {
                btnAction.setOnClickListener(v -> {
                    if (listener != null) listener.onRemoveFromFavoritesClick(topic.topicName);
                });
            } else {
                btnAction.setOnClickListener(v -> {
                    if (listener != null) listener.onAddToFavoritesClick(topic.topicName);
                });
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTopicClick(topic.topicName);
            });
        }
    }
}