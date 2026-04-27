package com.example.mqttclient.Adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mqttclient.Database.AllTopicsEntity;
import com.example.mqttclient.R;
import com.google.android.material.button.MaterialButton;

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
    private Set<Integer> expandedPositions = new HashSet<>();

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
        expandedPositions.clear();
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
        boolean isExpanded = expandedPositions.contains(position);
        holder.bind(topic, isExpanded, position);
    }

    @Override
    public int getItemCount() {
        return topics.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageButton btnExpand, btnAction;
        TextView topicName;
        TextView lastMessageCompact, lastMessageExpanded;
        TextView timestamp, statusText;
        ImageView ivRetained;
        View expandedContent;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            btnExpand = itemView.findViewById(R.id.btn_expand);
            topicName = itemView.findViewById(R.id.topic_name);
            lastMessageCompact = itemView.findViewById(R.id.last_message_compact);
            lastMessageExpanded = itemView.findViewById(R.id.last_message_expanded);
            timestamp = itemView.findViewById(R.id.timestamp);
            statusText = itemView.findViewById(R.id.status);
            btnAction = itemView.findViewById(R.id.btn_action);
            ivRetained = itemView.findViewById(R.id.iv_retained);
            expandedContent = itemView.findViewById(R.id.expanded_content);
        }

        void bind(AllTopicsEntity topic, boolean isExpanded, int position) {
            topicName.setText(topic.topicName);

            String msg = topic.getLastMessage();
            String displayMsg = (msg != null && !msg.isEmpty()) ? msg : "нет сообщений";
            lastMessageCompact.setText(displayMsg);
            lastMessageExpanded.setText(displayMsg);

            long time = topic.getLastMessageTimestamp() > 0 ? topic.getLastMessageTimestamp() : topic.lastSeenTimestamp;
            timestamp.setText(dateFormat.format(time));

            if (topic.isHasRetained()) {
                ivRetained.setImageResource(R.drawable.ic_bookmark_filled);
                ivRetained.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.orange_500));
            } else {
                ivRetained.setImageResource(R.drawable.ic_bookmark_outline);
                ivRetained.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.gray_400));
            }

            boolean isSubscribed = subscribedTopics.contains(topic.topicName);
            btnAction.setImageResource(isSubscribed ? R.drawable.ic_check : R.drawable.ic_add);
            btnAction.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.black));
            btnAction.setOnClickListener(v -> {
                if (listener == null) return;
                if (isSubscribed) listener.onRemoveFromFavoritesClick(topic.topicName);
                else listener.onAddToFavoritesClick(topic.topicName);
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
                statusText.setVisibility(View.GONE);
            } else {
                expandedContent.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTopicClick(topic.topicName);
            });
        }
    }
}