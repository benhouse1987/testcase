package com.example.mindmap.service.openai.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Import

@JsonIgnoreProperties(ignoreUnknown = true) // Important for OpenAI responses
public class OpenAIChatResponseDto {
    private List<Choice> choices;
    // Add other fields like 'usage' if needed

    // Getters and Setters
    public List<Choice> getChoices() { return choices; }
    public void setChoices(List<Choice> choices) { this.choices = choices; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private OpenAIMessageDto message;
        // Add other fields like 'finish_reason' if needed

        // Getters and Setters
        public OpenAIMessageDto getMessage() { return message; }
        public void setMessage(OpenAIMessageDto message) { this.message = message; }
    }
}
