package com.example.mindmap.service.openai.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Import

@JsonIgnoreProperties(ignoreUnknown = true)
public class GPTTestCaseStructureDto {
    private List<FunctionalPointDto> functionalPoints;

    // Getters and Setters
    public List<FunctionalPointDto> getFunctionalPoints() { return functionalPoints; }
    public void setFunctionalPoints(List<FunctionalPointDto> functionalPoints) { this.functionalPoints = functionalPoints; }
}
