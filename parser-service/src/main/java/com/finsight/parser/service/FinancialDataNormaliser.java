package com.finsight.parser.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class FinancialDataNormaliser {

    // patterns to extract common financial figures from report text
    private static final Pattern REVENUE_PATTERN =
            Pattern.compile("(?i)(?:total\\s+)?revenue[:\\s]+\\$?([\\d,]+\\.?\\d*)\\s*(million|billion)?");

    private static final Pattern NET_INCOME_PATTERN =
            Pattern.compile("(?i)net\\s+income[:\\s]+\\$?([\\d,]+\\.?\\d*)\\s*(million|billion)?");

    private static final Pattern TOTAL_DEBT_PATTERN =
            Pattern.compile("(?i)total\\s+debt[:\\s]+\\$?([\\d,]+\\.?\\d*)\\s*(million|billion)?");

    private static final Pattern CASH_FLOW_PATTERN =
            Pattern.compile("(?i)(?:operating\\s+)?cash\\s+flow[:\\s]+\\$?([\\d,]+\\.?\\d*)\\s*(million|billion)?");

    private static final Pattern EARNINGS_PER_SHARE_PATTERN =
            Pattern.compile("(?i)(?:basic\\s+)?earnings\\s+per\\s+share[:\\s]+\\$?([\\d.]+)");

    public Map<String, Object> normalise(String rawText) {
        Map<String, Object> metrics = new HashMap<>();

        extractMetric(rawText, REVENUE_PATTERN, "revenue")
                .ifPresent(v -> metrics.put("revenue", v));

        extractMetric(rawText, NET_INCOME_PATTERN, "netIncome")
                .ifPresent(v -> metrics.put("netIncome", v));

        extractMetric(rawText, TOTAL_DEBT_PATTERN, "totalDebt")
                .ifPresent(v -> metrics.put("totalDebt", v));

        extractMetric(rawText, CASH_FLOW_PATTERN, "cashFlow")
                .ifPresent(v -> metrics.put("cashFlow", v));

        extractMetric(rawText, EARNINGS_PER_SHARE_PATTERN, "earningsPerShare")
                .ifPresent(v -> metrics.put("earningsPerShare", v));

        // anomaly flags — agent-service uses these as hints
        metrics.put("hasNegativeNetIncome",
                rawText.toLowerCase().contains("net loss"));

        metrics.put("missingRevenue", !metrics.containsKey("revenue"));
        metrics.put("missingCashFlow", !metrics.containsKey("cashFlow"));

        log.info("Normalised {} financial metrics from raw text", metrics.size());
        return metrics;
    }

    private java.util.Optional<Double> extractMetric(
            String text, Pattern pattern, String metricName) {
        try {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String valueStr = matcher.group(1).replace(",", "");
                double value = Double.parseDouble(valueStr);

                // scale to actual value if million/billion suffix found
                if (matcher.groupCount() >= 2 && matcher.group(2) != null) {
                    String suffix = matcher.group(2).toLowerCase();
                    if (suffix.equals("billion")) value *= 1_000_000_000;
                    else if (suffix.equals("million")) value *= 1_000_000;
                }
                return java.util.Optional.of(value);
            }
        } catch (Exception e) {
            log.warn("Failed to extract metric '{}': {}", metricName, e.getMessage());
        }
        return java.util.Optional.empty();
    }
}