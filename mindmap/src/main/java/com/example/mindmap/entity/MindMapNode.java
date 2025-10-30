package com.example.mindmap.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
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

    private Boolean isExpanded = true;

    private Boolean hasStrikethrough = false;

    // 新增：创建时间，默认在插入时填充为当前时间
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private java.time.LocalDateTime createdAt;

    // 新增：是否 AI 生成标记，AI 创建时传入 1，默认 null
    private Integer isAiGenerated;

    // CSS样式字符串，用于存储节点的样式信息，最大长度4000字符
    private String cssStyle;

    // Sprint代码，字符串类型，最大20字符
    private String sprintCode;
}
