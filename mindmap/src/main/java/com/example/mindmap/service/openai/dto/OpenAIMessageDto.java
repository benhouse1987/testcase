package com.example.mindmap.service.openai.dto;

public class OpenAIMessageDto {
    private String role;
    private String content;

    public OpenAIMessageDto(String role, String content) {
        this.role = role;
        this.content = content;
    }

    // Getters and Setters
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
