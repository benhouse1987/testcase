package com.example.mindmap.service.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Import

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioDto {
    private String testCaseId;
    private String testCaseGroup;
    // functionalPointName is already in the parent FunctionalPointDto
    private String quotedRequirementText; // "引用的需求原文"
    private String prerequisites;         // "前置准备"
    private String testSteps;             // "测试步骤"
    private String expectedResults;       // "预期结果"

    // Getters and Setters
    public String getTestCaseId() { return testCaseId; }
    public void setTestCaseId(String testCaseId) { this.testCaseId = testCaseId; }
    public String getTestCaseGroup() { return testCaseGroup; }
    public void setTestCaseGroup(String testCaseGroup) { this.testCaseGroup = testCaseGroup; }
    public String getQuotedRequirementText() { return quotedRequirementText; }
    public void setQuotedRequirementText(String quotedRequirementText) { this.quotedRequirementText = quotedRequirementText; }
    public String getPrerequisites() { return prerequisites; }
    public void setPrerequisites(String prerequisites) { this.prerequisites = prerequisites; }
    public String getTestSteps() { return testSteps; }
    public void setTestSteps(String testSteps) { this.testSteps = testSteps; }
    public String getExpectedResults() { return expectedResults; }
    public void setExpectedResults(String expectedResults) { this.expectedResults = expectedResults; }
}
