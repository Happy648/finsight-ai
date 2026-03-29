package com.finsight.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RatioCalculatorToolTest {

    private RatioCalculatorTool tool;

    @BeforeEach
    void setUp() {
        tool = new RatioCalculatorTool();
    }

    @Test
    void calculate_fullMetrics_returnsAllRatios() {
        Map<String, Object> metrics = Map.of(
                "revenue", 5_200_000_000.0,
                "netIncome", 1_100_000_000.0,
                "totalDebt", 3_400_000_000.0,
                "cashFlow", 890_000_000.0
        );

        Map<String, Object> ratios = tool.calculate(metrics);

        assertThat(ratios).containsKey("profitMarginPct");
        assertThat(ratios).containsKey("debtToRevenueRatio");
        assertThat(ratios).containsKey("cashFlowToRevenuePct");
        assertThat((Double) ratios.get("profitMarginPct")).isEqualTo(21.15);
        assertThat((Double) ratios.get("debtToRevenueRatio")).isEqualTo(0.65);
    }

    @Test
    void calculate_missingNetIncome_skipsProfitMargin() {
        Map<String, Object> metrics = Map.of(
                "revenue", 5_200_000_000.0,
                "totalDebt", 3_400_000_000.0
        );

        Map<String, Object> ratios = tool.calculate(metrics);

        assertThat(ratios).doesNotContainKey("profitMarginPct");
        assertThat(ratios).containsKey("debtToRevenueRatio");
    }

    @Test
    void calculate_emptyMetrics_returnsEmptyRatios() {
        Map<String, Object> ratios = tool.calculate(Map.of());

        assertThat(ratios).isEmpty();
    }
}