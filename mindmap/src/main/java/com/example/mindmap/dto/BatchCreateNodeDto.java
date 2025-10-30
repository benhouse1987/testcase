package com.example.mindmap.dto;

import com.example.mindmap.entity.NodeStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
public class BatchCreateNodeDto {

    private Long id; // 可选字段，如果提供则使用指定的id创建节点

    @NotBlank(message = "Description cannot be blank")
    private String description;

    private String remarks;

    @NotBlank(message = "Requirement ID cannot be blank")
    private String requirementId;

    private String backendDeveloper;
    private String frontendDeveloper;
    private String tester;
    private String requirementReference;

    private NodeStatus status = NodeStatus.PENDING_TEST; // Default status

    // 新增：是否 AI 生成标记，AI 创建时传入 1，默认 null
    private Integer isAiGenerated;

    // CSS样式字符串，用于存储节点的样式信息
    private String cssStyle;

    // Sprint代码，字符串类型，最大20字符
    private String sprintCode;

    private List<BatchCreateNodeDto> children = new ArrayList<>();

    // Constructor to initialize children - though @Data and @NoArgsConstructor might be enough,
    // explicitly initializing here or ensuring Lombok does it if it's a more complex setup.
    // For simple list initialization like this, direct initialization as above is common.
    // If specific constructor logic for children was needed, it would go here.
    // Lombok's @Data should handle getters/setters for all fields.
    // @NoArgsConstructor handles the no-args constructor.
}
