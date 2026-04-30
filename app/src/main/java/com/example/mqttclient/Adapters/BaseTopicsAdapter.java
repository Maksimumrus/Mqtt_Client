package com.example.mqttclient.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mqttclient.Models.TopicTreeNode;
import com.example.mqttclient.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseTopicsAdapter <VH extends BaseTopicsAdapter.BaseViewHolder> extends RecyclerView.Adapter<VH> {
    protected List<TopicTreeNode> flatList = new ArrayList<>();
    protected List<TopicTreeNode> rootNodes = new ArrayList<>();
    protected Set<String> expandedGroups = new HashSet<>();
    protected Set<String> expandedLeaves = new HashSet<>();

    protected OnTreeNodeClickListener listener;

    public interface OnTreeNodeClickListener {
        void onLeafClick(TopicTreeNode node, Object data);
        void onGroupClick(TopicTreeNode node);
        void onActionClick(TopicTreeNode node, Object data); // подписка/отписка/удаление
    }

    public void setListener(OnTreeNodeClickListener listener) {
        this.listener = listener;
    }

    public void setTreeRoots(List<TopicTreeNode> roots) {
        this.rootNodes = roots != null ? roots : new ArrayList<>();
        rebuildFlatList();
    }

    protected void rebuildFlatList() {
        flatList.clear();
        flattenTree(rootNodes, 0);
        notifyDataSetChanged();
    }

    private void flattenTree(List<TopicTreeNode> nodes, int depth) {
        for (TopicTreeNode node : nodes) {
            node.depth = depth;
            flatList.add(node);
            if (node.type == TopicTreeNode.Type.GROUP && expandedGroups.contains(node.fullPath)) {
                flattenTree(node.children, depth + 1);
            }
        }
    }

    public void toggleGroup(TopicTreeNode node, int position) {
        if (node.type != TopicTreeNode.Type.GROUP) return;
        if (expandedGroups.contains(node.fullPath)) {
            expandedGroups.remove(node.fullPath);
        } else {
            expandedGroups.add(node.fullPath);
        }
        rebuildFlatList();
        if (listener != null) listener.onGroupClick(node);
    }

    public void toggleLeaf(TopicTreeNode node, int position) {
        if (node.type != TopicTreeNode.Type.LEAF) return;
        String key = node.fullPath + "_leaf";
        if (expandedLeaves.contains(key)) {
            expandedLeaves.remove(key);
        } else {
            expandedLeaves.add(key);
        }
        notifyItemChanged(position);
    }

    public void saveExpandedState(Set<String> groups, Set<String> leaves) {
        expandedGroups.clear();
        expandedGroups.addAll(groups);
        expandedLeaves.clear();
        expandedLeaves.addAll(leaves);
        rebuildFlatList();
    }

    public Set<String> getExpandedGroups() { return new HashSet<>(expandedGroups); }
    public Set<String> getExpandedLeaves() { return new HashSet<>(expandedLeaves); }

    public void autoExpandGroups(List<TopicTreeNode> nodes) {
        for (TopicTreeNode node : nodes) {
            if (node.type == TopicTreeNode.Type.GROUP && !node.children.isEmpty()) {
                expandedGroups.add(node.fullPath);
                autoExpandGroups(node.children);
            }
        }
    }

    @Override
    public int getItemCount() {
        return flatList.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_topic, parent, false);
        return createViewHolder(view);
    }

    protected abstract VH createViewHolder(View itemView);

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TopicTreeNode node = flatList.get(position);
        int marginDp = node.depth * 16;
        int marginPx = (int) (marginDp * holder.itemView.getContext().getResources().getDisplayMetrics().density);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        params.leftMargin = marginPx;
        holder.itemView.setLayoutParams(params);

        holder.bind(node, position);
    }

    public abstract static class BaseViewHolder extends RecyclerView.ViewHolder {
        public ImageButton btnExpand;
        public TextView topicName;
        public ImageButton btnAction;
        public ImageView unreadIndicator;
        public View expandedContent;
        public TextView lastMessageExpanded, timestamp, statusText;

        public BaseViewHolder(@NonNull View itemView) {
            super(itemView);
            btnExpand = itemView.findViewById(R.id.btn_expand);
            topicName = itemView.findViewById(R.id.topic_name);
            btnAction = itemView.findViewById(R.id.btn_action);
            unreadIndicator = itemView.findViewById(R.id.unread_indicator);
            expandedContent = itemView.findViewById(R.id.expanded_content);
            lastMessageExpanded = itemView.findViewById(R.id.last_message_expanded);
            timestamp = itemView.findViewById(R.id.timestamp);
            statusText = itemView.findViewById(R.id.status);
        }

        public abstract void bind(TopicTreeNode node, int position);
    }
}
