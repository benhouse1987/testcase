package com.example.mindmap.dto;

import com.example.mindmap.entity.NodeStatus;
import lombok.AllArgsConstructor; // Added
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor // Added
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
    private Boolean isExpanded;
    private Boolean hasStrikethrough;
    private List<MindMapNodeDto> children = new ArrayList<>();

    // Manual constructor removed to rely on Lombok's @AllArgsConstructor
}
