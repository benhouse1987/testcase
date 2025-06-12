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
import java.util.function.Function; // Added for explicit typing if needed

import com.example.mindmap.entity.NodeStatus;
import com.example.mindmap.dto.MindMapNodeDto; // Added import
import com.example.mindmap.dto.BatchCreateNodeDto;
import com.example.mindmap.dto.RequirementInputDto;
import com.example.mindmap.dto.UpdateNodeRequest; // Added
import com.example.mindmap.entity.MindMapNodeRemark; // Added
import com.example.mindmap.mapper.MindMapNodeRemarkMapper; // Added
import com.example.mindmap.service.openai.OpenAIService;
import com.example.mindmap.service.openai.dto.GPTTestCaseStructureDto;
import com.example.mindmap.service.openai.dto.FunctionalPointDto;
import com.example.mindmap.service.openai.dto.ScenarioDto;
// import org.springframework.beans.factory.annotation.Autowired; // Already present
// import java.util.ArrayList; // Already present
// import java.util.List; // Already present
import reactor.core.publisher.Mono; // For handling async call


@Service
public class MindMapServiceImpl implements MindMapService {

    @Autowired
    private MindMapNodeMapper mindMapNodeMapper;

    @Autowired
    private MindMapNodeRemarkMapper mindMapNodeRemarkMapper; // Added

    @Autowired
    private OpenAIService openAIService;

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
            return java.util.Collections.emptyList();
        }

        QueryWrapper<MindMapNode> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("requirement_id", requirementId);
        // Optional: Add order by parent_id and then by some other criteria if needed for consistent processing
        // queryWrapper.orderByAsc("parent_id", "id"); 
        List<MindMapNode> flatList = mindMapNodeMapper.selectList(queryWrapper);

        if (flatList.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        Map<Long, MindMapNodeDto> map = flatList.stream().map(node -> {
            MindMapNodeDto dto = new MindMapNodeDto();
            dto.setId(node.getId());
            dto.setParentId(node.getParentId());
            dto.setDescription(node.getDescription());
            // dto.setRemarks(node.getRemarks()); // Old field removed
            MindMapNodeRemark remarkEntityForDto = mindMapNodeRemarkMapper.selectById(node.getId());
            dto.setRemarks(remarkEntityForDto != null ? remarkEntityForDto.getRemarksText() : null);
            dto.setRequirementId(node.getRequirementId());
            dto.setBackendDeveloper(node.getBackendDeveloper());
            dto.setFrontendDeveloper(node.getFrontendDeveloper());
            dto.setTester(node.getTester());
            dto.setRequirementReference(node.getRequirementReference());
            dto.setStatus(node.getStatus());
            dto.setIsExpanded(node.isExpanded()); // Added mapping
            dto.setHasStrikethrough(node.isHasStrikethrough()); // Added mapping
            return dto;
        }).collect(Collectors.toMap(MindMapNodeDto::getId, (MindMapNodeDto valueDto) -> valueDto));

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
    // This method is now obsolete due to remarks handling in updateNode
    // @Override
    // @Transactional
    // public MindMapNode updateNodeRemarks(Long nodeId, String remarks) { ... }

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

    @Override
    @Transactional // Ensure atomicity: if any part fails, the whole transaction rolls back.
    public MindMapNodeDto createNodesBatch(BatchCreateNodeDto batchCreateNodeDto) {
        if (batchCreateNodeDto == null) {
            throw new IllegalArgumentException("Batch creation DTO cannot be null.");
        }

        // Convert the root DTO to an entity and save it
        MindMapNode rootNodeEntity = convertBatchDtoToEntity(batchCreateNodeDto, null); // null for parentId of root
        mindMapNodeMapper.insert(rootNodeEntity); // MyBatis Plus will set the ID on rootNodeEntity after insert

        // Save remarks for the root node
        if (StringUtils.hasText(batchCreateNodeDto.getRemarks())) {
            MindMapNodeRemark newRemark = new MindMapNodeRemark(rootNodeEntity.getId(), batchCreateNodeDto.getRemarks());
            mindMapNodeRemarkMapper.insert(newRemark);
        }

        // Recursively create children
        if (batchCreateNodeDto.getChildren() != null && !batchCreateNodeDto.getChildren().isEmpty()) {
            createChildrenRecursive(batchCreateNodeDto.getChildren(), rootNodeEntity.getId(), batchCreateNodeDto.getRequirementId()); // Pass reqId for remarks
        }

        // After all nodes are created, fetch the complete structure to return as a DTO.
        return getMindMapNodeDtoWithChildren(rootNodeEntity.getId());
    }

    private MindMapNode convertBatchDtoToEntity(BatchCreateNodeDto dto, Long parentId) {
        MindMapNode entity = new MindMapNode();
        entity.setDescription(dto.getDescription());
        // entity.setRemarks(dto.getRemarks()); // Removed, handled separately
        entity.setRequirementId(dto.getRequirementId());
        entity.setBackendDeveloper(dto.getBackendDeveloper());
        entity.setFrontendDeveloper(dto.getFrontendDeveloper());
        entity.setTester(dto.getTester());
        entity.setRequirementReference(dto.getRequirementReference());
        entity.setStatus(dto.getStatus() == null ? com.example.mindmap.entity.NodeStatus.PENDING_TEST : dto.getStatus());
        entity.setParentId(parentId);
        // Note: Children are not set here as they are handled by recursive calls after parent is saved.
        return entity;
    }

    private void createChildrenRecursive(List<BatchCreateNodeDto> childDtos, Long parentId, String requirementId) { // Pass requirementId
        for (BatchCreateNodeDto childDto : childDtos) {
            // Ensure childDto also has requirementId if it can be different from parent
            // For now, assume childDto.getRequirementId() is correctly set or inherited conceptually
            MindMapNode childNodeEntity = convertBatchDtoToEntity(childDto, parentId);
            // If childDto doesn't have its own requirementId, set it from parent:
            if (!StringUtils.hasText(childNodeEntity.getRequirementId())) {
                 childNodeEntity.setRequirementId(requirementId);
            }
            mindMapNodeMapper.insert(childNodeEntity); // ID will be set on childNodeEntity

            // Save remarks for the child node
            if (StringUtils.hasText(childDto.getRemarks())) {
                MindMapNodeRemark newRemark = new MindMapNodeRemark(childNodeEntity.getId(), childDto.getRemarks());
                mindMapNodeRemarkMapper.insert(newRemark);
            }

            if (childDto.getChildren() != null && !childDto.getChildren().isEmpty()) {
                createChildrenRecursive(childDto.getChildren(), childNodeEntity.getId(), childNodeEntity.getRequirementId()); // Pass current node's reqId
            }
        }
    }

    private MindMapNodeDto getMindMapNodeDtoWithChildren(Long nodeId) {
        MindMapNode nodeEntity = mindMapNodeMapper.selectById(nodeId);
        if (nodeEntity == null) {
            return null;
        }

        MindMapNodeDto nodeDto = new MindMapNodeDto();
        nodeDto.setId(nodeEntity.getId());
        nodeDto.setParentId(nodeEntity.getParentId());
        nodeDto.setDescription(nodeEntity.getDescription());
        // nodeDto.setRemarks(nodeEntity.getRemarks()); // Old field removed
        MindMapNodeRemark remarkEntity = mindMapNodeRemarkMapper.selectById(nodeEntity.getId());
        nodeDto.setRemarks(remarkEntity != null ? remarkEntity.getRemarksText() : null);
        nodeDto.setRequirementId(nodeEntity.getRequirementId());
        nodeDto.setBackendDeveloper(nodeEntity.getBackendDeveloper());
        nodeDto.setFrontendDeveloper(nodeEntity.getFrontendDeveloper());
        nodeDto.setTester(nodeEntity.getTester());
        nodeDto.setRequirementReference(nodeEntity.getRequirementReference());
        nodeDto.setStatus(nodeEntity.getStatus());
        nodeDto.setIsExpanded(nodeEntity.isExpanded()); // Added mapping
        nodeDto.setHasStrikethrough(nodeEntity.isHasStrikethrough()); // Added mapping

        // Fetch and set children
        QueryWrapper<MindMapNode> childrenQuery = new QueryWrapper<>();
        childrenQuery.eq("parent_id", nodeId);
        List<MindMapNode> childEntities = mindMapNodeMapper.selectList(childrenQuery);

        if (childEntities != null && !childEntities.isEmpty()) {
            for (MindMapNode childEntity : childEntities) {
                nodeDto.getChildren().add(getMindMapNodeDtoWithChildren(childEntity.getId())); // Recursive call
            }
        }
        return nodeDto;
    }

    @Override
    @Transactional // This operation should be atomic
    public MindMapNodeDto generateTestCasesFromRequirement(RequirementInputDto requirementInputDto) {
        // 1. Define Prompts for OpenAI
        String systemPrompt = "You are a senior testing expert. Please write test cases in Chinese for the requirements. " +
                              "Output the test cases in a structured JSON format. The root of the JSON should be an object with a single key 'functionalPoints', " +
                              "which is an array. Each element in 'functionalPoints' should represent a distinct functional point and have " +
                              "'functionalPointName' (string) and 'testScenarios' (array) keys. Each element in 'testScenarios' should have " +
                              "'testCaseId' (string, e.g., TC-001), 'testCaseGroup' (string, e.g., Smoke Test), " +
                              "'quotedRequirementText' (string), 'prerequisites' (string), 'testSteps' (string), and 'expectedResults' (string) keys. " +
                              "Ensure 'quotedRequirementText' includes about 20 characters before and after the relevant part of the original text, with ellipses for the rest.";

        String userPromptPrefix = "Based on the following requirement, generate detailed test cases as per the specified JSON structure. " +
                                  "Divide the requirement into multiple distinct functional test object names. " +
                                  "For each functional point, write corresponding test cases. " +
                                  "Provide as many diverse test cases as possible without repetition of the same validation type. " +
                                  "The 'quotedRequirementText' should be extracted carefully from the original requirement text provided below, " +
                                  "including approximately 20 characters before and 20 characters after the key segment, using '...' for omitted parts.";

        // 2. Call OpenAIService - this is a blocking call for simplicity in this example.
        // In a fully reactive application, you'd continue the reactive chain.
        GPTTestCaseStructureDto gptResponse = openAIService.generateTestCases(
                requirementInputDto.getOriginalRequirementText(),
                systemPrompt,
                userPromptPrefix
        ).block(); // .block() makes it synchronous. Handle potential errors if Mono is empty or errors out.

        if (gptResponse == null || gptResponse.getFunctionalPoints() == null || gptResponse.getFunctionalPoints().isEmpty()) {
            // Handle error: GPT returned no usable data
            // You might throw an exception or return null/empty DTO
            throw new RuntimeException("Failed to generate test cases or received empty response from AI service.");
        }

        // 3. Transform GPT response (GPTTestCaseStructureDto) to BatchCreateNodeDto
        BatchCreateNodeDto rootBatchDto = new BatchCreateNodeDto();
        rootBatchDto.setDescription(requirementInputDto.getRequirementId() + " " + requirementInputDto.getRequirementTitle());
        rootBatchDto.setRequirementId(requirementInputDto.getRequirementId());
        // Set other root node properties if necessary, e.g., status

        List<BatchCreateNodeDto> firstLevelChildren = new ArrayList<>();
        for (FunctionalPointDto fpDto : gptResponse.getFunctionalPoints()) {
            BatchCreateNodeDto fpNodeDto = new BatchCreateNodeDto();
            fpNodeDto.setDescription(fpDto.getFunctionalPointName());
            fpNodeDto.setRequirementId(requirementInputDto.getRequirementId()); // Inherit reqId

            List<BatchCreateNodeDto> secondLevelChildren = new ArrayList<>();
            if (fpDto.getTestScenarios() != null) {
                for (ScenarioDto scenarioDto : fpDto.getTestScenarios()) {
                    BatchCreateNodeDto scenarioNodeDto = new BatchCreateNodeDto();
                    
                    // Construct rich text description for the scenario node
                    StringBuilder scenarioDescription = new StringBuilder();
                    scenarioDescription.append("### Test Scenario: ").append(fpDto.getFunctionalPointName()).append("\n"); // Markdown H3 for scenario title
                    scenarioDescription.append("**Test Case ID:** ").append(scenarioDto.getTestCaseId()).append("\n");
                    scenarioDescription.append("**Test Case Group:** ").append(scenarioDto.getTestCaseGroup()).append("\n");
                    scenarioDescription.append("**Prerequisites:** ").append(scenarioDto.getPrerequisites()).append("\n");
                    scenarioDescription.append("**Test Steps:**\n").append(scenarioDto.getTestSteps().replace("\n", "\n    ")).append("\n"); // Indent steps
                    scenarioDescription.append("**Expected Results:** ").append(scenarioDto.getExpectedResults()).append("\n");
                    
                    scenarioNodeDto.setDescription(scenarioDescription.toString());
                    scenarioNodeDto.setRequirementReference(scenarioDto.getQuotedRequirementText());
                    scenarioNodeDto.setRequirementId(requirementInputDto.getRequirementId()); // Inherit reqId
                    secondLevelChildren.add(scenarioNodeDto);
                }
            }
            fpNodeDto.setChildren(secondLevelChildren);
            firstLevelChildren.add(fpNodeDto);
        }
        rootBatchDto.setChildren(firstLevelChildren);

        // 4. Call existing batch creation logic (from previous user request)
        // This reuses the logic to save the hierarchical structure.
        return this.createNodesBatch(rootBatchDto);
    }

    @Override
    @Transactional
    public void moveNode(Long nodeToMoveId, Long newParentNodeId) {
        if (nodeToMoveId.equals(newParentNodeId)) {
            throw new IllegalArgumentException("Node cannot be moved to itself. Node ID: " + nodeToMoveId);
        }

        MindMapNode nodeToMove = mindMapNodeMapper.selectById(nodeToMoveId);
        if (nodeToMove == null) {
            throw new IllegalArgumentException("Node to move (ID: " + nodeToMoveId + ") not found.");
        }

        MindMapNode newParentNode = null;
        // The problem description implies newParentNodeId is always for an existing node.
        // If newParentNodeId could be null to move to root, the @PathVariable would need to be optional,
        // or a different endpoint would be used.
        if (newParentNodeId == null) {
             // This case might mean moving nodeToMove to become a root node.
             // However, based on the PUT /nodes/{nodeToMoveId}/move-to/{newParentNodeId} path,
             // newParentNodeId is expected to be present.
             // If moving to root is a requirement for this endpoint, it needs clarification on how newParentNodeId indicates this.
             // For now, let's assume newParentNodeId is always provided for an existing parent.
            throw new IllegalArgumentException("New parent node ID cannot be null for this operation.");
        }

        newParentNode = mindMapNodeMapper.selectById(newParentNodeId);
        if (newParentNode == null) {
            throw new IllegalArgumentException("New parent node (ID: " + newParentNodeId + ") not found.");
        }


        // Check if already a child of the new parent
        // Handles cases where parentId is null (root node) and newParentNodeId is also null (moving a root to be a root - no op)
        // or where parentId is not null and equals newParentNodeId.
        if ((nodeToMove.getParentId() == null && newParentNodeId == null) || 
            (nodeToMove.getParentId() != null && nodeToMove.getParentId().equals(newParentNodeId))) {
            // Already in the desired state, do nothing.
            // Consider logging this event if useful:
            // logger.info("Node {} is already a child of {}. No move operation needed.", nodeToMoveId, newParentNodeId);
            return;
        }
        
        // Cyclic dependency check: newParentNode cannot be a descendant of nodeToMove
        // This check is relevant only if newParentNodeId refers to an actual node.
        // If newParentNodeId was null (moving to root), this check would be skipped.
        List<MindMapNode> descendants = new ArrayList<>();
        getAllDescendants(nodeToMoveId, descendants);
        for (MindMapNode descendant : descendants) {
            if (descendant.getId().equals(newParentNodeId)) {
                throw new IllegalArgumentException(
                    "Cyclic move detected: New parent node (ID: " + newParentNodeId + ") " +
                    "is a descendant of the node to move (ID: " + nodeToMoveId + ")."
                );
            }
        }

        // All checks passed, update parentId and save
        nodeToMove.setParentId(newParentNodeId); 
        mindMapNodeMapper.updateById(nodeToMove);
        // logger.info("Node {} moved successfully under new parent {}", nodeToMoveId, newParentNodeId);
    }

    private void getAllDescendants(Long nodeId, List<MindMapNode> descendantsList) {
        QueryWrapper<MindMapNode> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", nodeId);
        List<MindMapNode> directChildren = mindMapNodeMapper.selectList(queryWrapper);

        for (MindMapNode child : directChildren) {
            descendantsList.add(child);
            getAllDescendants(child.getId(), descendantsList); // Recursive call
        }
    }

    @Override
    @Transactional
    public MindMapNodeDto updateNode(Long id, UpdateNodeRequest request) {
        MindMapNode node = mindMapNodeMapper.selectById(id);
        if (node == null) {
            throw new RuntimeException("Node not found with id: " + id); // Consider specific exception
        }

        if (request.getDescription() != null) {
            node.setDescription(request.getDescription());
        }

        // Handle remarks update
        if (request.getRemarks() != null) { // If key "remarks" is present in JSON request
            MindMapNodeRemark existingRemark = mindMapNodeRemarkMapper.selectById(node.getId());
            if (request.getRemarks().isEmpty()) {
                // If remarks are explicitly set to empty string, delete existing remark entry
                if (existingRemark != null) {
                    mindMapNodeRemarkMapper.deleteById(node.getId());
                }
            } else {
                // If remarks are provided and not empty, create or update
                if (existingRemark != null) {
                    existingRemark.setRemarksText(request.getRemarks());
                    mindMapNodeRemarkMapper.updateById(existingRemark);
                } else {
                    MindMapNodeRemark newRemark = new MindMapNodeRemark(node.getId(), request.getRemarks());
                    mindMapNodeRemarkMapper.insert(newRemark);
                }
            }
        }
        // If request.getRemarks() is null (key not in JSON), we don't touch existing remarks.

        if (request.getRequirementId() != null) {
            node.setRequirementId(request.getRequirementId());
        }
        if (request.getBackendDeveloper() != null) {
            node.setBackendDeveloper(request.getBackendDeveloper());
        }
        if (request.getFrontendDeveloper() != null) {
            node.setFrontendDeveloper(request.getFrontendDeveloper());
        }
        if (request.getTester() != null) {
            node.setTester(request.getTester());
        }
        if (request.getRequirementReference() != null) {
            node.setRequirementReference(request.getRequirementReference());
        }
        if (request.getStatus() != null) {
            node.setStatus(request.getStatus());
            // If status changes, we might need to trigger recalculations like in setNodeStatus
            // For this subtask, direct status update is performed.
            // Consider if recalculateChildrenStatusRecursive and recalculateParentStatusRecursive
            // should be called here if status is part of the update.
            // For now, keeping it simple as per initial instructions.
        }
        if (request.getIsExpanded() != null) {
            node.setExpanded(request.getIsExpanded());
        }
        if (request.getHasStrikethrough() != null) {
            node.setHasStrikethrough(request.getHasStrikethrough());
        }

        mindMapNodeMapper.updateById(node);

        // Convert entity to DTO for the response
        // Assuming MindMapNodeDto has been updated to include isExpanded and hasStrikethrough
        MindMapNodeDto updatedDto = new MindMapNodeDto();
        updatedDto.setId(node.getId());
        updatedDto.setParentId(node.getParentId());
        updatedDto.setDescription(node.getDescription());
        // updatedDto.setRemarks(node.getRemarks()); // Old field removed
        MindMapNodeRemark updatedRemarkEntity = mindMapNodeRemarkMapper.selectById(node.getId());
        updatedDto.setRemarks(updatedRemarkEntity != null ? updatedRemarkEntity.getRemarksText() : null);
        updatedDto.setRequirementId(node.getRequirementId());
        updatedDto.setBackendDeveloper(node.getBackendDeveloper());
        updatedDto.setFrontendDeveloper(node.getFrontendDeveloper());
        updatedDto.setTester(node.getTester());
        updatedDto.setRequirementReference(node.getRequirementReference());
        updatedDto.setStatus(node.getStatus());
        updatedDto.setIsExpanded(node.isExpanded()); // Use Lombok generated getter
        updatedDto.setHasStrikethrough(node.isHasStrikethrough()); // Use Lombok generated getter

        // Children are not typically loaded/returned on a simple node update response
        // unless explicitly required. The current getMindMapByRequirementId builds the tree.
        // If children are needed here, they'd need to be fetched and mapped.
        // The existing constructor for MindMapNodeDto also needs to be considered if used.
        // The current DTO constructor was updated to include these new fields.
        // However, direct field setting like above is also fine.

        return updatedDto;
    }
}
