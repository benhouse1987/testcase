package com.example.mindmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.mindmap.dto.BatchCreateNodeDto;
import com.example.mindmap.dto.MindMapNodeDto;
import com.example.mindmap.dto.RequirementInputDto;
import com.example.mindmap.dto.UpdateNodeRequest;
import com.example.mindmap.entity.MindMapNode;
import com.example.mindmap.entity.NodeStatus;
import com.example.mindmap.mapper.MindMapNodeMapper;
import com.example.mindmap.service.MindMapService;
import com.example.mindmap.service.openai.OpenAIService;
import com.example.mindmap.service.openai.dto.FunctionalPointDto;
import com.example.mindmap.service.openai.dto.GPTTestCaseStructureDto;
import com.example.mindmap.service.openai.dto.ScenarioDto;
import com.example.mindmap.exception.InvalidOperationException; // Added
import com.example.mindmap.exception.ResourceNotFoundException; // Added
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class MindMapServiceImpl implements MindMapService {

    private static final Logger logger = LoggerFactory.getLogger(MindMapServiceImpl.class);

    @Autowired
    private MindMapNodeMapper mindMapNodeMapper;

    @Autowired
    private OpenAIService openAIService;

    @Override
    @Transactional
    public MindMapNode addNode(MindMapNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
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
        List<MindMapNode> flatList = mindMapNodeMapper.selectList(queryWrapper);

        if (flatList.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        Map<Long, MindMapNodeDto> map = flatList.stream().map(node -> {
            MindMapNodeDto dto = new MindMapNodeDto();
            BeanUtils.copyProperties(node, dto); // Use BeanUtils for mapping
            return dto;
        }).collect(Collectors.toMap(MindMapNodeDto::getId, (MindMapNodeDto valueDto) -> valueDto));

        List<MindMapNodeDto> tree = new ArrayList<>();
        for (MindMapNodeDto dto : map.values()) {
            if (dto.getParentId() == null) {
                tree.add(dto);
            } else {
                MindMapNodeDto parentDto = map.get(dto.getParentId());
                if (parentDto != null) {
                    parentDto.getChildren().add(dto);
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
            throw new InvalidOperationException("Node ID cannot be null");
        }
        if (!StringUtils.hasText(description)) {
            throw new InvalidOperationException("Description cannot be empty");
        }
        MindMapNode node = mindMapNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new ResourceNotFoundException("Node not found with ID: " + nodeId);
        }
        node.setDescription(description);
        mindMapNodeMapper.updateById(node);
        return node;
    }

    @Override
    @Transactional
    public MindMapNode updateNodeRemarks(Long nodeId, String remarks) {
        if (nodeId == null) {
            throw new InvalidOperationException("Node ID cannot be null");
        }
        MindMapNode node = mindMapNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new ResourceNotFoundException("Node not found with ID: " + nodeId);
        }
        node.setRemarks(remarks); // Remarks can be empty or null
        mindMapNodeMapper.updateById(node);
        return node;
    }

    @Override
    @Transactional
    public MindMapNode setNodeStatus(Long nodeId, com.example.mindmap.entity.NodeStatus status) {
        if (nodeId == null) {
            throw new InvalidOperationException("Node ID cannot be null");
        }
        if (status == null) {
            throw new InvalidOperationException("Status cannot be null");
        }
        MindMapNode node = mindMapNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new ResourceNotFoundException("Node not found with ID: " + nodeId);
        }
        node.setStatus(status);
        mindMapNodeMapper.updateById(node);

        // Recalculation logic
        recalculateChildrenStatusRecursive(nodeId, status);
        if (node.getParentId() != null) { // Only recalculate parent if there is a parent
          recalculateParentStatusRecursive(nodeId); // Pass current node's ID
        }
        
        return mindMapNodeMapper.selectById(nodeId); // Return fresh node data
    }

    @Override
    @Transactional
    public List<MindMapNode> batchSetNodeStatus(List<Long> nodeIds, com.example.mindmap.entity.NodeStatus status) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new InvalidOperationException("Node IDs list cannot be null or empty");
        }
        if (status == null) {
            throw new InvalidOperationException("Status cannot be null");
        }
        
        List<MindMapNode> updatedNodesResult = new ArrayList<>();

        // First pass: Update status of specified nodes and their children
        for (Long nodeId : nodeIds) {
            MindMapNode node = mindMapNodeMapper.selectById(nodeId);
            if (node != null) {
                // Only update and recurse if status is actually changing
                if (node.getStatus() != status) {
                    node.setStatus(status);
                    mindMapNodeMapper.updateById(node);
                    // Propagate status change downwards to all children
                    recalculateChildrenStatusRecursive(nodeId, status);
                }
            }
        }

        // Second pass: Recalculate parent statuses
        // Collect unique parent IDs of the nodes that were part of the batch and whose status matches the target status
        List<Long> distinctParentIdsToRecalculate = nodeIds.stream()
            .map(nodeId -> mindMapNodeMapper.selectById(nodeId)) // Fetch fresh node state
            .filter(node -> node != null && node.getParentId() != null && node.getStatus() == status)
            .map(MindMapNode::getParentId)
            .distinct()
            .collect(Collectors.toList());

        for (Long parentId : distinctParentIdsToRecalculate) {
            // Find a child of this parent that was part of the batch (and has the new status)
            // to initiate the upward recalculation from that child's perspective.
            // Any child of this parent that was in the batch and now has the target status will do.
            nodeIds.stream()
                .map(nid -> mindMapNodeMapper.selectById(nid)) // Fetch fresh node
                .filter(n -> n != null && parentId.equals(n.getParentId()) && n.getStatus() == status)
                .findFirst() // Find any such child
                .ifPresent(childNode -> recalculateParentStatusRecursive(childNode.getId()));
        }

        // Third pass: Collect fresh node data for the response
        for (Long nodeId : nodeIds) {
            MindMapNode freshNode = mindMapNodeMapper.selectById(nodeId);
            if (freshNode != null) {
                updatedNodesResult.add(freshNode);
            }
        }
        return updatedNodesResult;
    }


    // Helper Methods for status recalculation
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

    // childNodeId is the ID of a node whose status might have changed, triggering parent check
    private void recalculateParentStatusRecursive(Long childNodeId) {
        MindMapNode childNode = mindMapNodeMapper.selectById(childNodeId);
        // If childNode is null (e.g. deleted) or has no parent, stop.
        if (childNode == null || childNode.getParentId() == null) {
            return;
        }

        Long parentId = childNode.getParentId();
        MindMapNode parentNode = mindMapNodeMapper.selectById(parentId);
        if (parentNode == null) {
            return; // Parent node somehow doesn't exist, stop.
        }

        QueryWrapper<MindMapNode> siblingsQueryWrapper = new QueryWrapper<>();
        siblingsQueryWrapper.eq("parent_id", parentId);
        List<MindMapNode> siblings = mindMapNodeMapper.selectList(siblingsQueryWrapper); // Includes the childNode itself
        
        if (siblings.isEmpty()) {
            // This case should ideally not happen if childNode has this parentId.
            // If it does, it implies an inconsistent state or the child was just deleted.
            // If parent has no children left, its status determination might follow different rules.
            // For now, if no siblings found (which is odd if childNode exists), do not change parent status.
            return;
        }
        
        // Determine the collective status of children
        NodeStatus determinedParentStatus = siblings.get(0).getStatus(); // Assume first child's status
        boolean allSameStatus = true;
        for (int i = 1; i < siblings.size(); i++) {
            if (siblings.get(i).getStatus() != determinedParentStatus) {
                allSameStatus = false;
                break;
            }
        }

        if (allSameStatus) {
            // If all children have the same status, parent takes that status
            if (parentNode.getStatus() != determinedParentStatus) {
                parentNode.setStatus(determinedParentStatus);
                mindMapNodeMapper.updateById(parentNode);
                recalculateParentStatusRecursive(parentId); // Recurse upwards (pass parent's ID as the new 'child' for next level)
            }
        } else { // Children have mixed statuses
            // If children have mixed statuses, parent becomes PENDING_TEST
            if (parentNode.getStatus() != NodeStatus.PENDING_TEST) {
                parentNode.setStatus(NodeStatus.PENDING_TEST);
                mindMapNodeMapper.updateById(parentNode);
                recalculateParentStatusRecursive(parentId); // Recurse upwards
            }
        }
    }

    @Override
    @Transactional // Ensure atomicity: if any part fails, the whole transaction rolls back.
    public MindMapNodeDto createNodesBatch(BatchCreateNodeDto batchCreateNodeDto) {
        if (batchCreateNodeDto == null) {
            throw new InvalidOperationException("Batch creation DTO cannot be null.");
        }

        // 如果第一层（根节点）有id，先删除该id对应的整棵节点树
        if (batchCreateNodeDto.getId() != null) {
            MindMapNode existingNode = mindMapNodeMapper.selectById(batchCreateNodeDto.getId());
            if (existingNode != null) {
                deleteNodeAndChildren(batchCreateNodeDto.getId());
            }
        }

        // Convert the root DTO to an entity and save it
        MindMapNode rootNodeEntity = convertBatchDtoToEntity(batchCreateNodeDto, null); // null for parentId of root
        if (rootNodeEntity.getId() != null) {
            // 检查节点是否存在，如果存在则更新，否则插入
            MindMapNode existingNode = mindMapNodeMapper.selectById(rootNodeEntity.getId());
            if (existingNode != null) {
                mindMapNodeMapper.updateById(rootNodeEntity);
            } else {
                mindMapNodeMapper.insert(rootNodeEntity);
            }
        } else {
            // 如果没有提供id，使用普通insert让数据库自动生成id
            mindMapNodeMapper.insert(rootNodeEntity);
        }

        // Recursively create children
        if (batchCreateNodeDto.getChildren() != null && !batchCreateNodeDto.getChildren().isEmpty()) {
            createChildrenRecursive(batchCreateNodeDto.getChildren(), rootNodeEntity.getId());
        }

        // After all nodes are created, fetch the complete structure to return as a DTO.
        return getMindMapNodeDtoWithChildren(rootNodeEntity.getId());
    }

    private MindMapNode convertBatchDtoToEntity(BatchCreateNodeDto dto, Long parentId) {
        MindMapNode entity = new MindMapNode();
        // 如果DTO中提供了id，则使用指定的id
        if (dto.getId() != null) {
            entity.setId(dto.getId());
        }
        // Manually map fields from DTO to ensure all desired fields are set
        entity.setDescription(dto.getDescription());
        entity.setRemarks(dto.getRemarks());
        entity.setRequirementId(dto.getRequirementId());
        entity.setBackendDeveloper(dto.getBackendDeveloper());
        entity.setFrontendDeveloper(dto.getFrontendDeveloper());
        entity.setTester(dto.getTester());
        entity.setRequirementReference(dto.getRequirementReference());
        entity.setStatus(dto.getStatus() == null ? com.example.mindmap.entity.NodeStatus.PENDING_TEST : dto.getStatus());
        // isExpanded and hasStrikethrough are not in BatchCreateNodeDto, will take entity defaults
        entity.setParentId(parentId);
        // 新增映射：AI 创建标记
        entity.setIsAiGenerated(dto.getIsAiGenerated());
        return entity;
    }

    private void createChildrenRecursive(List<BatchCreateNodeDto> childDtos, Long parentId) {
        for (BatchCreateNodeDto childDto : childDtos) {
            MindMapNode childNodeEntity = convertBatchDtoToEntity(childDto, parentId);
            if (childNodeEntity.getId() != null) {
                // 检查节点是否存在，如果存在则更新，否则插入
                MindMapNode existingChildNode = mindMapNodeMapper.selectById(childNodeEntity.getId());
                if (existingChildNode != null) {
                    mindMapNodeMapper.updateById(childNodeEntity);
                } else {
                    mindMapNodeMapper.insert(childNodeEntity);
                }
            } else {
                // 如果没有提供id，使用普通insert让数据库自动生成id
                mindMapNodeMapper.insert(childNodeEntity);
            }

            if (childDto.getChildren() != null && !childDto.getChildren().isEmpty()) {
                createChildrenRecursive(childDto.getChildren(), childNodeEntity.getId());
            }
        }
    }

    // Helper to fetch a node and its children as DTO, recursively
    private MindMapNodeDto getMindMapNodeDtoWithChildren(Long nodeId) {
        MindMapNode nodeEntity = mindMapNodeMapper.selectById(nodeId);
        if (nodeEntity == null) {
            return null;
        }

        MindMapNodeDto nodeDto = new MindMapNodeDto();
        BeanUtils.copyProperties(nodeEntity, nodeDto); // Maps all matching fields

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
        // Delete existing nodes for this requirementId before generating new ones
        QueryWrapper<MindMapNode> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("requirement_id", requirementInputDto.getRequirementId());
        int deletedRows = mindMapNodeMapper.delete(deleteWrapper);
        logger.info("Deleted {} existing MindMapNode(s) for requirement ID: {}", deletedRows, requirementInputDto.getRequirementId());

        // 第一步：调用大模型分析需求，获取功能点
        logger.info("开始分析需求文档，提取功能点...");
        String analyzedContent = openAIService.analyzeRequirementFunctionPoints(
         "需求标题"+  requirementInputDto.getRequirementTitle()+"\n"+     requirementInputDto.getOriginalRequirementText()
        ).block(); // 同步调用

        if (analyzedContent == null || analyzedContent.trim().isEmpty()) {
            throw new RuntimeException("Failed to analyze requirement or received empty analysis result from AI service.");
        }

        // 将分析结果存入 requirementInputDto 的 analyzedContent 属性
        requirementInputDto.setAnalyzedContent(analyzedContent);
        logger.info("需求分析完成，功能点内容长度: {} 字符", analyzedContent.length());
        logger.debug("分析得到的功能点内容: {}", analyzedContent);

        String systemPrompt = "You are a senior testing expert. Please write test cases in Chinese for the requirements. " +
                              "Output the test cases in a structured JSON format. The root of the JSON should be an object with a single key 'functionalPoints', " +
                              "which is an array. Each element in 'functionalPoints' should represent a distinct functional point and have " +
                              "'functionalPointName' (string) and 'testScenarios' (array) keys. Each element in 'testScenarios' should have " +
                              "'testCaseId' (string, e.g., 0010,0020,0030), 'testCaseGroup' (string, e.g., Smoke Test), " +
                "'testTarget' (string), 描述测试用例的测试目标, " +
                "'quotedRequirementText' (string), 'prerequisites' (string，联系上下文描述完整的配置前提或者场景前提), 'testSteps' (string 详细的测试步骤), and 'expectedResults' (string 详细的检查点，包括报错文案，具体的数据值等，需要具体描述每一个期望点的实际检查内容) keys. " +
                "'remark'(html format string， 汇总 prerequisites，testSteps，expectedResults，quotedRequirementText这几个字段的内容，按照固定顺序 返回完整描述，内容不要引用其他字段。使用中文，html格式，注意换行，加粗各个标题，美观友好"+
                "Ensure 'quotedRequirementText' includes about 80 characters before and after the relevant part of the original text,可以包含多段， with ellipses for the rest.";

        String userPromptPrefix = "Based on the following analyzed functional points, generate detailed test cases as per the specified JSON structure. " +
                                  "Divide the functional points into multiple distinct functional test object names. " +
                                  "For each functional point, write corresponding test cases. " +
                                  "Provide as many diverse test cases as possible without repetition of the same validation type. " +
                                  "The 'quotedRequirementText' should be extracted carefully from the analyzed content provided below, " +
                                  "including approximately 80 characters before and 80 characters after the key segment, using '...' for omitted parts,analyzed content 使用html的加粗格式." +
                "生成测试用例的时候，注意忽略需求中的背景，不要为需求背景生成用例;" +
                "如果需求新增了配置项，需要测试 各种情形下配置项的初始值逻辑,如果需求没有明显地新增配置项，则不要生成配置测试用例";

        // 第二步：使用分析后的功能点内容生成测试用例
        logger.info("开始基于功能点分析结果生成测试用例...");
        GPTTestCaseStructureDto gptResponse = openAIService.generateTestCases(
                requirementInputDto.getRequirementTitle() + " " + requirementInputDto.getAnalyzedContent(),
                systemPrompt,
                userPromptPrefix
        ).block(); // .block() makes it synchronous. Handle potential errors if Mono is empty or errors out.

        if (gptResponse == null || gptResponse.getFunctionalPoints() == null || gptResponse.getFunctionalPoints().isEmpty()) {
            throw new RuntimeException("Failed to generate test cases or received empty response from AI service.");
        }

        logger.info("测试用例生成完成，共生成 {} 个功能点", gptResponse.getFunctionalPoints().size());
        int totalTestCases = gptResponse.getFunctionalPoints().stream()
                .mapToInt(fp -> fp.getTestScenarios() != null ? fp.getTestScenarios().size() : 0)
                .sum();
        logger.info("总共生成 {} 个测试用例", totalTestCases);

        BatchCreateNodeDto rootBatchDto = new BatchCreateNodeDto();
        rootBatchDto.setDescription( requirementInputDto.getRequirementId() + " " + requirementInputDto.getRequirementTitle());
        rootBatchDto.setRequirementId(requirementInputDto.getRequirementId());
        rootBatchDto.setRemarks(requirementInputDto.getDocToken());
        // Set other root node properties if necessary, e.g., status
        rootBatchDto.setStatus(NodeStatus.PENDING_TEST); // Default status
        // 新增：AI 创建标记
        rootBatchDto.setIsAiGenerated(1);


        List<BatchCreateNodeDto> firstLevelChildren = new ArrayList<>();
        for (FunctionalPointDto fpDto : gptResponse.getFunctionalPoints()) {
            BatchCreateNodeDto fpNodeDto = new BatchCreateNodeDto();
            fpNodeDto.setDescription(fpDto.getFunctionalPointName());
            fpNodeDto.setRequirementId(requirementInputDto.getRequirementId()); // Inherit reqId
            fpNodeDto.setStatus(NodeStatus.PENDING_TEST); // Default status
            // 新增：AI 创建标记
            fpNodeDto.setIsAiGenerated(1);


            List<BatchCreateNodeDto> secondLevelChildren = new ArrayList<>();
            if (fpDto.getTestScenarios() != null) {
                for (ScenarioDto scenarioDto : fpDto.getTestScenarios()) {
                    BatchCreateNodeDto scenarioNodeDto = new BatchCreateNodeDto();
                    
                    scenarioNodeDto.setDescription(scenarioDto.getTestCaseId()+" "+ scenarioDto.getTestTarget());
                    scenarioNodeDto.setRemarks(scenarioDto.getRemark());
                    scenarioNodeDto.setRequirementReference(scenarioDto.getQuotedRequirementText());
                    scenarioNodeDto.setRequirementId(requirementInputDto.getRequirementId()); // Inherit reqId
                    scenarioNodeDto.setStatus(NodeStatus.PENDING_TEST); // Default status
                    // 新增：AI 创建标记
                    scenarioNodeDto.setIsAiGenerated(1);
                    secondLevelChildren.add(scenarioNodeDto);
                }
            }
            fpNodeDto.setChildren(secondLevelChildren);
            firstLevelChildren.add(fpNodeDto);
        }
        rootBatchDto.setChildren(firstLevelChildren);

        MindMapNodeDto result = this.createNodesBatch(rootBatchDto);
        logger.info("思维导图节点创建完成，根节点ID: {}, 需求ID: {}", 
                result != null ? result.getId() : "null", requirementInputDto.getRequirementId());
        
        return result;
    }

    @Override
    @Transactional
    public void moveNode(Long nodeToMoveId, Long newParentNodeId) {
        if (nodeToMoveId.equals(newParentNodeId)) {
            throw new InvalidOperationException("Node cannot be moved to itself. Node ID: " + nodeToMoveId);
        }

        MindMapNode nodeToMove = mindMapNodeMapper.selectById(nodeToMoveId);
        if (nodeToMove == null) {
            throw new ResourceNotFoundException("Node to move (ID: " + nodeToMoveId + ") not found.");
        }

        // newParentNodeId being null means moving the node to become a root node.
        MindMapNode newParentNode = null;
        if (newParentNodeId != null) {
            newParentNode = mindMapNodeMapper.selectById(newParentNodeId);
            if (newParentNode == null) {
                throw new ResourceNotFoundException("New parent node (ID: " + newParentNodeId + ") not found.");
            }
        }


        // Check if already a child of the new parent (or already a root if newParentNodeId is null)
        if ((nodeToMove.getParentId() == null && newParentNodeId == null) || 
            (nodeToMove.getParentId() != null && nodeToMove.getParentId().equals(newParentNodeId))) {
            return; // Already in the desired state
        }
        
        // Cyclic dependency check: newParentNode (if not null) cannot be a descendant of nodeToMove
        if (newParentNodeId != null) { // Only check if moving under an existing node
            List<MindMapNode> descendants = new ArrayList<>();
            getAllDescendantsRecursive(nodeToMoveId, descendants);
            for (MindMapNode descendant : descendants) {
                if (descendant.getId().equals(newParentNodeId)) {
                    throw new InvalidOperationException(
                        "Cyclic move detected: New parent node (ID: " + newParentNodeId + ") " +
                        "is a descendant of the node to move (ID: " + nodeToMoveId + ")."
                    );
                }
            }
        }

        nodeToMove.setParentId(newParentNodeId); // Set to null if newParentNodeId is null (moving to root)
        mindMapNodeMapper.updateById(nodeToMove);
    }

    // Helper to get all descendants of a node
    private void getAllDescendantsRecursive(Long nodeId, List<MindMapNode> descendantsList) {
        QueryWrapper<MindMapNode> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", nodeId);
        List<MindMapNode> directChildren = mindMapNodeMapper.selectList(queryWrapper);

        for (MindMapNode child : directChildren) {
            descendantsList.add(child);
            getAllDescendantsRecursive(child.getId(), descendantsList); // Recursive call
        }
    }

    @Override
    @Transactional
    public MindMapNodeDto updateNode(Long id, UpdateNodeRequest request) {
        MindMapNode node = mindMapNodeMapper.selectById(id);
        if (node == null) {
            throw new ResourceNotFoundException("Node not found with id: " + id);
        }

        boolean statusChanged = false;
        NodeStatus originalStatus = node.getStatus();

        if (request.getDescription() != null) {
            node.setDescription(request.getDescription());
        }
        if (request.getRemarks() != null) {
            node.setRemarks(request.getRemarks());
        }
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
        if (request.getStatus() != null && node.getStatus() != request.getStatus()) {
            node.setStatus(request.getStatus());
            statusChanged = true;
        }
        if (request.getIsExpanded() != null) {
            node.setIsExpanded(request.getIsExpanded());
        }
        if (request.getHasStrikethrough() != null) {
            node.setHasStrikethrough(request.getHasStrikethrough());
        }

        mindMapNodeMapper.updateById(node);

        if (statusChanged) {
            // If status changed, trigger recalculations
            recalculateChildrenStatusRecursive(id, node.getStatus());
            if (node.getParentId() != null) {
                recalculateParentStatusRecursive(id);
            }
        }

        // Return DTO of the updated node (freshly fetched to reflect potential status recalculations)
        MindMapNode freshNode = mindMapNodeMapper.selectById(id);
        MindMapNodeDto updatedDto = new MindMapNodeDto();
        BeanUtils.copyProperties(freshNode, updatedDto);
        return updatedDto;
    }

    // --- New method for copying node and its children ---
    @Override
    @Transactional
    public MindMapNodeDto copyNodeAndChildren(Long sourceNodeId, Long targetParentNodeId) {
        if (sourceNodeId.equals(targetParentNodeId)) { // Handles cases where targetParentNodeId might be null
            throw new InvalidOperationException("Source node ID and target parent node ID cannot be the same.");
        }

        MindMapNode sourceNodeToCopy = mindMapNodeMapper.selectById(sourceNodeId);
        if (sourceNodeToCopy == null) {
            throw new ResourceNotFoundException("Source node with ID " + sourceNodeId + " not found.");
        }

        // targetParentNodeId can be null if we want to copy the sourceNodeId as a new root node.
        if (targetParentNodeId != null) {
            MindMapNode targetParentNode = mindMapNodeMapper.selectById(targetParentNodeId);
            if (targetParentNode == null) {
                throw new ResourceNotFoundException("Target parent node with ID " + targetParentNodeId + " not found.");
            }

            // Check for cyclic dependency: targetParentNode cannot be a descendant of sourceNodeToCopy
            List<MindMapNode> descendants = new ArrayList<>();
            getAllDescendantsRecursive(sourceNodeId, descendants); // Fills 'descendants' list
            for (MindMapNode descendant : descendants) {
                if (descendant.getId().equals(targetParentNodeId)) {
                    throw new InvalidOperationException(
                        "Cyclic copy detected: Target parent node (ID: " + targetParentNodeId + ") " +
                        "is a descendant of the source node (ID: " + sourceNodeId + ")."
                    );
                }
            }
        }
        // If targetParentNodeId is null, the copied node becomes a new root node.

        return copyNodeRecursive(sourceNodeToCopy, targetParentNodeId);
    }

    private MindMapNodeDto copyNodeRecursive(MindMapNode nodeToCopy, Long newParentId) {
        MindMapNode newEntity = new MindMapNode();
        // Copy all relevant properties from nodeToCopy to newEntity.
        // "id" is excluded to ensure the database generates a new one.
        BeanUtils.copyProperties(nodeToCopy, newEntity, "id");
        newEntity.setParentId(newParentId); // Set the parent ID for the new copy.
        newEntity.setId(null); // Explicitly set ID to null for auto-generation.

        mindMapNodeMapper.insert(newEntity); // Save, ID gets populated.

        MindMapNodeDto newDto = new MindMapNodeDto();
        BeanUtils.copyProperties(newEntity, newDto); // Map the newly saved entity (with ID) to DTO.

        // Recursively copy children of the original nodeToCopy
        QueryWrapper<MindMapNode> childrenQuery = new QueryWrapper<>();
        childrenQuery.eq("parent_id", nodeToCopy.getId()); // Find children of the *original* node being copied
        List<MindMapNode> childrenOfNodeToCopy = mindMapNodeMapper.selectList(childrenQuery);

        if (childrenOfNodeToCopy != null && !childrenOfNodeToCopy.isEmpty()) {
            List<MindMapNodeDto> copiedChildrenDtos = new ArrayList<>();
            for (MindMapNode childToCopy : childrenOfNodeToCopy) {
                // Recursive call: copy each child, making it a child of the *newly created* parent (newEntity)
                copiedChildrenDtos.add(copyNodeRecursive(childToCopy, newEntity.getId()));
            }
            newDto.setChildren(copiedChildrenDtos);
        }
        return newDto;
    }
}
