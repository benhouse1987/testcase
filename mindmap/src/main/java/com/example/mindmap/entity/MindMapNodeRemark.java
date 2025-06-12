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
@TableName("mind_map_node_remarks")
public class MindMapNodeRemark {

    @TableId(value = "node_id", type = IdType.NONE) // node_id is a foreign key, not auto-generated here
    private Long nodeId;

    private String remarksText;

    // Optional: If you want a bidirectional relationship, add MindMapNode reference
    // @OneToOne
    // @JoinColumn(name = "node_id", referencedColumnName = "id", insertable = false, updatable = false)
    // private MindMapNode mindMapNode;
}
