package com.example.mindmap.controller.dto;

import com.example.mindmap.entity.NodeStatus;
import java.util.List;
import lombok.Data;

@Data
public class BatchStatusUpdateRequest {
    private List<Long> nodeIds;
    private NodeStatus status;
}
