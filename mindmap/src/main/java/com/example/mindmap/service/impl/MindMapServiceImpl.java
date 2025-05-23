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
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map; // Added import
import java.util.stream.Collectors; // Added import

import com.example.mindmap.entity.NodeStatus;
import com.example.mindmap.dto.MindMapNodeDto; // Added import

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
    public List<MindMapNodeDto> getMindMapByRequirementId(String requirementId) {
        if (!StringUtils.hasText(requirementId)) {
            return List.of();
        }

        QueryWrapper<MindMapNode> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("requirement_id", requirementId);
        // Optional: Add order by parent_id and then by some other criteria if needed for consistent processing
        // queryWrapper.orderByAsc("parent_id", "id"); 
        List<MindMapNode> flatList = mindMapNodeMapper.selectList(queryWrapper);

        if (flatList.isEmpty()) {
            return List.of();
        }

        Map<Long, MindMapNodeDto> map = flatList.stream().map(node ->
            new MindMapNodeDto(
                node.getId(),
                node.getParentId(),
                node.getDescription(),
                node.getRemarks(),
                node.getRequirementId(),
                node.getBackendDeveloper(),
                node.getFrontendDeveloper(),
                node.getTester(),
                node.getRequirementReference(),
                node.getStatus()
            )
        ).collect(Collectors.toMap(MindMapNodeDto::getId, dto -> dto));

        List<MindMapNodeDto> tree = new ArrayList<>();
        for (MindMapNodeDto dto : map.values()) {
            if (dto.getParentId() == null) {
                tree.add(dto); // This is a root node for this requirement
            } else {
                MindMapNodeDto parentDto = map.get(dto.getParentId());
                if (parentDto != null) {
                    parentDto.getChildren().add(dto);
                } else {
                    // This case means a node has a parentId that doesn't exist in the fetched list
                    // for this requirementId. This could indicate data integrity issues or
                    // a node whose parent belongs to a different requirement (which shouldn't happen with current sample data).
                    // For now, we can add it to the root level, or log a warning.
                    // Adding to root level might be misleading. Let's assume valid parentage for now.
                    // If data is clean, this 'else' block might not be strictly necessary.
                    // A robust solution might log this or handle it based on specific requirements.
                }
            }
        }
        return tree;
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

        // Recalculation logic
        recalculateChildrenStatusRecursive(nodeId, status);
        if (node.getParentId() != null) { // Only recalculate parent if there is a parent
          recalculateParentStatusRecursive(nodeId); // Pass current node's ID
        }
        
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
        
        List<MindMapNode> updatedNodes = new ArrayList<>();
        // Keep track of parents for upward recalculation to avoid redundant checks on the same parent.
        // However, the recursive nature of recalculateParentStatusRecursive handles some of this,
        // but calling it multiple times for siblings is inefficient.
        // For simplicity in this iteration, we'll call it for each node.
        // A more optimized approach would collect unique parentIds and then iterate them.

        for (Long nodeId : nodeIds) {
            MindMapNode node = mindMapNodeMapper.selectById(nodeId);
            if (node != null) {
                if (node.getStatus() != status) { // Only process if status is changing
                    node.setStatus(status);
                    mindMapNodeMapper.updateById(node);
                    
                    recalculateChildrenStatusRecursive(nodeId, status);
                    // No need to call parent recalculation here for each node in the batch immediately.
                    // It's better to do it after all nodes in the batch have their direct status and children updated.
                }
                // We add the node to the list of results even if its status didn't change,
                // or we could choose to only add nodes that were actually modified.
                // Let's add it if it exists.
                updatedNodes.add(mindMapNodeMapper.selectById(nodeId)); // fetch fresh state
            }
        }

        // After all nodes in the batch and their children are updated,
        // trigger parent recalculation for each *unique* parent of the nodes in the batch.
        // Simpler approach for batch parent recalculation for this iteration:
        // (This replaces the Set logic above)
        for (Long nodeId : nodeIds) {
            MindMapNode node = mindMapNodeMapper.selectById(nodeId); // get fresh node
            // Check if its status was intended to be changed by this batch operation.
            // This is tricky because we don't have the original status easily here unless we query before loop.
            // Let's assume if a node is in nodeIds, its parent *might* need recalculation.
            if (node != null && node.getParentId() != null) {
                 // Only trigger if the node's status actually IS the new batch status
                 // (meaning it was either already that status, or was changed to it).
                if(node.getStatus() == status){
                     recalculateParentStatusRecursive(nodeId);
                }
            }
        }
        
        // Refresh the list of updatedNodes before returning, as parent recalculations might have affected them.
        // This is not strictly necessary if we return the nodes as they were after direct/child updates.
        // The current updatedNodes list holds nodes after their status and their children's status were set.
        // Parent recalculations are side effects that might affect nodes not even in the original list.

        return updatedNodes; // Returns nodes from the batch, after their status and children's status are updated.
    }

    // Helper Methods
    private void recalculateChildrenStatusRecursive(Long parentId, com.example.mindmap.entity.NodeStatus newStatus) {
        QueryWrapper<MindMapNode> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", parentId);
        List<MindMapNode> children = mindMapNodeMapper.selectList(queryWrapper);

        for (MindMapNode child : children) {
            child.setStatus(newStatus);
            mindMapNodeMapper.updateById(child);
            recalculateChildrenStatusRecursive(child.getId(), newStatus); // Recurse for grandchildren
        }
    }

    private void recalculateParentStatusRecursive(Long nodeId) {
        MindMapNode currentNode = mindMapNodeMapper.selectById(nodeId);
        if (currentNode == null || currentNode.getParentId() == null) {
            return; // No parent or node doesn't exist, stop recursion
        }

        Long parentId = currentNode.getParentId();
        MindMapNode parentNode = mindMapNodeMapper.selectById(parentId);
        if (parentNode == null) {
            return; // Parent node somehow doesn't exist
        }

        QueryWrapper<MindMapNode> siblingsQueryWrapper = new QueryWrapper<>();
        siblingsQueryWrapper.eq("parent_id", parentId);
        List<MindMapNode> siblings = mindMapNodeMapper.selectList(siblingsQueryWrapper);
        
        com.example.mindmap.entity.NodeStatus determinedParentStatus = com.example.mindmap.entity.NodeStatus.PENDING_TEST; // Default
        
        if (!siblings.isEmpty()) {
            boolean allSameStatus = true;
            com.example.mindmap.entity.NodeStatus firstSiblingStatus = siblings.get(0).getStatus();

            for (MindMapNode sibling : siblings) {
                if (sibling.getStatus() != firstSiblingStatus) {
                    allSameStatus = false;
                    break;
                }
            }

            if (allSameStatus) {
                determinedParentStatus = firstSiblingStatus;
            }
        }
        // If siblings list is empty (e.g. last child was deleted), what should parent status be?
        // Current logic: if all children (0 in this case) are X, parent becomes X. This is tricky.
        // Let's stick to: if there are children, and they are all X, parent is X. Otherwise PENDING_TEST.
        // If no children, parent status remains unchanged by this specific logic path.
        // This means deleting children one by one until none are left won't change parent status via this method.
        // This might need refinement based on exact desired behavior for empty children list.
        // For now, if determinedParentStatus is PENDING_TEST due to mixed statuses OR because all children matched and set it, then update.

        if (parentNode.getStatus() != determinedParentStatus) {
            parentNode.setStatus(determinedParentStatus);
            mindMapNodeMapper.updateById(parentNode);
            recalculateParentStatusRecursive(parentId); // Recurse upwards
        }
    }
}
