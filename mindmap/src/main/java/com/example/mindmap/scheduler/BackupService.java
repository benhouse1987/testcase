package com.example.mindmap.scheduler;

import com.example.mindmap.entity.MindMapNode;
import com.example.mindmap.entity.MindMapNodeBak;
import com.example.mindmap.mapper.MindMapNodeMapper;
import com.example.mindmap.mapper.MindMapNodeBakMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // Added for pagination

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);

    @Autowired
    private MindMapNodeMapper mindMapNodeMapper;

    @Autowired
    private MindMapNodeBakMapper mindMapNodeBakMapper;

    /**
     * Scheduled task to back up mind_map_node data to mind_map_node_bak.
     * Runs every day at 10 PM.
     * Cron expression: second, minute, hour, day of month, month, day(s) of week
     * {@code 0 0 22 * * ?} means 10:00:00 PM every day.
     */
    @Scheduled(cron = "0 0 22 * * ?")
    @Transactional
    public void backupMindMapNodes() {
        logger.info("Starting daily backup of mind_map_node table...");
        long totalBackedUpCount = 0;
        long currentPage = 1;
        final long pageSize = 500; // Records per page
        LocalDateTime backupTimestamp = LocalDateTime.now(); // Use one timestamp for the whole operation

        try {
            while (true) {
                Page<MindMapNode> page = new Page<>(currentPage, pageSize);
                Page<MindMapNode> pagedNodes = mindMapNodeMapper.selectPage(page, null); // Pass null for no specific query wrapper

                List<MindMapNode> currentNodes = pagedNodes.getRecords();

                if (currentNodes == null || currentNodes.isEmpty()) {
                    if (currentPage == 1) { // No data at all
                        logger.info("No data found in mind_map_node table. Nothing to back up.");
                    } else { // No more data in subsequent pages
                        logger.info("Finished processing all pages.");
                    }
                    break; // Exit loop if no records are found in the current page
                }

                logger.info("Processing page {} with {} nodes for backup.", currentPage, currentNodes.size());

                List<MindMapNodeBak> backupNodesBatch = currentNodes.stream().map(node -> {
                    MindMapNodeBak bakNode = new MindMapNodeBak();
                    BeanUtils.copyProperties(node, bakNode);
                    bakNode.setBackupTime(backupTimestamp);
                    return bakNode;
                }).collect(Collectors.toList());

                for (MindMapNodeBak bakNode : backupNodesBatch) {
                    mindMapNodeBakMapper.insert(bakNode);
                }
                totalBackedUpCount += backupNodesBatch.size();

                if (currentNodes.size() < pageSize) {
                    // This was the last page
                    break;
                }
                currentPage++;
            }

            if (totalBackedUpCount > 0) {
                logger.info("Successfully backed up {} total nodes from mind_map_node to mind_map_node_bak.", totalBackedUpCount);
            }

        } catch (Exception e) {
            logger.error("Error during mind_map_node backup process after backing up {} nodes.", totalBackedUpCount, e);
            // Depending on requirements, you might want to re-throw or handle differently
        }
    }
}
