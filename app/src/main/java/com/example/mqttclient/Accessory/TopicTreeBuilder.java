package com.example.mqttclient.Accessory;

import com.example.mqttclient.Database.AllTopicsEntity;
import com.example.mqttclient.Models.Topic;
import com.example.mqttclient.Models.TopicTreeNode;
import java.util.*;

public class TopicTreeBuilder {

    public static List<TopicTreeNode> buildTree(List<?> items, boolean isAllTopics) {
        Map<String, TopicTreeNode> nodeMap = new HashMap<>();
        List<TopicTreeNode> roots = new ArrayList<>();

        for (Object item : items) {
            String fullPath;
            Object data;
            boolean hasUnread;
            if (isAllTopics) {
                AllTopicsEntity entity = (AllTopicsEntity) item;
                fullPath = entity.getTopicName();
                data = entity;
                hasUnread = entity.hasUnread;
            } else {
                Topic topic = (Topic) item;
                fullPath = topic.getName();
                data = topic;
                hasUnread = topic.isUnread();
            }
            String[] segments = fullPath.split("/");
            StringBuilder currentPath = new StringBuilder();
            TopicTreeNode parent = null;
            for (int i = 0; i < segments.length; i++) {
                String seg = segments[i];
                if (i > 0) currentPath.append("/");
                currentPath.append(seg);
                String path = currentPath.toString();

                if (!nodeMap.containsKey(path)) {
                    TopicTreeNode node;
                    if (i == segments.length - 1) {
                        node = TopicTreeNode.createLeaf(seg, path, data);
                        node.hasUnread = hasUnread;
                    } else {
                        node = TopicTreeNode.createGroup(seg, path);
                        node.hasUnread = false;
                    }
                    nodeMap.put(path, node);
                    if (parent == null) {
                        roots.add(node);
                    } else {
                        parent.children.add(node);
                    }
                }
                parent = nodeMap.get(path);
            }
        }

        for (TopicTreeNode root : roots) {
            propagateUnreadAndSort(root);
        }
        // Сортировка корней
        roots.sort((a, b) -> {
            if (a.hasUnread != b.hasUnread) return Boolean.compare(b.hasUnread, a.hasUnread);
            return a.displayName.compareToIgnoreCase(b.displayName);
        });

        return roots;
    }

    private static void propagateUnreadAndSort(TopicTreeNode node) {
        if (node.type == TopicTreeNode.Type.LEAF) {
            return;
        }
        for (TopicTreeNode child : node.children) {
            propagateUnreadAndSort(child);
        }

        boolean groupHasUnread = false;
        for (TopicTreeNode child : node.children) {
            if (child.hasUnread) {
                groupHasUnread = true;
                break;
            }
        }
        node.hasUnread = groupHasUnread;
        node.children.sort((a, b) -> {
            if (a.hasUnread != b.hasUnread) return Boolean.compare(b.hasUnread, a.hasUnread);
            return a.displayName.compareToIgnoreCase(b.displayName);
        });
    }


    public static List<TopicTreeNode> flattenTree(List<TopicTreeNode> roots, Set<String> expandedPaths) {
        List<TopicTreeNode> flat = new ArrayList<>();
        for (TopicTreeNode node : roots) {
            flattenNode(node, flat, expandedPaths, 0);
        }
        return flat;
    }

    private static void flattenNode(TopicTreeNode node, List<TopicTreeNode> out, Set<String> expandedPaths, int depth) {
        node.depth = depth;
        out.add(node);
        if (node.type == TopicTreeNode.Type.GROUP && expandedPaths.contains(node.fullPath)) {
            for (TopicTreeNode child : node.children) {
                flattenNode(child, out, expandedPaths, depth + 1);
            }
        }
    }
}
