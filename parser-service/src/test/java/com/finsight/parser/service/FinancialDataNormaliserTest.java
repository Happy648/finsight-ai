package com.finsight.parser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialDataNormaliserTest {

    private FinancialDataNormaliser normaliser;

    @BeforeEach
    void setUp() {
        normaliser = new FinancialDataNormaliser();
    }

    @Test
    void normalise_fullFinancialReport_extractsAllMetrics() {
        String rawText = """
                Annual Financial Report 2024
                Total Revenue: $5,200 million
                Net Income: $1,100 million
                Total Debt: $3,400 million
                Operating Cash Flow: $890 million
                Basic Earnings Per Share: $4.23
                """;

        Map<String, Object> metrics = normaliser.normalise(rawText);

        assertThat(metrics).containsKey("revenue");
        assertThat(metrics).containsKey("netIncome");
        assertThat(metrics).containsKey("totalDebt");
        assertThat(metrics).containsKey("cashFlow");
        assertThat(metrics).containsKey("earningsPerShare");
        assertThat((Double) metrics.get("revenue")).isEqualTo(5_200_000_000.0);
        assertThat((Boolean) metrics.get("missingRevenue")).isFalse();
    }

    @Test
    void normalise_netLossReport_flagsNegativeNetIncome() {
        String rawText = """
                Q3 Financial Summary
                Total Revenue: $2,100 million
                Net Loss: $340 million
                Total Debt: $1,200 million
                """;

        Map<String, Object> metrics = normaliser.normalise(rawText);

        assertThat((Boolean) metrics.get("hasNegativeNetIncome")).isTrue();
    }

    @Test
    void normalise_missingRevenue_flagsMissingRevenue() {
        String rawText = """
                Partial Report
                Net Income: $500 million
                Total Debt: $800 million
                """;

        Map<String, Object> metrics = normaliser.normalise(rawText);

        assertThat((Boolean) metrics.get("missingRevenue")).isTrue();
        assertThat((Boolean) metrics.get("missingCashFlow")).isTrue();
    }

    @Test
    void normalise_emptyText_returnsAnomalyFlagsOnly() {
        Map<String, Object> metrics = normaliser.normalise("");

        assertThat(metrics).containsKey("missingRevenue");
        assertThat(metrics).containsKey("missingCashFlow");
        assertThat(metrics).containsKey("hasNegativeNetIncome");
        assertThat((Boolean) metrics.get("missingRevenue")).isTrue();
    }

    @Test
    void normalise_billionSuffix_scalesCorrectly() {
        String rawText = """
                Revenue: $1.5 billion
                Net Income: $200 million
                """;

        Map<String, Object> metrics = normaliser.normalise(rawText);

        assertThat((Double) metrics.get("revenue")).isEqualTo(1_500_000_000.0);
        assertThat((Double) metrics.get("netIncome")).isEqualTo(200_000_000.0);
    }
}