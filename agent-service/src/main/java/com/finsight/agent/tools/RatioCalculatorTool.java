package com.finsight.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class RatioCalculatorTool {

    public Map<String, Object> calculate(Map<String, Object> financialMetrics) {
        Map<String, Object> ratios = new HashMap<>();

        // profit margin
        if (financialMetrics.containsKey("revenue") &&
                financialMetrics.containsKey("netIncome")) {
            double revenue = ((Number) financialMetrics.get("revenue")).doubleValue();
            double netIncome = ((Number) financialMetrics.get("netIncome")).doubleValue();
            double profitMargin = (netIncome / revenue) * 100;
            ratios.put("profitMarginPct", Math.round(profitMargin * 100.0) / 100.0);
        }

        // debt to revenue ratio
        if (financialMetrics.containsKey("totalDebt") &&
                financialMetrics.containsKey("revenue")) {
            double debt = ((Number) financialMetrics.get("totalDebt")).doubleValue();
            double revenue = ((Number) financialMetrics.get("revenue")).doubleValue();
            double debtRatio = debt / revenue;
            ratios.put("debtToRevenueRatio", Math.round(debtRatio * 100.0) / 100.0);
        }

        // cash flow to revenue ratio
        if (financialMetrics.containsKey("cashFlow") &&
                financialMetrics.containsKey("revenue")) {
            double cashFlow = ((Number) financialMetrics.get("cashFlow")).doubleValue();
            double revenue = ((Number) financialMetrics.get("revenue")).doubleValue();
            double cashFlowRatio = (cashFlow / revenue) * 100;
            ratios.put("cashFlowToRevenuePct", Math.round(cashFlowRatio * 100.0) / 100.0);
        }

        log.info("Calculated {} financial ratios", ratios.size());
        return ratios;
    }
}