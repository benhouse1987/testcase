package com.example.mindmap.service;

import com.example.mindmap.entity.MindMapNode;
import java.util.List;

public interface MindMapService {
    MindMapNode addNode(MindMapNode node);
    MindMapNode getNodeById(Long nodeId);
    List<MindMapNode> getMindMapByRequirementId(String requirementId); // Returning a list for now

    // ... existing methods ...
    void deleteNodeAndChildren(Long nodeId);
    void deleteNodeKeepChildren(Long nodeId);

    // ... existing methods ...
    MindMapNode updateNodeDescription(Long nodeId, String description);
    MindMapNode updateNodeRemarks(Long nodeId, String remarks);

    // ... existing methods ...
    MindMapNode setNodeStatus(Long nodeId, com.example.mindmap.entity.NodeStatus status);
    List<MindMapNode> batchSetNodeStatus(List<Long> nodeIds, com.example.mindmap.entity.NodeStatus status);
}
