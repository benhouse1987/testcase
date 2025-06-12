package com.example.mindmap.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField; // Added
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.example.mindmap.entity.MindMapNodeRemark; // Added

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

    private boolean isExpanded = true;

    private boolean hasStrikethrough = false;

    @TableField(exist = false)
    private MindMapNodeRemark remark;
}
