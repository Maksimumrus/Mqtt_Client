package com.example.mqttclient.Models;

import java.util.ArrayList;
import java.util.List;

public class TopicTreeNode {
    public enum Type {GROUP, LEAF}

    public Type type;
    public String displayName;
    public String fullPath;
    public Object data;
    public List<TopicTreeNode> children = new ArrayList<>();
    public boolean isExpanded;
    public int depth;

    public static TopicTreeNode createGroup(String displayName, String fullPath) {
        TopicTreeNode node = new TopicTreeNode();
        node.type = Type.GROUP;
        node.displayName = displayName;
        node.fullPath = fullPath;
        node.data = null;
        return node;
    }

    public static TopicTreeNode createLeaf(String displayName, String fullPath, Object data) {
        TopicTreeNode node = new TopicTreeNode();
        node.type = Type.LEAF;
        node.displayName = displayName;
        node.fullPath = fullPath;
        node.data = data;
        return node;
    }
}
