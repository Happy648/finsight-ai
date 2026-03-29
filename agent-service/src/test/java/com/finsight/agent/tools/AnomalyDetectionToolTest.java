package com.finsight.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyDetectionToolTest {

    private AnomalyDetectionTool tool;

    @BeforeEach
    void setUp() {
        tool = new AnomalyDetectionTool();
    }

    @Test
    void detect_missingRevenue_flagsCritical() {
        Map<String, Object> metrics = Map.of(
                "missingRevenue", true,
                "missingCashFlow", false,
                "hasNegativeNetIncome", false
        );

        List<String> anomalies = tool.detect(metrics);

        assertThat(anomalies).anyMatch(a -> a.startsWith("CRITICAL"));
        assertThat(anomalies).anyMatch(a -> a.contains("Revenue data is missing"));
    }

    @Test
    void detect_highDebtRatio_flagsHighRisk() {
        Map<String, Object> metrics = Map.of(
                "missingRevenue", false,
                "missingCashFlow", false,
                "hasNegativeNetIncome", false,
                "totalDebt", 10_000_000_000.0,
                "revenue", 2_000_000_000.0    // debt/revenue = 5.0 — above threshold
        );

        List<String> anomalies = tool.detect(metrics);

        assertThat(anomalies).anyMatch(a -> a.startsWith("HIGH RISK"));
        assertThat(anomalies).anyMatch(a -> a.contains("Debt-to-revenue"));
    }

    @Test
    void detect_netLoss_flagsAlert() {
        Map<String, Object> metrics = Map.of(
                "missingRevenue", false,
                "missingCashFlow", false,
                "hasNegativeNetIncome", true
        );

        List<String> anomalies = tool.detect(metrics);

        assertThat(anomalies).anyMatch(a -> a.startsWith("ALERT"));
        assertThat(anomalies).anyMatch(a -> a.contains("net loss"));
    }

    @Test
    void detect_healthyFinancials_returnsNoAnomalies() {
        Map<String, Object> metrics = Map.of(
                "missingRevenue", false,
                "missingCashFlow", false,
                "hasNegativeNetIncome", false,
                "revenue", 5_000_000_000.0,
                "totalDebt", 1_000_000_000.0,  // debt/revenue = 0.2 — healthy
                "cashFlow", 500_000_000.0
        );

        List<String> anomalies = tool.detect(metrics);

        assertThat(anomalies).isEmpty();
    }
}