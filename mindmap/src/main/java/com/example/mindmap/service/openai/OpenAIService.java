package com.example.mindmap.service.openai;

import com.example.mindmap.service.impl.MindMapServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.example.mindmap.service.openai.dto.OpenAIChatRequestDto;
import com.example.mindmap.service.openai.dto.OpenAIMessageDto;
import com.example.mindmap.service.openai.dto.OpenAIChatResponseDto;
import com.example.mindmap.service.openai.dto.GPTTestCaseStructureDto; // For parsing the content
import com.fasterxml.jackson.databind.ObjectMapper; // For parsing JSON content
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
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

//    private static final String OPENAI_CHAT_COMPLETIONS_URL =  "https://huilianyi-ai.openai.azure.com/openai/deployments/gpt-4.1/chat/completions?api-version=2025-01-01-preview";

    private static final String OPENAI_CHAT_COMPLETIONS_URL =  "https://huilianyi-ai.openai.azure.com/openai/deployments/o1/chat/completions?api-version=2025-01-01-preview";

    public OpenAIService(WebClient.Builder webClientBuilder, @Value("${openai.api.key}") String apiKey, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.clientConnector(new ReactorClientHttpConnector(
                HttpClient.create()
                        .responseTimeout(Duration.ofMinutes(5))))
                .baseUrl(OPENAI_CHAT_COMPLETIONS_URL)

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
        OpenAIChatRequestDto requestDto = new OpenAIChatRequestDto("o1-preview", messages, responseFormatMap);

        return this.webClient.post()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(requestDto)
                .retrieve()
                .bodyToMono(OpenAIChatResponseDto.class) // Deserialize the outer response to OpenAIChatResponseDto
                .doOnError(error -> logger.warn("OpenAI API call failed, attempting retry. Error: {}", error.getMessage()))
                .retry(1)
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

    /**
     * 分析需求文本，提取功能点
     * @param requirementText 原始需求文本
     * @return 分析后的功能点内容
     */
    public Mono<String> analyzeRequirementFunctionPoints(String requirementText) {
        if (apiKey == null || apiKey.isEmpty() || "YOUR_OPENAI_API_KEY_PLACEHOLDER".equals(apiKey)) {
            return Mono.error(new IllegalStateException("OpenAI API key is not configured or is still a placeholder."));
        }

        String systemPrompt = "你是一个资深的需求分析专家。请分析需求文档，整理需求为开发的功能点。";
        String userPrompt = requirementText + " 按照文档顺序，整理需求为开发的功能点，每一条功能点都是可以直接对照开发的，充分考虑需求标题，保留功能点对应的菜单界面，以及生效前提。"+
        "分析当前功能点是否是已有功能并标记，同时返回引用的需求原文，不要想象和推测需求文档不存在的内容.";

        List<OpenAIMessageDto> messages = new ArrayList<>();
        messages.add(new OpenAIMessageDto("system", systemPrompt));
        messages.add(new OpenAIMessageDto("user", userPrompt));

        OpenAIChatRequestDto requestDto = new OpenAIChatRequestDto("o1-preview", messages, null);

        return this.webClient.post()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(requestDto)
                .retrieve()
                .bodyToMono(OpenAIChatResponseDto.class)
                .doOnError(error -> logger.warn("OpenAI API call for requirement analysis failed, attempting retry. Error: {}", error.getMessage()))
                .retry(1)
                .flatMap(response -> {
                    if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                        OpenAIChatResponseDto.Choice firstChoice = response.getChoices().get(0);
                        if (firstChoice.getMessage() != null && firstChoice.getMessage().getContent() != null) {
                            String analyzedContent = firstChoice.getMessage().getContent();
                            logger.info("Requirement analysis response: {}", analyzedContent);
                            return Mono.just(analyzedContent);
                        }
                    }
                    return Mono.error(new RuntimeException("Invalid or empty response from OpenAI for requirement analysis."));
                })
                .doOnError(error -> {
                    logger.error("OpenAI API call for requirement analysis failed: {}", error.getMessage());
                });
    }
}
