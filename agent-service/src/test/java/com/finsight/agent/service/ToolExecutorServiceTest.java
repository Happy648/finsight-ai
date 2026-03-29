package com.finsight.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.agent.tools.AnomalyDetectionTool;
import com.finsight.agent.tools.RatioCalculatorTool;
import com.finsight.agent.tools.RiskSummaryTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolExecutorServiceTest {

    @Mock
    private AnomalyDetectionTool anomalyDetectionTool;

    @Mock
    private RatioCalculatorTool ratioCalculatorTool;

    @Mock
    private RiskSummaryTool riskSummaryTool;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private ToolExecutorService toolExecutorService;

    @Test
    void execute_detectAnomalies_returnsJsonResult() {
        // Arrange
        when(anomalyDetectionTool.detect(any()))
                .thenReturn(List.of("CRITICAL: Revenue data is missing"));

        Map<String, Object> input = Map.of(
                "financial_metrics", Map.of(
                        "missingRevenue", true,
                        "hasNegativeNetIncome", false
                )
        );

        // Act
        String result = toolExecutorService.execute("detect_anomalies", input);

        // Assert
        assertThat(result).contains("CRITICAL");
        assertThat(result).contains("Revenue data is missing");
        verify(anomalyDetectionTool).detect(any());
    }

    @Test
    void execute_calculateRatios_returnsJsonResult() {
        // Arrange
        when(ratioCalculatorTool.calculate(any()))
                .thenReturn(Map.of("profitMarginPct", 21.15));

        Map<String, Object> input = Map.of(
                "financial_metrics", Map.of(
                        "revenue", 5_200_000_000.0,
                        "netIncome", 1_100_000_000.0
                )
        );

        // Act
        String result = toolExecutorService.execute("calculate_ratios", input);

        // Assert
        assertThat(result).contains("profitMarginPct");
        verify(ratioCalculatorTool).calculate(any());
    }

    @Test
    void execute_determineRiskLevel_returnsRiskLevel() {
        // Arrange
        when(riskSummaryTool.determineRiskLevel(any(), any()))
                .thenReturn("HIGH");

        Map<String, Object> input = Map.of(
                "anomalies", List.of("ALERT: Net loss detected"),
                "ratios", Map.of("debtToRevenueRatio", 2.5)
        );

        // Act
        String result = toolExecutorService.execute("determine_risk_level", input);

        // Assert
        assertThat(result).contains("HIGH");
        verify(riskSummaryTool).determineRiskLevel(any(), any());
    }

    @Test
    void execute_unknownTool_returnsErrorJson() {
        // Act
        String result = toolExecutorService.execute("unknown_tool", Map.of());

        // Assert
        assertThat(result).contains("error");
        assertThat(result).contains("Unknown tool");
    }
}