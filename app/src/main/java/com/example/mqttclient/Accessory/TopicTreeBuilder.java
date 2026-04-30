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
            if (isAllTopics) {
                AllTopicsEntity entity = (AllTopicsEntity) item;
                fullPath = entity.getTopicName();
                data = entity;
            } else {
                Topic topic = (Topic) item;
                fullPath = topic.getName();
                data = topic;
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
                        // лист (сам топик)
                        node = TopicTreeNode.createLeaf(seg, path, data);
                    } else {
                        // промежуточная группа
                        node = TopicTreeNode.createGroup(seg, path);
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

        // Сортировка детей по отображаемому имени
        for (TopicTreeNode node : nodeMap.values()) {
            node.children.sort(Comparator.comparing(n -> n.displayName));
        }
        roots.sort(Comparator.comparing(r -> r.displayName));
        return roots;
    }

    /**
     * Преобразует дерево в плоский список для RecyclerView с учётом раскрытых групп.
     */
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
