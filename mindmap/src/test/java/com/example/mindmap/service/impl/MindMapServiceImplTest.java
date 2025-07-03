package com.example.mindmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.mindmap.dto.RequirementInputDto;
import com.example.mindmap.entity.MindMapNode;
import com.example.mindmap.mapper.MindMapNodeMapper;
import com.example.mindmap.service.openai.OpenAIService;
import com.example.mindmap.service.openai.dto.GPTTestCaseStructureDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.mindmap.dto.MindMapNodeDto;
import com.example.mindmap.exception.InvalidOperationException;
import com.example.mindmap.exception.ResourceNotFoundException;
import com.example.mindmap.entity.NodeStatus;


@ExtendWith(MockitoExtension.class)
class MindMapServiceImplTest {

    @Mock
    private MindMapNodeMapper mindMapNodeMapper;

    @Mock
    private OpenAIService openAIService;

    @InjectMocks
    private MindMapServiceImpl mindMapService;

    @Test
    void generateTestCasesFromRequirement_shouldDeleteExistingNodesFirst() {
        // Arrange
        String requirementId = "REQ-001";
        RequirementInputDto inputDto = new RequirementInputDto();
        inputDto.setRequirementId(requirementId);
        inputDto.setRequirementTitle("Test Title");
        inputDto.setOriginalRequirementText("Some requirement text.");

        // Mock the OpenAIService to return an empty response to avoid NullPointerExceptions later in the method
        GPTTestCaseStructureDto mockGptResponse = new GPTTestCaseStructureDto();
        mockGptResponse.setFunctionalPoints(Collections.emptyList()); // Ensure it's not null and empty
        when(openAIService.generateTestCases(anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(mockGptResponse));

        // Mock createNodesBatch to prevent further execution and potential NullPointerExceptions
        // as we are only testing the deletion part and the beginning of the method.
        // We use a spy on a real MindMapServiceImpl instance or mock the call to createNodesBatch if it's public
        // For simplicity, if createNodesBatch is complex or private and called internally,
        // ensure GPT response leads to minimal processing in the tested method after deletion.
        // Here, returning an empty list from GPT should prevent deep processing in createNodesBatch.

        when(mindMapNodeMapper.delete(any(QueryWrapper.class))).thenReturn(5); // Assume 5 rows deleted

        // Act
        try {
            mindMapService.generateTestCasesFromRequirement(inputDto);
        } catch (RuntimeException e) {
            // Catching RuntimeException because the method throws one if GPT response is empty.
            // This is expected in this test setup after the deletion.
            if (!e.getMessage().contains("Failed to generate test cases or received empty response from AI service.")) {
                throw e; // rethrow if it's not the expected exception
            }
        }


        // Assert
        QueryWrapper<MindMapNode> expectedDeleteWrapper = new QueryWrapper<>();
        expectedDeleteWrapper.eq("requirement_id", requirementId);

        // Verify that delete was called once with a QueryWrapper that matches the requirementId
        verify(mindMapNodeMapper, times(1)).delete(argThat(wrapper -> {
            // Extract the SQL and parameters from the wrapper for comparison.
            // This is a bit complex as QueryWrapper's generated SQL depends on its internal state.
            // A simpler check might be to capture the argument and inspect its properties.
            // For this test, we'll check the 'eqSql' part which contains "requirement_id = 'REQ-001'".
            // Note: getSqlSelect is not what we want. We need to check the WHERE clause.
            // The actual SQL generation is done by MyBatis Plus internally.
            // We rely on the QueryWrapper's internal state being correctly set.
            // A common way is to check the `getCustomSqlSegment` or similar, or ensure `getParamNameValuePairs` reflects the condition.
            // Let's verify the parameter map if possible, or the sql segment.
            // For now, ensuring it's a QueryWrapper and then checking its effect via other means (like # of calls) is often sufficient.
            // A more robust check on QueryWrapper contents:
            String expectedSqlSegment = "requirement_id = '" + requirementId + "'";
            // The actual segment might be "requirement_id = #{ew.paramNameValuePairs.MPGENVAL1}"
            // and `ew.paramNameValuePairs.MPGENVAL1` would be `requirementId`.
            // This lambda matcher for argThat needs to return true if the argument is as expected.
            // We are verifying the wrapper that was passed to delete() method.
            // Cast to QueryWrapper to access getParamNameValuePairs
            if (wrapper instanceof QueryWrapper) {
                QueryWrapper<?> queryWrapper = (QueryWrapper<?>) wrapper;
                return queryWrapper.getSqlSegment().contains("requirement_id") &&
                       queryWrapper.getParamNameValuePairs().containsValue(requirementId);
            }
            return false; // Should not happen if the actual object is a QueryWrapper
        }));

        // Verify that OpenAIService was called (meaning deletion happened before it)
        verify(openAIService, times(1)).generateTestCases(anyString(), anyString(), anyString());
    }

    @Test
    void copyNodeAndChildren_success_singleNodeNoChildren() {
        // Arrange
        Long sourceNodeId = 1L;
        Long targetParentNodeId = 10L;
        Long newGeneratedId = 100L;

        MindMapNode sourceNode = new MindMapNode(sourceNodeId, null, "Source Node", "Source Remarks", "REQ-001", "dev_be", "dev_fe", "tester_x", "ref", NodeStatus.PENDING_TEST, true, false);
        MindMapNode targetParentNode = new MindMapNode(targetParentNodeId, null, "Target Parent", "Target Remarks", "REQ-002", null, null, null, null, NodeStatus.TESTED, true, false);

        when(mindMapNodeMapper.selectById(sourceNodeId)).thenReturn(sourceNode);
        when(mindMapNodeMapper.selectById(targetParentNodeId)).thenReturn(targetParentNode);

        // Mock finding children of source node (returns empty list)
        // Use a flexible argThat for QueryWrapper
        when(mindMapNodeMapper.selectList(argThat(qw -> {
            String sql = qw.getCustomSqlSegment();
            return sql != null && sql.contains("parent_id = " + sourceNodeId);
        }))).thenReturn(Collections.emptyList());

        // Mock insert to simulate ID generation
        when(mindMapNodeMapper.insert(any(MindMapNode.class))).thenAnswer(invocation -> {
            MindMapNode nodeToInsert = invocation.getArgument(0);
            nodeToInsert.setId(newGeneratedId); // Simulate ID generation
            return 1; // rows affected
        });

        // Act
        MindMapNodeDto resultDto = mindMapService.copyNodeAndChildren(sourceNodeId, targetParentNodeId);

        // Assert
        assertNotNull(resultDto);
        assertEquals(newGeneratedId, resultDto.getId());
        assertEquals(targetParentNodeId, resultDto.getParentId());
        assertEquals(sourceNode.getDescription(), resultDto.getDescription());
        assertEquals(sourceNode.getRemarks(), resultDto.getRemarks());
        assertEquals(sourceNode.getRequirementId(), resultDto.getRequirementId());
        assertEquals(sourceNode.getStatus(), resultDto.getStatus());
        assertTrue(resultDto.getChildren().isEmpty());

        verify(mindMapNodeMapper, times(1)).selectById(sourceNodeId);
        verify(mindMapNodeMapper, times(1)).selectById(targetParentNodeId);
        verify(mindMapNodeMapper, times(1)).insert(argThat(node ->
            node.getParentId().equals(targetParentNodeId) &&
            node.getDescription().equals(sourceNode.getDescription()) &&
            node.getId() == null // Before insert, ID should be null (or not newGeneratedId yet)
        ));
         // Verify it tries to fetch children for the original source node
        verify(mindMapNodeMapper, times(1)).selectList(argThat(qw -> {
             String sql = qw.getCustomSqlSegment();
             return sql != null && sql.contains("parent_id = " + sourceNodeId);
        }));
    }

    @Test
    void copyNodeAndChildren_success_nodeWithOneLevelOfChildren() {
        // Arrange
        Long sourceNodeId = 1L;
        Long sourceChildId = 2L;
        Long targetParentNodeId = 10L;

        Long newCopiedParentId = 100L;
        Long newCopiedChildId = 101L;

        MindMapNode sourceNode = new MindMapNode(sourceNodeId, null, "Source Parent", null, "REQ-01", null, null, null, null, NodeStatus.PENDING_TEST, true, false);
        MindMapNode sourceChild = new MindMapNode(sourceChildId, sourceNodeId, "Source Child 1", null, "REQ-01", null, null, null, null, NodeStatus.PENDING_TEST, true, false);
        MindMapNode targetParentNode = new MindMapNode(targetParentNodeId, null, "Target Parent", null, "REQ-02", null, null, null, null, NodeStatus.TESTED, true, false);

        // Mock fetching source and target parent
        when(mindMapNodeMapper.selectById(sourceNodeId)).thenReturn(sourceNode);
        when(mindMapNodeMapper.selectById(targetParentNodeId)).thenReturn(targetParentNode);

        // Mock fetching children of sourceNode (returns sourceChild)
        when(mindMapNodeMapper.selectList(argThat(qw -> qw.getCustomSqlSegment() != null && qw.getCustomSqlSegment().contains("parent_id = " + sourceNodeId))))
            .thenReturn(Collections.singletonList(sourceChild));

        // Mock fetching children of sourceChild (returns empty list)
        when(mindMapNodeMapper.selectList(argThat(qw -> qw.getCustomSqlSegment() != null && qw.getCustomSqlSegment().contains("parent_id = " + sourceChildId))))
            .thenReturn(Collections.emptyList());

        // Mock insert for the copied parent node
        when(mindMapNodeMapper.insert(argThat(node -> node.getDescription().equals("Source Parent")))).thenAnswer(invocation -> {
            MindMapNode nodeToInsert = invocation.getArgument(0);
            nodeToInsert.setId(newCopiedParentId);
            return 1;
        });
        // Mock insert for the copied child node
        when(mindMapNodeMapper.insert(argThat(node -> node.getDescription().equals("Source Child 1")))).thenAnswer(invocation -> {
            MindMapNode nodeToInsert = invocation.getArgument(0);
            nodeToInsert.setId(newCopiedChildId);
            return 1;
        });

        // Act
        MindMapNodeDto resultDto = mindMapService.copyNodeAndChildren(sourceNodeId, targetParentNodeId);

        // Assert
        assertNotNull(resultDto);
        assertEquals(newCopiedParentId, resultDto.getId());
        assertEquals(targetParentNodeId, resultDto.getParentId());
        assertEquals("Source Parent", resultDto.getDescription());
        assertEquals(1, resultDto.getChildren().size());

        MindMapNodeDto copiedChildDto = resultDto.getChildren().get(0);
        assertEquals(newCopiedChildId, copiedChildDto.getId());
        assertEquals(newCopiedParentId, copiedChildDto.getParentId()); // Child's parent is the new copied parent
        assertEquals("Source Child 1", copiedChildDto.getDescription());
        assertTrue(copiedChildDto.getChildren().isEmpty());

        verify(mindMapNodeMapper, times(2)).insert(any(MindMapNode.class)); // Parent and child
    }

    @Test
    void copyNodeAndChildren_success_nodeWithMultipleLevelsOfChildren() {
        // Arrange
        Long sourceNodeId = 1L;
        Long sourceChildId = 2L;
        Long sourceGrandChildId = 3L;
        Long targetParentNodeId = 10L;

        Long newCopiedParentId = 100L;
        Long newCopiedChildId = 101L;
        Long newCopiedGrandChildId = 102L;

        MindMapNode sourceNode = new MindMapNode(sourceNodeId, null, "Source Parent", null, "REQ-01", null, null, null, null, NodeStatus.PENDING_TEST, true, false);
        MindMapNode sourceChild = new MindMapNode(sourceChildId, sourceNodeId, "Source Child", null, "REQ-01", null, null, null, null, NodeStatus.PENDING_TEST, true, false);
        MindMapNode sourceGrandChild = new MindMapNode(sourceGrandChildId, sourceChildId, "Source GrandChild", null, "REQ-01", null, null, null, null, NodeStatus.PENDING_TEST, true, false);
        MindMapNode targetParentNode = new MindMapNode(targetParentNodeId, null, "Target Parent", null, "REQ-02", null, null, null, null, NodeStatus.TESTED, true, false);

        when(mindMapNodeMapper.selectById(sourceNodeId)).thenReturn(sourceNode);
        when(mindMapNodeMapper.selectById(targetParentNodeId)).thenReturn(targetParentNode);

        // Mock children of sourceNode
        when(mindMapNodeMapper.selectList(argThat(qw -> qw.getCustomSqlSegment() != null && qw.getCustomSqlSegment().contains("parent_id = " + sourceNodeId))))
            .thenReturn(Collections.singletonList(sourceChild));
        // Mock children of sourceChild
        when(mindMapNodeMapper.selectList(argThat(qw -> qw.getCustomSqlSegment() != null && qw.getCustomSqlSegment().contains("parent_id = " + sourceChildId))))
            .thenReturn(Collections.singletonList(sourceGrandChild));
        // Mock children of sourceGrandChild (empty)
        when(mindMapNodeMapper.selectList(argThat(qw -> qw.getCustomSqlSegment() != null && qw.getCustomSqlSegment().contains("parent_id = " + sourceGrandChildId))))
            .thenReturn(Collections.emptyList());

        // Mock inserts
        when(mindMapNodeMapper.insert(argThat(node -> node.getDescription().equals("Source Parent")))).thenAnswer(inv -> {
            inv.getArgument(0, MindMapNode.class).setId(newCopiedParentId); return 1;
        });
        when(mindMapNodeMapper.insert(argThat(node -> node.getDescription().equals("Source Child")))).thenAnswer(inv -> {
            inv.getArgument(0, MindMapNode.class).setId(newCopiedChildId); return 1;
        });
        when(mindMapNodeMapper.insert(argThat(node -> node.getDescription().equals("Source GrandChild")))).thenAnswer(inv -> {
            inv.getArgument(0, MindMapNode.class).setId(newCopiedGrandChildId); return 1;
        });

        // Act
        MindMapNodeDto resultDto = mindMapService.copyNodeAndChildren(sourceNodeId, targetParentNodeId);

        // Assert
        assertNotNull(resultDto);
        assertEquals(newCopiedParentId, resultDto.getId());
        assertEquals(targetParentNodeId, resultDto.getParentId());
        assertEquals("Source Parent", resultDto.getDescription());
        assertEquals(1, resultDto.getChildren().size());

        MindMapNodeDto copiedChildDto = resultDto.getChildren().get(0);
        assertEquals(newCopiedChildId, copiedChildDto.getId());
        assertEquals(newCopiedParentId, copiedChildDto.getParentId());
        assertEquals("Source Child", copiedChildDto.getDescription());
        assertEquals(1, copiedChildDto.getChildren().size());

        MindMapNodeDto copiedGrandChildDto = copiedChildDto.getChildren().get(0);
        assertEquals(newCopiedGrandChildId, copiedGrandChildDto.getId());
        assertEquals(newCopiedChildId, copiedGrandChildDto.getParentId());
        assertEquals("Source GrandChild", copiedGrandChildDto.getDescription());
        assertTrue(copiedGrandChildDto.getChildren().isEmpty());

        verify(mindMapNodeMapper, times(3)).insert(any(MindMapNode.class));
    }

    @Test
    void copyNodeAndChildren_success_copyToRoot() {
        // Arrange
        Long sourceNodeId = 1L;
        Long newGeneratedId = 100L;
        // targetParentNodeId is null for copying to root

        MindMapNode sourceNode = new MindMapNode(sourceNodeId, 50L, "Source Node to be Root", null, "REQ-01", null, null, null, null, NodeStatus.PENDING_TEST, true, false);

        when(mindMapNodeMapper.selectById(sourceNodeId)).thenReturn(sourceNode);
        // No need to mock selectById for targetParentNodeId as it's null

        when(mindMapNodeMapper.selectList(argThat(qw -> qw.getCustomSqlSegment() != null && qw.getCustomSqlSegment().contains("parent_id = " + sourceNodeId))))
            .thenReturn(Collections.emptyList()); // No children for simplicity

        when(mindMapNodeMapper.insert(any(MindMapNode.class))).thenAnswer(invocation -> {
            MindMapNode nodeToInsert = invocation.getArgument(0);
            nodeToInsert.setId(newGeneratedId);
            return 1;
        });

        // Act
        MindMapNodeDto resultDto = mindMapService.copyNodeAndChildren(sourceNodeId, null); // Pass null for targetParentNodeId

        // Assert
        assertNotNull(resultDto);
        assertEquals(newGeneratedId, resultDto.getId());
        assertNull(resultDto.getParentId()); // Should be a root node
        assertEquals(sourceNode.getDescription(), resultDto.getDescription());
        assertTrue(resultDto.getChildren().isEmpty());

        verify(mindMapNodeMapper, times(1)).selectById(sourceNodeId);
        verify(mindMapNodeMapper, never()).selectById(null); // Ensure it doesn't try to fetch a null ID parent
        verify(mindMapNodeMapper, times(1)).insert(argThat(node ->
            node.getParentId() == null && // Important: copied node is root
            node.getDescription().equals(sourceNode.getDescription())
        ));
    }

    @Test
    void copyNodeAndChildren_error_sourceNodeNotFound() {
        // Arrange
        Long sourceNodeId = 1L;
        Long targetParentNodeId = 10L;

        when(mindMapNodeMapper.selectById(sourceNodeId)).thenReturn(null); // Source node does not exist
        // No need to mock targetParentNode if source is not found first

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            mindMapService.copyNodeAndChildren(sourceNodeId, targetParentNodeId);
        });
        assertEquals("Source node with ID " + sourceNodeId + " not found.", exception.getMessage());
        verify(mindMapNodeMapper, times(1)).selectById(sourceNodeId);
        verify(mindMapNodeMapper, never()).selectById(targetParentNodeId);
        verify(mindMapNodeMapper, never()).insert(any(MindMapNode.class));
    }

    @Test
    void copyNodeAndChildren_error_targetParentNodeNotFound() {
        // Arrange
        Long sourceNodeId = 1L;
        Long targetParentNodeId = 10L;

        MindMapNode sourceNode = new MindMapNode(sourceNodeId, null, "Source Node", null, "REQ-01", null, null, null, null, NodeStatus.PENDING_TEST, true, false);

        when(mindMapNodeMapper.selectById(sourceNodeId)).thenReturn(sourceNode);
        when(mindMapNodeMapper.selectById(targetParentNodeId)).thenReturn(null); // Target parent does not exist

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            mindMapService.copyNodeAndChildren(sourceNodeId, targetParentNodeId);
        });
        assertEquals("Target parent node with ID " + targetParentNodeId + " not found.", exception.getMessage());
        verify(mindMapNodeMapper, times(1)).selectById(sourceNodeId);
        verify(mindMapNodeMapper, times(1)).selectById(targetParentNodeId);
        verify(mindMapNodeMapper, never()).insert(any(MindMapNode.class));
    }

    @Test
    void copyNodeAndChildren_error_sourceIdEqualsTargetParentId() {
        // Arrange
        Long sourceNodeId = 1L;
        Long targetParentNodeId = 1L; // Same as sourceNodeId

        // No need to mock mapper calls as this check happens before db interaction

        // Act & Assert
        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            mindMapService.copyNodeAndChildren(sourceNodeId, targetParentNodeId);
        });
        assertEquals("Source node ID and target parent node ID cannot be the same.", exception.getMessage());
        verify(mindMapNodeMapper, never()).selectById(anyLong());
        verify(mindMapNodeMapper, never()).insert(any(MindMapNode.class));
    }

    @Test
    void copyNodeAndChildren_error_cyclicDependency_targetIsDescendant() {
        // Arrange
        Long sourceNodeId = 1L;
        Long childOfSourceId = 2L; // This will be the targetParentNodeId
        Long grandChildOfSourceId = 3L;

        MindMapNode sourceNode = new MindMapNode(sourceNodeId, null, "Source", null, "REQ-01", null, null, null, null, NodeStatus.PENDING_TEST, true, false);
        MindMapNode childNode = new MindMapNode(childOfSourceId, sourceNodeId, "Child (Target Parent)", null, "REQ-01", null, null, null, null, NodeStatus.PENDING_TEST, true, false);
        MindMapNode grandChildNode = new MindMapNode(grandChildOfSourceId, childOfSourceId, "GrandChild", null, "REQ-01", null, null, null, null, NodeStatus.PENDING_TEST, true, false);

        when(mindMapNodeMapper.selectById(sourceNodeId)).thenReturn(sourceNode);
        when(mindMapNodeMapper.selectById(childOfSourceId)).thenReturn(childNode); // Target parent is childOfSource

        // Mocking getAllDescendantsRecursive behavior:
        // When checking children of sourceNode (1L)
        when(mindMapNodeMapper.selectList(argThat(qw -> qw.getCustomSqlSegment() != null && qw.getCustomSqlSegment().contains("parent_id = " + sourceNodeId))))
            .thenReturn(Collections.singletonList(childNode)); // childNode (ID 2L) is a direct child

        // If the target (childOfSourceId = 2L) is a direct child, the recursion for its children might not even be needed
        // for the cycle detection, as it's found directly.
        // However, if the target was grandChildOfSourceId (3L), then this mock would be essential:
        when(mindMapNodeMapper.selectList(argThat(qw -> qw.getCustomSqlSegment() != null && qw.getCustomSqlSegment().contains("parent_id = " + childOfSourceId))))
             .thenReturn(Collections.singletonList(grandChildNode));
        // And children of grandChild (empty)
        when(mindMapNodeMapper.selectList(argThat(qw -> qw.getCustomSqlSegment() != null && qw.getCustomSqlSegment().contains("parent_id = " + grandChildOfSourceId))))
            .thenReturn(Collections.emptyList());


        // Act & Assert
        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            mindMapService.copyNodeAndChildren(sourceNodeId, childOfSourceId); // Target is childOfSourceId (2L)
        });

        String expectedMessage = "Cyclic copy detected: Target parent node (ID: " + childOfSourceId + ") " +
                                 "is a descendant of the source node (ID: " + sourceNodeId + ").";
        assertEquals(expectedMessage, exception.getMessage());

        verify(mindMapNodeMapper, times(1)).selectById(sourceNodeId);
        verify(mindMapNodeMapper, times(1)).selectById(childOfSourceId); // For fetching target parent

        // Verify selectList call for direct children of sourceNodeId
        verify(mindMapNodeMapper, times(1)).selectList(argThat(qw -> qw.getCustomSqlSegment() != null && qw.getCustomSqlSegment().contains("parent_id = " + sourceNodeId)));

        // Other selectList calls for deeper descendants might or might not happen depending on when the cycle is found.
        // For this specific case (target is a direct child), getAllDescendantsRecursive adds childNode (2L) to the list.
        // The loop then immediately finds that descendant.getId() (2L) equals targetParentNodeId (2L).
        // So, selectList for parent_id = 2L (children of childNode) won't be called by getAllDescendantsRecursive in this path.
        verify(mindMapNodeMapper, never()).insert(any(MindMapNode.class));
    }
}
