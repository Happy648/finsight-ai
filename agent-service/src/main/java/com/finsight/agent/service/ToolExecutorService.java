package com.finsight.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.agent.tools.AnomalyDetectionTool;
import com.finsight.agent.tools.RatioCalculatorTool;
import com.finsight.agent.tools.RiskSummaryTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutorService {

    private final AnomalyDetectionTool anomalyDetectionTool;
    private final RatioCalculatorTool ratioCalculatorTool;
    private final RiskSummaryTool riskSummaryTool;
    private final ObjectMapper objectMapper;

    public String execute(String toolName, Map<String, Object> toolInput) {
        try {
            log.info("Executing tool: {}", toolName);

            Object result = switch (toolName) {
                case "detect_anomalies" -> {
                    Map<String, Object> metrics = objectMapper.convertValue(
                            toolInput.get("financial_metrics"), Map.class);
                    yield anomalyDetectionTool.detect(metrics);
                }
                case "calculate_ratios" -> {
                    Map<String, Object> metrics = objectMapper.convertValue(
                            toolInput.get("financial_metrics"), Map.class);
                    yield ratioCalculatorTool.calculate(metrics);
                }
                case "determine_risk_level" -> {
                    List<String> anomalies = objectMapper.convertValue(
                            toolInput.get("anomalies"), List.class);
                    Map<String, Object> ratios = objectMapper.convertValue(
                            toolInput.get("ratios"), Map.class);
                    yield riskSummaryTool.determineRiskLevel(anomalies, ratios);
                }
                default -> throw new IllegalArgumentException(
                        "Unknown tool: " + toolName);
            };

            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            log.error("Tool execution failed for {}: {}", toolName, e.getMessage());
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
