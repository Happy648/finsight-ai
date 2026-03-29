package com.finsight.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AnomalyDetectionTool {

    public List<String> detect(Map<String, Object> financialMetrics) {
        List<String> anomalies = new ArrayList<>();

        // missing critical metrics
        if (Boolean.TRUE.equals(financialMetrics.get("missingRevenue"))) {
            anomalies.add("CRITICAL: Revenue data is missing from the report");
        }
        if (Boolean.TRUE.equals(financialMetrics.get("missingCashFlow"))) {
            anomalies.add("WARNING: Cash flow data is missing from the report");
        }

        // negative net income
        if (Boolean.TRUE.equals(financialMetrics.get("hasNegativeNetIncome"))) {
            anomalies.add("ALERT: Company is reporting a net loss");
        }

        // debt ratio check
        if (financialMetrics.containsKey("totalDebt") &&
                financialMetrics.containsKey("revenue")) {
            double debt = ((Number) financialMetrics.get("totalDebt")).doubleValue();
            double revenue = ((Number) financialMetrics.get("revenue")).doubleValue();
            double debtToRevenue = debt / revenue;

            if (debtToRevenue > 2.0) {
                anomalies.add(String.format(
                        "HIGH RISK: Debt-to-revenue ratio is %.2f — exceeds 2.0 threshold",
                        debtToRevenue));
            }
        }

        // negative cash flow
        if (financialMetrics.containsKey("cashFlow")) {
            double cashFlow = ((Number) financialMetrics.get("cashFlow")).doubleValue();
            if (cashFlow < 0) {
                anomalies.add("WARNING: Negative operating cash flow detected");
            }
        }

        log.info("Detected {} anomalies", anomalies.size());
        return anomalies;
    }
}