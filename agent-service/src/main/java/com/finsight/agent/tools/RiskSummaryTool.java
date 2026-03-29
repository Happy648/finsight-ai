package com.finsight.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RiskSummaryTool {

    public String determineRiskLevel(List<String> anomalies,
                                     Map<String, Object> ratios) {
        long criticalCount = anomalies.stream()
                .filter(a -> a.startsWith("CRITICAL")).count();
        long highCount = anomalies.stream()
                .filter(a -> a.startsWith("HIGH RISK")).count();
        long alertCount = anomalies.stream()
                .filter(a -> a.startsWith("ALERT")).count();

        if (criticalCount > 0) return "CRITICAL";
        if (highCount > 0 || alertCount > 0) return "HIGH";
        if (anomalies.size() > 2) return "MEDIUM";
        if (anomalies.isEmpty()) return "LOW";
        return "MEDIUM";
    }
}
