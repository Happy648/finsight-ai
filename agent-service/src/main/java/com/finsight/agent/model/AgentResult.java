package com.finsight.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "agent_results")
public class AgentResult {

    @Id
    private String id;
    private String documentId;
    private String filename;
    private String riskSummary;
    private List<String> anomalies;
    private Map<String, Object> calculatedRatios;
    private String riskLevel;
    private int toolCallCount;          // how many tool calls Claude made
    private long processingTimeMs;      // for sub-3s target tracking
    private Instant generatedAt;
}
