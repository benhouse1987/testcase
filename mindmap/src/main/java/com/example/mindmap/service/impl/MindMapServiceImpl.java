package com.example.mindmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.mindmap.entity.MindMapNode;
import com.example.mindmap.mapper.MindMapNodeMapper;
import com.example.mindmap.service.MindMapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


import java.util.List;

@Service
public class MindMapServiceImpl implements MindMapService {

    @Autowired
    private MindMapNodeMapper mindMapNodeMapper;

    @Override
    @Transactional
    public MindMapNode addNode(MindMapNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!StringUtils.hasText(node.getDescription())) {
            throw new IllegalArgumentException("Node description cannot be empty");
        }
        // Ensure status is not null if not set by default (though entity has default)
        if (node.getStatus() == null) {
            node.setStatus(com.example.mindmap.entity.NodeStatus.PENDING_TEST);
        }
        mindMapNodeMapper.insert(node);
        return node;
    }

    @Override
    public MindMapNode getNodeById(Long nodeId) {
        if (nodeId == null) {
            return null;
        }
        return mindMapNodeMapper.selectById(nodeId);
    }

    @Override
    public List<MindMapNode> getMindMapByRequirementId(String requirementId) {
        if (!StringUtils.hasText(requirementId)) {
            return List.of(); // Return an empty list if requirementId is blank
        }
        QueryWrapper<MindMapNode> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("requirement_id", requirementId);
        // Later, we might need to order them or build a tree structure.
        // For now, just returning a flat list of nodes associated with the requirementId.
        return mindMapNodeMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional
    public void deleteNodeAndChildren(Long nodeId) {
        MindMapNode node = mindMapNodeMapper.selectById(nodeId);
        if (node == null) {
            // Or throw an exception e.g., NodeNotFoundException
            return;
        }
        deleteChildrenRecursive(nodeId); // Helper to delete all children
        mindMapNodeMapper.deleteById(nodeId); // Delete the node itself
    }

    private void deleteChildrenRecursive(Long parentId) {
        QueryWrapper<MindMapNode> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", parentId);
        List<MindMapNode> children = mindMapNodeMapper.selectList(queryWrapper);

        for (MindMapNode child : children) {
            deleteChildrenRecursive(child.getId()); // Recursively delete children of this child
            mindMapNodeMapper.deleteById(child.getId()); // Delete this child
        }
    }

    @Override
    @Transactional
    public void deleteNodeKeepChildren(Long nodeId) {
        MindMapNode nodeToDelete = mindMapNodeMapper.selectById(nodeId);
        if (nodeToDelete == null) {
            // Or throw an exception
            return;
        }

        Long newParentId = nodeToDelete.getParentId(); // Children will move to this node's parent

        QueryWrapper<MindMapNode> childrenQuery = new QueryWrapper<>();
        childrenQuery.eq("parent_id", nodeId);
        List<MindMapNode> children = mindMapNodeMapper.selectList(childrenQuery);

        for (MindMapNode child : children) {
            child.setParentId(newParentId);
            mindMapNodeMapper.updateById(child);
        }

        mindMapNodeMapper.deleteById(nodeId); // Delete the node
    }

    @Override
    @Transactional
    public MindMapNode updateNodeDescription(Long nodeId, String description) {
        if (nodeId == null) {
            throw new IllegalArgumentException("Node ID cannot be null");
        }
        if (!StringUtils.hasText(description)) {
            throw new IllegalArgumentException("Description cannot be empty");
        }
        MindMapNode node = mindMapNodeMapper.selectById(nodeId);
        if (node == null) {
            // Or throw a custom NodeNotFoundException
            return null;
        }
        node.setDescription(description);
        mindMapNodeMapper.updateById(node);
        return node;
    }

    @Override
    @Transactional
    public MindMapNode updateNodeRemarks(Long nodeId, String remarks) {
        if (nodeId == null) {
            throw new IllegalArgumentException("Node ID cannot be null");
        }
        MindMapNode node = mindMapNodeMapper.selectById(nodeId);
        if (node == null) {
            // Or throw a custom NodeNotFoundException
            return null;
        }
        node.setRemarks(remarks); // Remarks can be empty or null
        mindMapNodeMapper.updateById(node);
        return node;
    }

    @Override
    @Transactional
    public MindMapNode setNodeStatus(Long nodeId, com.example.mindmap.entity.NodeStatus status) {
        if (nodeId == null) {
            throw new IllegalArgumentException("Node ID cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        MindMapNode node = mindMapNodeMapper.selectById(nodeId);
        if (node == null) {
            // Or throw a custom NodeNotFoundException
            return null;
        }
        node.setStatus(status);
        mindMapNodeMapper.updateById(node);
        // Recalculation logic will be added here later
        return node;
    }

    @Override
    @Transactional
    public List<MindMapNode> batchSetNodeStatus(List<Long> nodeIds, com.example.mindmap.entity.NodeStatus status) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new IllegalArgumentException("Node IDs list cannot be null or empty");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        
        List<MindMapNode> updatedNodes = new java.util.ArrayList<>(); // Explicitly using java.util.ArrayList
        for (Long nodeId : nodeIds) {
            // This could be optimized with a single batch update if MyBatis Plus supports it easily for varied entities,
            // but iterating is fine for now and ensures individual node object consistency.
            MindMapNode node = mindMapNodeMapper.selectById(nodeId);
            if (node != null) {
                node.setStatus(status);
                mindMapNodeMapper.updateById(node);
                updatedNodes.add(node);
                // Recalculation logic for each node will be added here later
            }
            // else: decide how to handle if a node in the batch is not found (e.g., log, skip, throw error)
        }
        return updatedNodes;
    }
}
