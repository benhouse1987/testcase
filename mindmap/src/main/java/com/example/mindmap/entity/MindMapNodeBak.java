package com.example.mindmap.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("mind_map_node_bak")
public class MindMapNodeBak {

    // No @TableId here if we are simply copying,
    // or if ID is not auto-generated in the backup table by DB.
    // The Liquibase script made 'id' not auto-incrementing for the bak table.
    private Long id;

    private Long parentId;

    private String description;

    private String remarks;

    private String requirementId;

    private String backendDeveloper;

    private String frontendDeveloper;

    private String tester;

    private String requirementReference;

    private NodeStatus status; // Assuming NodeStatus enum can be reused

    private boolean isExpanded = true;

    private boolean hasStrikethrough = false;

    private LocalDateTime backupTime;
}
