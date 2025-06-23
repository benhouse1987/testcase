package com.example.mindmap.service.openai;

import com.example.mindmap.service.impl.MindMapServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.example.mindmap.service.openai.dto.OpenAIChatRequestDto;
import com.example.mindmap.service.openai.dto.OpenAIMessageDto;
import com.example.mindmap.service.openai.dto.OpenAIChatResponseDto;
import com.example.mindmap.service.openai.dto.GPTTestCaseStructureDto; // For parsing the content
import com.fasterxml.jackson.databind.ObjectMapper; // For parsing JSON content
import java.util.ArrayList; // For creating messages list
import java.util.List; // For List interface
import java.util.Map; // For Map interface for responseFormatMap
import java.util.HashMap; // Added for JDK 1.8 compatibility
import java.util.Collections; // Added for JDK 1.8 compatibility


@Service
public class OpenAIService {

    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper objectMapper; // For parsing JSON string to DTO
    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);

    private static final String OPENAI_CHAT_COMPLETIONS_URL =  "https://huilianyi-ai.openai.azure.com/openai/deployments/gpt-4.1/chat/completions?api-version=2025-01-01-preview";

    public OpenAIService(WebClient.Builder webClientBuilder, @Value("${openai.api.key}") String apiKey, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl(OPENAI_CHAT_COMPLETIONS_URL)
                                         .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                         .build();
        this.apiKey = apiKey;
        this.objectMapper = objectMapper; // Inject ObjectMapper
    }

    public Mono<GPTTestCaseStructureDto> generateTestCases(String requirementText, String systemPrompt, String userPromptPrefix) {
        if (apiKey == null || apiKey.isEmpty() || "YOUR_OPENAI_API_KEY_PLACEHOLDER".equals(apiKey)) {
            // Consider logging a warning here as well
            return Mono.error(new IllegalStateException("OpenAI API key is not configured or is still a placeholder."));
        }

        List<OpenAIMessageDto> messages = new ArrayList<>();
        messages.add(new OpenAIMessageDto("system", systemPrompt));
        messages.add(new OpenAIMessageDto("user", userPromptPrefix + "\n\n" + requirementText));

        // Specify JSON response format
        Map<String, String> tempMap = new HashMap<>();
        tempMap.put("type", "json_object");
        Map<String, String> responseFormatMap = Collections.unmodifiableMap(tempMap);
        OpenAIChatRequestDto requestDto = new OpenAIChatRequestDto("gpt-4o-mini", messages, responseFormatMap);

        return this.webClient.post()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(requestDto)
                .retrieve()
                .bodyToMono(OpenAIChatResponseDto.class) // Deserialize the outer response to OpenAIChatResponseDto
                .flatMap(response -> {
                    if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                        OpenAIChatResponseDto.Choice firstChoice = response.getChoices().get(0);
                        if (firstChoice.getMessage() != null && firstChoice.getMessage().getContent() != null) {
                            String jsonContent = firstChoice.getMessage().getContent();
                            logger.info("llm response {}",jsonContent);
                            try {
                                // Parse the JSON string content into GPTTestCaseStructureDto
                                GPTTestCaseStructureDto structuredContent = objectMapper.readValue(jsonContent, GPTTestCaseStructureDto.class);
                                return Mono.just(structuredContent);
                            } catch (Exception e) {
                                // Log parsing error
                                System.err.println("Failed to parse GPT JSON content: " + e.getMessage());
                                return Mono.error(new RuntimeException("Failed to parse GPT JSON content.", e));
                            }
                        }
                    }
                    return Mono.error(new RuntimeException("Invalid or empty response from OpenAI."));
                })
                .doOnError(error -> {
                    System.err.println("OpenAI API call or processing failed: " + error.getMessage());
                    // Consider more specific error handling based on WebClientResponseException
                });
    }
}
