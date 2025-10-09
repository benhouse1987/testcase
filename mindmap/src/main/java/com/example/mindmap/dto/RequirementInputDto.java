package com.example.mindmap.dto;

import javax.validation.constraints.NotBlank;

public class RequirementInputDto {

    @NotBlank(message = "Requirement ID cannot be blank")
    private String requirementId;

    @NotBlank(message = "Requirement title cannot be blank")
    private String requirementTitle;

    @NotBlank(message = "Original requirement text cannot be blank")
    private String originalRequirementText;

    private String docToken;

    private String analyzedContent; // 存储大模型分析后的功能点内容

    // Constructors, Getters, and Setters (or use Lombok @Data)
    public RequirementInputDto() {}

    public RequirementInputDto(String requirementId, String requirementTitle, String originalRequirementText) {
        this.requirementId = requirementId;
        this.requirementTitle = requirementTitle;
        this.originalRequirementText = originalRequirementText;
    }

    public String getRequirementId() { return requirementId; }
    public void setRequirementId(String requirementId) { this.requirementId = requirementId; }
    public String getRequirementTitle() { return requirementTitle; }
    public void setRequirementTitle(String requirementTitle) { this.requirementTitle = requirementTitle; }
    public String getOriginalRequirementText() { return originalRequirementText; }
    public void setOriginalRequirementText(String originalRequirementText) { this.originalRequirementText = originalRequirementText; }

    public String getDocToken() {
        return docToken;
    }

    public void setDocToken(String docToken) {
        this.docToken = docToken;
    }

    public String getAnalyzedContent() {
        return analyzedContent;
    }

    public void setAnalyzedContent(String analyzedContent) {
        this.analyzedContent = analyzedContent;
    }
}
