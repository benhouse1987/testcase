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

    private List<BatchCreateNodeDto> children = new ArrayList<>();

    // Constructor to initialize children - though @Data and @NoArgsConstructor might be enough,
    // explicitly initializing here or ensuring Lombok does it if it's a more complex setup.
    // For simple list initialization like this, direct initialization as above is common.
    // If specific constructor logic for children was needed, it would go here.
    // Lombok's @Data should handle getters/setters for all fields.
    // @NoArgsConstructor handles the no-args constructor.
}
