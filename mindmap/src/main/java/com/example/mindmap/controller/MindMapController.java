package com.example.mindmap.controller;

import com.example.mindmap.entity.MindMapNode;
import com.example.mindmap.service.MindMapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.mindmap.dto.BatchCreateNodeDto;
import javax.validation.Valid;


import java.util.List;
import com.example.mindmap.dto.MindMapNodeDto; // Add this import

@RestController
@RequestMapping("/api/mindmap")
public class MindMapController {

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
    // public ResponseEntity<List<MindMapNode>> getMindMapByRequirementId(@PathVariable String requirementId) { // Old
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
    public ResponseEntity<MindMapNode> updateNodeDescription(@PathVariable Long nodeId, @RequestBody String description) {
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
    public ResponseEntity<MindMapNode> updateNodeRemarks(@PathVariable Long nodeId, @RequestBody(required = false) String remarks) {
        try {
            MindMapNode updatedNode = mindMapService.updateNodeRemarks(nodeId, remarks);
            if (updatedNode != null) {
                return ResponseEntity.ok(updatedNode);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            // This path might not be hit if only nodeId is validated strictly in service for remarks
            return ResponseEntity.badRequest().body(null); 
        }
    }

    // Set the status of a single node
    // PUT /api/mindmap/nodes/{nodeId}/status
    @PutMapping("/nodes/{nodeId}/status")
    public ResponseEntity<MindMapNode> setNodeStatus(@PathVariable Long nodeId, @RequestBody com.example.mindmap.entity.NodeStatus status) {
        // Spring should be able to convert the JSON string "PENDING_TEST" to NodeStatus enum directly
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
    public ResponseEntity<List<MindMapNode>> batchSetNodeStatus(@RequestBody com.example.mindmap.controller.dto.BatchStatusUpdateRequest request) {
        if (request == null || request.getNodeIds() == null || request.getNodeIds().isEmpty() || request.getStatus() == null) {
            return ResponseEntity.badRequest().body(null); // Basic validation for the request object
        }
        try {
            List<MindMapNode> updatedNodes = mindMapService.batchSetNodeStatus(request.getNodeIds(), request.getStatus());
            // Decide on response: OK with list, or OK with count, etc.
            // If some nodes in the batch were not found, the service currently skips them.
            // The response will contain only the nodes that were successfully updated.
            return ResponseEntity.ok(updatedNodes);
        } catch (IllegalArgumentException e) {
            // This catches validation errors from the service layer (e.g. empty list after filtering)
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/nodes/batch")
    public ResponseEntity<MindMapNodeDto> createNodesBatch(@Valid @RequestBody BatchCreateNodeDto batchCreateNodeDto) {
        MindMapNodeDto createdRootNode = mindMapService.createNodesBatch(batchCreateNodeDto);
        // Assuming createNodesBatch returns the root DTO of the created structure
        return new ResponseEntity<>(createdRootNode, HttpStatus.CREATED);
    }
}
