package com.example.mqttclient.Adapters;

import android.view.View;

import androidx.annotation.NonNull;

import com.example.mqttclient.Database.AllTopicsEntity;
import com.example.mqttclient.Models.TopicTreeNode;
import com.example.mqttclient.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AllTopicsAdapter extends BaseTopicsAdapter<AllTopicsAdapter.ViewHolder> {

    private Set<String> subscribedTopics;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
    private List<TopicTreeNode> originalRoots;
    private String filterQuery = "";

    public AllTopicsAdapter(Set<String> subscribedTopics) {
        this.subscribedTopics = subscribedTopics;
    }

    public void setSubscribedTopics(Set<String> subscribed) {
        this.subscribedTopics = subscribed;
        notifyDataSetChanged();
    }

    @Override
    public void setTreeRoots(List<TopicTreeNode> roots) {
        this.originalRoots = roots;
        super.setTreeRoots(roots);
    }

    @NonNull
    @Override
    protected ViewHolder createViewHolder(View itemView) {
        return new ViewHolder(itemView);
    }

    public void setFilter(String query) {
        this.filterQuery = (query == null) ? "" : query.toLowerCase();
        applyFilters();
    }

    private void applyFilters() {
        if (originalRoots == null) return;
        List<TopicTreeNode> filtered = filterTree(originalRoots);
        autoExpandGroups(filtered);
        super.setTreeRoots(filtered);
    }

    private List<TopicTreeNode> filterTree(List<TopicTreeNode> nodes) {
        List<TopicTreeNode> result = new ArrayList<>();
        for (TopicTreeNode node : nodes) {
            if (node.type == TopicTreeNode.Type.LEAF) {
                AllTopicsEntity entity = (AllTopicsEntity) node.data;
                if (entity.getTopicName().toLowerCase().contains(filterQuery)) {
                    result.add(node);
                }
            } else {
                List<TopicTreeNode> filteredChildren = filterTree(node.children);
                if (!filteredChildren.isEmpty()) {
                    TopicTreeNode groupCopy = TopicTreeNode.createGroup(node.displayName, node.fullPath);
                    groupCopy.children = filteredChildren;
                    result.add(groupCopy);
                }
            }
        }
        return result;
    }

    public class ViewHolder extends BaseViewHolder {
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        @Override
        public void bind(TopicTreeNode node, int position) {
            topicName.setText(node.displayName);

            if (node.type == TopicTreeNode.Type.GROUP) {
                btnExpand.setVisibility(View.VISIBLE);
                btnExpand.setImageResource(expandedGroups.contains(node.fullPath) ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_right);
                View.OnClickListener toggleListener = v -> toggleGroup(node, position);
                btnExpand.setOnClickListener(toggleListener);
                itemView.setOnClickListener(toggleListener);
                btnAction.setVisibility(View.GONE);
                expandedContent.setVisibility(View.GONE);
            } else {
                AllTopicsEntity entity = (AllTopicsEntity) node.data;
                btnExpand.setVisibility(View.VISIBLE);
                boolean isLeafExpanded = expandedLeaves.contains(node.fullPath + "_leaf");
                btnExpand.setImageResource(isLeafExpanded ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_right);
                btnExpand.setOnClickListener(v -> toggleLeaf(node, position));

                boolean isSubscribed = subscribedTopics != null && subscribedTopics.contains(entity.topicName);
                btnAction.setImageResource(isSubscribed ? R.drawable.ic_check : R.drawable.ic_add);
                btnAction.setOnClickListener(v -> {
                    if (listener != null) listener.onActionClick(node, entity);
                });
                btnAction.setVisibility(View.VISIBLE);

                expandedContent.setVisibility(isLeafExpanded ? View.VISIBLE : View.GONE);
                if (isLeafExpanded) {
                    String msg = entity.getLastMessage();
                    lastMessageExpanded.setText(msg != null ? msg : "Нет сообщений");
                    long time = entity.getLastMessageTimestamp() > 0 ? entity.getLastMessageTimestamp() : entity.lastSeenTimestamp;
                    if (time > 0) {
                        timestamp.setText(dateFormat.format(time));
                    } else {
                        timestamp.setText("");
                    }
                }

                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onLeafClick(node, entity);
                });
            }
        }
    }
}