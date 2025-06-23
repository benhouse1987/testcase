package com.example.mindmap.repository;

import com.example.mindmap.entity.MindMapNodeBak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MindMapNodeBakRepository extends JpaRepository<MindMapNodeBak, Long> {
}
