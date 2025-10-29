package com.example.mindmap.dto;

import com.example.mindmap.entity.NodeStatus;
import lombok.Data;

@Data
public class UpdateNodeRequest {

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

    private String cssStyle;
}
