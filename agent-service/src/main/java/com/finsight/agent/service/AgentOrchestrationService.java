package com.finsight.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finsight.agent.event.PdfParsedEvent;
import com.finsight.agent.model.AgentResult;
import com.finsight.agent.repository.AgentResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrationService {

    private final RestClient anthropicRestClient;
    private final ToolExecutorService toolExecutorService;
    private final AgentResultRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.model}")
    private String model;

    @Value("${anthropic.max-tokens}")
    private int maxTokens;

    @Retryable(
            retryFor = { Exception.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public AgentResult orchestrate(PdfParsedEvent event) throws JsonProcessingException {
        long startTime = System.currentTimeMillis();
        log.info("Starting agent orchestration for documentId={}", event.getDocumentId());

        // build the initial message to Claude
        String userMessage = buildUserMessage(event);

        // conversation history — grows as tool calls happen
        List<ObjectNode> messages = new ArrayList<>();
        ObjectNode userNode = objectMapper.createObjectNode();
        userNode.put("role", "user");
        userNode.put("content", userMessage);
        messages.add(userNode);

        // tool definitions Claude can choose from
        ArrayNode tools = buildToolDefinitions();

        // agentic loop
        String finalSummary = null;
        List<String> anomalies = new ArrayList<>();
        Map<String, Object> ratios = Map.of();
        String riskLevel = "UNKNOWN";
        int toolCallCount = 0;

        while (true) {
            // call Claude API
            JsonNode response = callClaude(messages, tools);
            String stopReason = response.get("stop_reason").asText();

            log.info("Claude stop_reason={} for documentId={}",
                    stopReason, event.getDocumentId());

            if ("end_turn".equals(stopReason)) {
                // Claude is done — extract final text response
                finalSummary = extractTextContent(response);
                break;
            }

            if ("tool_use".equals(stopReason)) {
                // Claude wants to call a tool
                // 1. Add Claude's response to conversation history
                ObjectNode assistantNode = objectMapper.createObjectNode();
                assistantNode.put("role", "assistant");
                assistantNode.set("content", response.get("content"));
                messages.add(assistantNode);

                // 2. Execute each tool Claude requested
                ArrayNode toolResults = objectMapper.createArrayNode();

                for (JsonNode contentBlock : response.get("content")) {
                    if ("tool_use".equals(contentBlock.get("type").asText())) {
                        String toolName = contentBlock.get("name").asText();
                        String toolUseId = contentBlock.get("id").asText();
                        Map<String, Object> toolInput = objectMapper.convertValue(
                                contentBlock.get("input"), Map.class);

                        log.info("Executing tool={} for documentId={}",
                                toolName, event.getDocumentId());

                        String toolResult = toolExecutorService.execute(toolName, toolInput);
                        toolCallCount++;

                        // store results for AgentResult model
                        if ("detect_anomalies".equals(toolName)) {
                            anomalies = objectMapper.readValue(
                                    toolResult,
                                    objectMapper.getTypeFactory()
                                            .constructCollectionType(List.class, String.class));
                        } else if ("calculate_ratios".equals(toolName)) {
                            ratios = objectMapper.readValue(toolResult, Map.class);
                        } else if ("determine_risk_level".equals(toolName)) {
                            riskLevel = toolResult.replace("\"", "");
                        }

                        // build tool result block
                        ObjectNode resultBlock = objectMapper.createObjectNode();
                        resultBlock.put("type", "tool_result");
                        resultBlock.put("tool_use_id", toolUseId);
                        resultBlock.put("content", toolResult);
                        toolResults.add(resultBlock);
                    }
                }

                // 3. Add tool results back to conversation so Claude can continue
                ObjectNode toolResultNode = objectMapper.createObjectNode();
                toolResultNode.put("role", "user");
                toolResultNode.set("content", toolResults);
                messages.add(toolResultNode);
            }
        }

        long processingTime = System.currentTimeMillis() - startTime;
        log.info("Agent orchestration complete in {}ms for documentId={}",
                processingTime, event.getDocumentId());

        // save and return result
        AgentResult result = AgentResult.builder()
                .id(UUID.randomUUID().toString())
                .documentId(event.getDocumentId())
                .filename(event.getFilename())
                .riskSummary(finalSummary)
                .anomalies(anomalies)
                .calculatedRatios(ratios)
                .riskLevel(riskLevel)
                .toolCallCount(toolCallCount)
                .processingTimeMs(processingTime)
                .generatedAt(Instant.now())
                .build();

        repository.save(result);
        return result;
    }

    private String buildUserMessage(PdfParsedEvent event) {
        return String.format("""
                You are a financial risk analyst. Analyse the following financial report data.
                
                Document: %s
                Pages: %d
                
                Financial Metrics:
                %s
                
                Please perform the following steps in order:
                1. Call detect_anomalies with the financial metrics to identify risks
                2. Call calculate_ratios to compute key financial ratios
                3. Call determine_risk_level with the anomalies and ratios
                4. Provide a concise plain-English risk summary (3-5 sentences) for a non-technical audience
                
                Be specific about any concerns found in the data.
                """,
                event.getFilename(),
                event.getPageCount(),
                event.getFinancialMetrics().toString()
        );
    }

    private JsonNode callClaude(List<ObjectNode> messages, ArrayNode tools) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("max_tokens", maxTokens);
            requestBody.set("tools", tools);
            requestBody.set("messages",
                    objectMapper.valueToTree(messages));

            String response = anthropicRestClient.post()
                    .uri("/v1/messages")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readTree(response);

        } catch (Exception e) {
            log.error("Claude API call failed: {}", e.getMessage());
            throw new RuntimeException("Claude API call failed", e);
        }
    }

    private ArrayNode buildToolDefinitions() {
        ArrayNode tools = objectMapper.createArrayNode();

        // detect_anomalies tool
        ObjectNode anomalyTool = objectMapper.createObjectNode();
        anomalyTool.put("name", "detect_anomalies");
        anomalyTool.put("description",
                "Detects financial anomalies and risks from extracted metrics");
        ObjectNode anomalyInput = objectMapper.createObjectNode();
        anomalyInput.put("type", "object");
        ObjectNode anomalyProps = objectMapper.createObjectNode();
        ObjectNode metricsSchema = objectMapper.createObjectNode();
        metricsSchema.put("type", "object");
        metricsSchema.put("description", "Financial metrics extracted from the PDF");
        anomalyProps.set("financial_metrics", metricsSchema);
        anomalyInput.set("properties", anomalyProps);
        anomalyInput.set("required",
                objectMapper.createArrayNode().add("financial_metrics"));
        anomalyTool.set("input_schema", anomalyInput);
        tools.add(anomalyTool);

        // calculate_ratios tool
        ObjectNode ratioTool = objectMapper.createObjectNode();
        ratioTool.put("name", "calculate_ratios");
        ratioTool.put("description",
                "Calculates key financial ratios like profit margin and debt ratio");
        ObjectNode ratioInput = objectMapper.createObjectNode();
        ratioInput.put("type", "object");
        ObjectNode ratioProps = objectMapper.createObjectNode();
        ObjectNode ratioMetrics = objectMapper.createObjectNode();
        ratioMetrics.put("type", "object");
        ratioMetrics.put("description", "Financial metrics to calculate ratios from");
        ratioProps.set("financial_metrics", ratioMetrics);
        ratioInput.set("properties", ratioProps);
        ratioInput.set("required",
                objectMapper.createArrayNode().add("financial_metrics"));
        ratioTool.set("input_schema", ratioInput);
        tools.add(ratioTool);

        // determine_risk_level tool
        ObjectNode riskTool = objectMapper.createObjectNode();
        riskTool.put("name", "determine_risk_level");
        riskTool.put("description",
                "Determines overall risk level based on anomalies and ratios");
        ObjectNode riskInput = objectMapper.createObjectNode();
        riskInput.put("type", "object");
        ObjectNode riskProps = objectMapper.createObjectNode();
        ObjectNode anomaliesSchema = objectMapper.createObjectNode();
        anomaliesSchema.put("type", "array");
        anomaliesSchema.put("description", "List of detected anomalies");
        ObjectNode ratiosSchema = objectMapper.createObjectNode();
        ratiosSchema.put("type", "object");
        ratiosSchema.put("description", "Calculated financial ratios");
        riskProps.set("anomalies", anomaliesSchema);
        riskProps.set("ratios", ratiosSchema);
        riskInput.set("properties", riskProps);
        riskInput.set("required",
                objectMapper.createArrayNode().add("anomalies").add("ratios"));
        riskTool.set("input_schema", riskInput);
        tools.add(riskTool);

        return tools;
    }

    private String extractTextContent(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.get("content")) {
            if ("text".equals(block.get("type").asText())) {
                text.append(block.get("text").asText());
            }
        }
        return text.toString();
    }
}
