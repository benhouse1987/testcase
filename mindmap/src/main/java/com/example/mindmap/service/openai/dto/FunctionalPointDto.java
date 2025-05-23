package com.example.mindmap.service.openai.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Import

@JsonIgnoreProperties(ignoreUnknown = true)
public class FunctionalPointDto {
    private String functionalPointName;
    private List<ScenarioDto> testScenarios;

    // Getters and Setters
    public String getFunctionalPointName() { return functionalPointName; }
    public void setFunctionalPointName(String functionalPointName) { this.functionalPointName = functionalPointName; }
    public List<ScenarioDto> getTestScenarios() { return testScenarios; }
    public void setTestScenarios(List<ScenarioDto> testScenarios) { this.testScenarios = testScenarios; }
}
