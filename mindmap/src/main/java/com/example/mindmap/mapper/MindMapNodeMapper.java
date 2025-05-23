package com.example.mindmap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mindmap.entity.MindMapNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MindMapNodeMapper extends BaseMapper<MindMapNode> {
    int deleteByRequirementId(@Param("requirementId") String requirementId);
}
