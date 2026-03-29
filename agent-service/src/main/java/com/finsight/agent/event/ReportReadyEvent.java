package com.finsight.agent.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportReadyEvent {

    private String documentId;
    private String filename;
    private String riskSummary;
    private List<String> anomalies;
    private Map<String, Object> calculatedRatios;
    private String riskLevel;           // LOW, MEDIUM, HIGH, CRITICAL
    private Instant generatedAt;
}
