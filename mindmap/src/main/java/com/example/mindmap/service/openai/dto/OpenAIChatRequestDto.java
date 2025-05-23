package com.example.mindmap.service.openai.dto;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty; // Import for JsonProperty

public class OpenAIChatRequestDto {
    private String model;
    private List<OpenAIMessageDto> messages;
    @JsonProperty("response_format") // Ensure correct serialization for "response_format"
    private Map<String, String> responseFormat; 
    // Add other parameters like temperature, max_tokens if needed later

    public OpenAIChatRequestDto(String model, List<OpenAIMessageDto> messages, Map<String, String> responseFormat) {
        this.model = model;
        this.messages = messages;
        this.responseFormat = responseFormat;
    }

    // Getters and Setters
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<OpenAIMessageDto> getMessages() { return messages; }
    public void setMessages(List<OpenAIMessageDto> messages) { this.messages = messages; }
    public Map<String, String> getResponseFormat() { return responseFormat; }
    public void setResponseFormat(Map<String, String> responseFormat) { this.responseFormat = responseFormat; }
}
