package com.example.mindmap.dto;

import com.example.mindmap.entity.NodeStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class MindMapNodeDto {
    private Long id;
    private Long parentId;
    private String description;
    private String remarks;
    private String requirementId;
    private String backendDeveloper;
    private String frontendDeveloper;
    private String tester;
    private String requirementReference;
    private NodeStatus status;
    private List<MindMapNodeDto> children = new ArrayList<>();

    // Consider a constructor to map from MindMapNode entity if needed, or use a mapping library
    public MindMapNodeDto(Long id, Long parentId, String description, String remarks, String requirementId,
                          String backendDeveloper, String frontendDeveloper, String tester,
                          String requirementReference, NodeStatus status) {
        this.id = id;
        this.parentId = parentId;
        this.description = description;
        this.remarks = remarks;
        this.requirementId = requirementId;
        this.backendDeveloper = backendDeveloper;
        this.frontendDeveloper = frontendDeveloper;
        this.tester = tester;
        this.requirementReference = requirementReference;
        this.status = status;
    }
}
