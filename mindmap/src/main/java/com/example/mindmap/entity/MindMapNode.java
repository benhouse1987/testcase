package com.example.mindmap.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("mind_map_node")
public class MindMapNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    // Description (rich text, not null)
    private String description;

    // Remarks (rich text, nullable)
    private String remarks;

    // Requirement ID (nullable)
    private String requirementId;

    // Backend Developer (nullable)
    private String backendDeveloper;

    // Frontend Developer (nullable)
    private String frontendDeveloper;

    // Tester (nullable)
    private String tester;

    // Requirement Reference (rich text, nullable)
    private String requirementReference;

    // Status (default: PENDING_TEST)
    private NodeStatus status = NodeStatus.PENDING_TEST;
}
