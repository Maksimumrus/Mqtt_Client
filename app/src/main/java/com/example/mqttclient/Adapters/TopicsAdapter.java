package com.example.mqttclient.Adapters;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.mqttclient.Accessory.TopicTreeBuilder;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.Models.TopicTreeNode;
import com.example.mqttclient.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TopicsAdapter extends BaseTopicsAdapter<TopicsAdapter.ViewHolder> {

    private Set<String> subscribedTopics;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
    private List<TopicTreeNode> originalRoots;
    private String filterQuery = "";

    @Override
    public void setTreeRoots(List<TopicTreeNode> roots) {
        this.originalRoots = roots;
        super.setTreeRoots(roots);
    }

    public void setData(List<Topic> topics) {
        if (topics == null) topics = new ArrayList<>();
        originalRoots = TopicTreeBuilder.buildTree(topics, false);
        if (originalRoots == null) originalRoots = new ArrayList<>();
        autoExpandGroups(originalRoots);
        super.setTreeRoots(originalRoots);
    }

    public void setFilter(String query) {
        this.filterQuery = (query == null) ? "" : query.toLowerCase();
        applyFilters();
    }

    public void initExpandedLeaves(Set<String> leaves) {
        expandedLeaves.clear();
        if (leaves.isEmpty()) {
            if (originalRoots != null) {
                addAllLeaves(originalRoots);
            }
        } else {
            expandedLeaves.addAll(leaves);
        }
        rebuildFlatList();
    }

    private void addAllLeaves(List<TopicTreeNode> nodes) {
        if (nodes == null) return;
        for (TopicTreeNode node : nodes) {
            if (node.type == TopicTreeNode.Type.LEAF) {
                expandedLeaves.add(node.fullPath + "_leaf");
            } else {
                addAllLeaves(node.children);
            }
        }
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
                Topic topic = (Topic) node.data;
                if (topic.getName().toLowerCase().contains(filterQuery)) {
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

    @NonNull
    @Override
    protected ViewHolder createViewHolder(View itemView) {
        return new ViewHolder(itemView);
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
                unreadIndicator.setVisibility(node.hasUnread ? View.VISIBLE : View.GONE);
                expandedContent.setVisibility(View.GONE);
            } else {
                Topic topic = (Topic) node.data;
                btnExpand.setVisibility(View.VISIBLE);
                boolean isLeafExpanded = expandedLeaves.contains(node.fullPath + "_leaf");
                btnExpand.setImageResource(isLeafExpanded ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_right);
                btnExpand.setOnClickListener(v -> toggleLeaf(node, position));

                unreadIndicator.setVisibility(topic.isUnread() ? View.VISIBLE : View.GONE);

                btnAction.setImageResource(android.R.drawable.ic_menu_delete);
                btnAction.setOnClickListener(v -> {
                    if (listener != null) listener.onActionClick(node, topic);
                });
                btnAction.setVisibility(View.VISIBLE);

                expandedContent.setVisibility(isLeafExpanded ? View.VISIBLE : View.GONE);
                if (isLeafExpanded) {
                    String msg = topic.getLastMessage();
                    lastMessageExpanded.setText(msg != null ? msg : "Нет сообщений");
                    if (topic.getLastMessageTime() != null) {
                        timestamp.setText(dateFormat.format(topic.getLastMessageTime()));
                    } else {
                        timestamp.setText("");
                    }
                }

                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onLeafClick(node, topic);
                });
            }
        }
    }
}