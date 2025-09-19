package com.example.mindmap.controller;

import com.example.mindmap.entity.MindMapNode;
import com.example.mindmap.service.MindMapService;
import com.example.mindmap.utils.ThirdPartyAPITool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PutMapping; // Added
import org.springframework.web.bind.annotation.PathVariable; // Added
import com.example.mindmap.dto.BatchCreateNodeDto;
import com.example.mindmap.dto.RequirementInputDto; // New DTO
import com.example.mindmap.dto.UpdateNodeRequest; // Added
import javax.validation.Valid;
import org.slf4j.Logger; // SLF4J Logger
import org.slf4j.LoggerFactory; // SLF4J LoggerFactory
import com.example.mindmap.exception.InvalidOperationException; // Added
import com.example.mindmap.exception.ResourceNotFoundException; // Added

import java.util.List;
import com.example.mindmap.dto.MindMapNodeDto;

@RestController
@RequestMapping("/api/mindmap")
@Slf4j
public class MindMapController {

    private static final Logger logger = LoggerFactory.getLogger(MindMapController.class); // SLF4J Logger instance

    @Autowired
    private MindMapService mindMapService;

    // Add a new node
    // POST /api/mindmap/nodes
    @PostMapping("/nodes")
    public ResponseEntity<MindMapNode> addNode(@RequestBody MindMapNode node) {
        try {
            MindMapNode createdNode = mindMapService.addNode(node);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdNode);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build(); // Or return a proper error message
        }
    }

    // Get a node by its ID
    // GET /api/mindmap/nodes/{nodeId}
    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<MindMapNode> getNodeById(@PathVariable Long nodeId) {
        MindMapNode node = mindMapService.getNodeById(nodeId);
        if (node != null) {
            return ResponseEntity.ok(node);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Get all nodes for a specific requirement ID (forms a mind map)
    // GET /api/mindmap/requirements/{requirementId}/nodes
    @GetMapping("/requirements/{requirementId}/nodes")
    // public ResponseEntity<List<MindMapNode>>
    // getMindMapByRequirementId(@PathVariable String requirementId) { // Old
    public ResponseEntity<List<MindMapNodeDto>> getMindMapByRequirementId(@PathVariable String requirementId) { // New
        List<MindMapNodeDto> nodesTree = mindMapService.getMindMapByRequirementId(requirementId);
        // No change needed here if nodesTree is an empty list for no data,
        // service layer already handles returning List.of()
        return ResponseEntity.ok(nodesTree);
    }

    // Delete a node and all its children
    // DELETE /api/mindmap/nodes/{nodeId}/tree
    @DeleteMapping("/nodes/{nodeId}/tree")
    public ResponseEntity<Void> deleteNodeAndChildren(@PathVariable Long nodeId) {
        mindMapService.deleteNodeAndChildren(nodeId);
        return ResponseEntity.noContent().build();
    }

    // Delete a node but keep its children (re-parenting them)
    // DELETE /api/mindmap/nodes/{nodeId}
    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Void> deleteNodeKeepChildren(@PathVariable Long nodeId) {
        mindMapService.deleteNodeKeepChildren(nodeId);
        return ResponseEntity.noContent().build();
    }

    // Update a node's description
    // PUT /api/mindmap/nodes/{nodeId}/description
    @PutMapping("/nodes/{nodeId}/description")
    public ResponseEntity<MindMapNode> updateNodeDescription(@PathVariable Long nodeId,
            @RequestBody String description) {
        try {
            MindMapNode updatedNode = mindMapService.updateNodeDescription(nodeId, description);
            if (updatedNode != null) {
                return ResponseEntity.ok(updatedNode);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null); // Consider a proper error response
        }
    }

    // Update a node's remarks
    // PUT /api/mindmap/nodes/{nodeId}/remarks
    @PutMapping("/nodes/{nodeId}/remarks")
    public ResponseEntity<MindMapNode> updateNodeRemarks(@PathVariable Long nodeId,
            @RequestBody(required = false) String remarks) {
        try {
            MindMapNode updatedNode = mindMapService.updateNodeRemarks(nodeId, remarks);
            if (updatedNode != null) {
                return ResponseEntity.ok(updatedNode);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            // This path might not be hit if only nodeId is validated strictly in service
            // for remarks
            return ResponseEntity.badRequest().body(null);
        }
    }

    // Set the status of a single node
    // PUT /api/mindmap/nodes/{nodeId}/status
    @PutMapping("/nodes/{nodeId}/status")
    public ResponseEntity<MindMapNode> setNodeStatus(@PathVariable Long nodeId,
            @RequestBody com.example.mindmap.entity.NodeStatus status) {
        // Spring should be able to convert the JSON string "PENDING_TEST" to NodeStatus
        // enum directly
        try {
            MindMapNode updatedNode = mindMapService.setNodeStatus(nodeId, status);
            if (updatedNode != null) {
                return ResponseEntity.ok(updatedNode);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null); // Or a proper error object
        }
    }

    // Batch set the status for multiple nodes
    // PUT /api/mindmap/nodes/status/batch
    @PutMapping("/nodes/status/batch")
    public ResponseEntity<List<MindMapNode>> batchSetNodeStatus(
            @RequestBody com.example.mindmap.controller.dto.BatchStatusUpdateRequest request) {
        if (request == null || request.getNodeIds() == null || request.getNodeIds().isEmpty()
                || request.getStatus() == null) {
            return ResponseEntity.badRequest().body(null); // Basic validation for the request object
        }
        try {
            List<MindMapNode> updatedNodes = mindMapService.batchSetNodeStatus(request.getNodeIds(),
                    request.getStatus());
            // Decide on response: OK with list, or OK with count, etc.
            // If some nodes in the batch were not found, the service currently skips them.
            // The response will contain only the nodes that were successfully updated.
            return ResponseEntity.ok(updatedNodes);
        } catch (IllegalArgumentException e) {
            // This catches validation errors from the service layer (e.g. empty list after
            // filtering)
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/nodes/batch")
    public ResponseEntity<MindMapNodeDto> createNodesBatch(@Valid @RequestBody BatchCreateNodeDto batchCreateNodeDto) {
        MindMapNodeDto createdRootNode = mindMapService.createNodesBatch(batchCreateNodeDto);
        // Assuming createNodesBatch returns the root DTO of the created structure
        return new ResponseEntity<>(createdRootNode, HttpStatus.CREATED);
    }

    // New endpoint for generating test cases and creating mind map
    // POST /api/mindmap/generate-from-requirement
    @PostMapping("/generate-from-requirement")
    public ResponseEntity<MindMapNodeDto> generateAndCreateMindMap(
            @Valid @RequestBody RequirementInputDto requirementInputDto) {
        logger.info("Received request to generate test cases for requirement ID: {}",
                requirementInputDto.getRequirementId());
        logger.debug("Full request payload: {}", requirementInputDto); // Logs the full DTO, requires toString() in DTO
                                                                       // or use ObjectMapper

        try {
            MindMapNodeDto mindMapRootNode = mindMapService.generateTestCasesFromRequirement(requirementInputDto);
            logger.info(
                    "Successfully generated and saved test cases for requirement ID: {}. Returning root node ID: {}",
                    requirementInputDto.getRequirementId(), mindMapRootNode != null ? mindMapRootNode.getId() : "null");
            logger.debug("Full response payload: {}", mindMapRootNode); // Logs the full DTO

            return ResponseEntity.status(HttpStatus.CREATED).body(mindMapRootNode);
        } catch (IllegalStateException e) {
            logger.error("Error generating test cases for requirement ID: {}. OpenAI API key issue: {}",
                    requirementInputDto.getRequirementId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // Simplified error response
        } catch (RuntimeException e) {
            logger.error("Error generating test cases for requirement ID: {}. RuntimeException: {}",
                    requirementInputDto.getRequirementId(), e.getMessage(), e); // Log stack trace
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // Simplified error response
        } catch (Exception e) { // Catch any other unexpected errors
            logger.error("Unexpected error generating test cases for requirement ID: {}: {}",
                    requirementInputDto.getRequirementId(), e.getMessage(), e); // Log stack trace
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/nodes/{nodeToMoveId}/move-to/{newParentNodeId}")
    public ResponseEntity<Void> moveNode(
            @PathVariable Long nodeToMoveId,
            @PathVariable Long newParentNodeId) {
        try {
            mindMapService.moveNode(nodeToMoveId, newParentNodeId);
            return ResponseEntity.noContent().build(); // HTTP 204 for successful update with no body
        } catch (IllegalArgumentException e) { // Catching a generic exception for now
            // Based on service logic, this could be 400 Bad Request or 404 Not Found
            // For simplicity, returning 400. More specific exception handling is better.
            // A @ControllerAdvice would be ideal for mapping custom service exceptions to
            // HTTP statuses.
            logger.warn("Failed to move node {} to new parent {}: {}", nodeToMoveId, newParentNodeId, e.getMessage());
            if (e.getMessage().contains("not found") || e.getMessage().contains("does not exist")) { // Basic check
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.badRequest().build(); // For cyclic dependencies or other invalid arguments
        } catch (Exception e) { // Catch-all for other unexpected errors
            logger.error("Unexpected error while moving node {} to new parent {}: {}", nodeToMoveId, newParentNodeId,
                    e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/nodes/{id}")
    public MindMapNodeDto updateNode(@PathVariable Long id, @RequestBody UpdateNodeRequest request) {
        return mindMapService.updateNode(id, request);
    }

    @PostMapping("/nodes/{sourceNodeId}/copy-to/{targetParentNodeId}")
    public ResponseEntity<MindMapNodeDto> copyNode(
            @PathVariable Long sourceNodeId,
            @PathVariable Long targetParentNodeId) {
        try {
            MindMapNodeDto copiedNodeDto = mindMapService.copyNodeAndChildren(sourceNodeId, targetParentNodeId);
            return ResponseEntity.status(HttpStatus.CREATED).body(copiedNodeDto);
        } catch (ResourceNotFoundException e) {
            logger.warn(
                    "Resource not found during copy operation for sourceNodeId: {}, targetParentNodeId: {}. Message: {}",
                    sourceNodeId, targetParentNodeId, e.getMessage());
            // Consider returning an error DTO with e.getMessage()
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (InvalidOperationException e) {
            logger.warn("Invalid operation during copy for sourceNodeId: {}, targetParentNodeId: {}. Message: {}",
                    sourceNodeId, targetParentNodeId, e.getMessage());
            // Consider returning an error DTO with e.getMessage()
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) { // Catch-all for other unexpected errors
            logger.error("Unexpected error while copying node {} to new parent {}: {}", sourceNodeId,
                    targetParentNodeId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // Or an error DTO
        }
    }

    @GetMapping("/genTestCase")
    @ResponseBody
    public void genTestCase(@RequestParam String docToken, String rdcNumber, String title) {
        // 对title参数进行URL解码
        try {
            // 对URL编码的字符串进行两次解码,先解码%编码,再解码Unicode编码
            String encodedTitle = java.net.URLDecoder.decode(title, "UTF-8");
            title = java.net.URLDecoder.decode(encodedTitle, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            log.error("URL解码失败: {}", e.getMessage());
        }
        log.info("重新生成脑图 {}", title);
        RequirementInputDto testCaseRequestDTO = new RequirementInputDto();
        testCaseRequestDTO.setRequirementId(rdcNumber);
        testCaseRequestDTO.setRequirementTitle(title);
        testCaseRequestDTO.setDocToken(docToken);

        String doc = ThirdPartyAPITool.getDocContent(docToken);

        log.info("获取到了需求原文 {} {}", title, doc);
        //尝试去掉背景等描述
        if(doc.contains("功能逻辑")){
            doc = doc.substring(doc.indexOf("功能逻辑"),doc.length()-1);
        }
        testCaseRequestDTO.setOriginalRequirementText(doc);
        MindMapNodeDto mindMapRootNode = mindMapService.generateTestCasesFromRequirement(testCaseRequestDTO);

    }

    public static void main(String args[]) {
        try {
            // 对URL编码的字符串进行两次解码,先解码%编码,再解码Unicode编码
            String encodedTitle = java.net.URLDecoder.decode(
                    "inf-115913%2520%25E3%2580%2590251017%25E3%2580%2591%25E3%2580%2590%25E5%2589%258D%25E7%25AB%25AF%252F%25E5%2590%258E%25E7%25AB%25AF%25E3%2580%2591%25E3%2580%2590%25E8%25B7%25A8%25E7%25BB%2584%25E7%2594%25B3%25E8%25AF%25B7%25E7%25BB%2584%25E3%2580%2591%25E4%25BE%259B%25E5%25BA%2594%25E5%2595%2586%25E9%2593%25B6%25E8%25A1%258C%25E8%25B4%25A6%25E6%2588%25B7%25E6%2594%25AF%25E6%258C%2581%25E5%25AD%2597%25E6%25AE%25B5%25E6%259D%2583%25E9%2599%2590-%25E7%258E%258B%25E5%25AE%2587",
                    "UTF-8");
            String title = java.net.URLDecoder.decode(encodedTitle, "UTF-8");
            log.info("解码后的标题: {}", title);
        } catch (java.io.UnsupportedEncodingException e) {
            log.error("URL解码失败: {}", e.getMessage());
        }

        // log.info(ThirdPartyAPITool.getDocContent("VaucdNjgooeiIExo9CmcjwGlnAc"));

    }
}
