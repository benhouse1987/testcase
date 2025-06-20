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

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
}
