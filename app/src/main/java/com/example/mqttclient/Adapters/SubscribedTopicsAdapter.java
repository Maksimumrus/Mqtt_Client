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

public class SubscribedTopicsAdapter extends BaseTopicsAdapter<SubscribedTopicsAdapter.ViewHolder> {

    private Set<String> subscribedTopics;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
    private List<TopicTreeNode> originalRoots;
    private String filterQuery = "";
    private int statusFilter = 0;

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

    public void setFilter(String query, int status) {
        this.filterQuery = (query == null) ? "" : query.toLowerCase();
        this.statusFilter = status;
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
                boolean matches = true;
                if (!filterQuery.isEmpty()) {
                    boolean matchName = topic.getName().toLowerCase().contains(filterQuery);
                    boolean matchMsg = topic.getLastMessage() != null && topic.getLastMessage().toLowerCase().contains(filterQuery);
                    matches = matchName || matchMsg;
                }
                if (matches && statusFilter != 0) {
                    boolean active = topic.isActive();
                    if (statusFilter == 1 && !active) matches = false;
                    if (statusFilter == 2 && active) matches = false;
                }
                if (matches) result.add(node);
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
                unreadIndicator.setVisibility(View.GONE);
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
                    lastMessageExpanded.setText(msg != null ? msg : "нет сообщений");
                    if (topic.getLastMessageTime() != null) {
                        timestamp.setText(dateFormat.format(topic.getLastMessageTime()));
                    } else {
                        timestamp.setText("");
                    }
                    boolean active = topic.isActive();
                    statusText.setText(active ? "Активен" : "Неактивен");
                    statusText.setTextColor(active ?
                            ContextCompat.getColor(itemView.getContext(), R.color.green) :
                            ContextCompat.getColor(itemView.getContext(), R.color.red));
                    statusText.setVisibility(View.VISIBLE);
                }

                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onLeafClick(node, topic);
                });
            }
        }
    }
}

//public class SubscribedTopicsAdapter extends RecyclerView.Adapter<SubscribedTopicsAdapter.ViewHolder> {
//
//    private List<Topic> topics = new ArrayList<>();
//    private OnTopicActionListener listener;
//
//    private String filterQuery = "";
//    private int statusFilter = 0;
//
//    private List<Topic> originalTopics = new ArrayList<>();
//    private Set<Integer> expandedPositions = new HashSet<>();
//
//    public interface OnTopicActionListener {
//        void onTopicClick(Topic topic);
//        void onUnsubscribeClick(Topic topic);
//    }
//
//    public void setListener(OnTopicActionListener listener) {
//        this.listener = listener;
//    }
//
//    public void setData(List<Topic> newTopics) {
//        this.originalTopics = newTopics != null ? newTopics : new ArrayList<>();
//        applyFilters();
//    }
//
//    public void setFilter(String query, int status) {
//        this.filterQuery = (query == null) ? "" : query.toLowerCase();
//        this.statusFilter = status;
//        applyFilters();
//    }
//
//    private void applyFilters() {
//        List<Topic> filtered = new ArrayList<>();
//        for (Topic t : originalTopics) {
//            // Фильтр по поисковому запросу (название топика или последнее сообщение)
//            if (!filterQuery.isEmpty()) {
//                boolean matchName = t.getName().toLowerCase().contains(filterQuery);
//                boolean matchMsg = t.getLastMessage() != null &&
//                        t.getLastMessage().toLowerCase().contains(filterQuery);
//                if (!matchName && !matchMsg) continue;
//            }
//            // Фильтр по статусу
//            if (statusFilter == 1 && !t.isActive()) continue;
//            if (statusFilter == 2 && t.isActive()) continue;
//
//            filtered.add(t);
//        }
//        this.topics = filtered;
//        expandedPositions.clear();
//        for (int i = 0; i < topics.size(); i++) expandedPositions.add(i);
//        notifyDataSetChanged();
//    }
//
//    @NonNull
//    @Override
//    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//        View view = LayoutInflater.from(parent.getContext())
//                .inflate(R.layout.item_topic, parent, false);
//        return new ViewHolder(view);
//    }
//
//    @Override
//    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
//        Topic topic = topics.get(position);
//        boolean isExpanded = expandedPositions.contains(position);
//        holder.bind(topic, isExpanded, position);
//    }
//
//    @Override
//    public int getItemCount() {
//        return topics.size();
//    }
//
//    // Внутренний класс ViewHolder
//    class ViewHolder extends RecyclerView.ViewHolder {
//        ImageButton btnExpand, btnAction;
//        TextView topicName;
//        TextView lastMessageCompact, lastMessageExpanded;
//        TextView timestamp, statusText;
//        View retained;
//        View expandedContent;
//
//        public ViewHolder(@NonNull View itemView) {
//            super(itemView);
//            btnExpand = itemView.findViewById(R.id.btn_expand);
//            topicName = itemView.findViewById(R.id.topic_name);
//            lastMessageExpanded = itemView.findViewById(R.id.last_message_expanded);
//            timestamp = itemView.findViewById(R.id.timestamp);
//            statusText = itemView.findViewById(R.id.status);
//            btnAction = itemView.findViewById(R.id.btn_action);
//            retained = itemView.findViewById(R.id.retained);
//            expandedContent = itemView.findViewById(R.id.expanded_content);
//        }
//
//        void bind(Topic topic, boolean isExpanded, int position) {
//            topicName.setText(topic.getName());
//
//            String msg = topic.getLastMessage();
//            String displayMsg = (msg != null && !msg.isEmpty()) ? msg : "нет сообщений";
//            lastMessageCompact.setText(displayMsg);
//            lastMessageExpanded.setText(displayMsg);
//
//            // Время последнего сообщения
//            if (topic.getLastMessageTime() != null) {
//                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
//                timestamp.setText(sdf.format(topic.getLastMessageTime()));
//            } else {
//                timestamp.setText("");
//            }
//
//            retained.setBackgroundResource(topic.isHasRetained() ? R.drawable.circle_green : R.drawable.ic_circle);
//
//            boolean active = topic.isActive();
//            statusText.setText(active ? "Активен" : "Неактивен");
//            statusText.setTextColor(active ?
//                    ContextCompat.getColor(itemView.getContext(), R.color.green) :
//                    ContextCompat.getColor(itemView.getContext(), R.color.red));
//
//            // Кнопка отписки
//            btnAction.setImageResource(android.R.drawable.ic_menu_delete);
//            btnAction.setOnClickListener(v -> {
//                if (listener != null) listener.onUnsubscribeClick(topic);
//            });
//
//            btnExpand.setImageResource(isExpanded ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_right);
//            btnExpand.setOnClickListener(v -> {
//                if (isExpanded) expandedPositions.remove(position);
//                else expandedPositions.add(position);
//                notifyItemChanged(position);
//            });
//
//            if (isExpanded) {
//                expandedContent.setVisibility(View.VISIBLE);
//                lastMessageCompact.setVisibility(View.GONE);
//            } else {
//                expandedContent.setVisibility(View.GONE);
//                lastMessageCompact.setVisibility(View.VISIBLE);
//            }
//
//            // Клик по карточке
//            itemView.setOnClickListener(v -> {
//                if (listener != null) listener.onTopicClick(topic);
//            });
//        }
//    }
//}
