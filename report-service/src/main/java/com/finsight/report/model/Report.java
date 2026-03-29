package com.finsight.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reports")
public class Report {

    @Id
    private String id;

    @Indexed(unique = true)
    private String documentId;

    private String filename;
    private String riskSummary;
    private List<String> anomalies;
    private Map<String, Object> calculatedRatios;
    private String riskLevel;
    private ReportStatus status;
    private Instant generatedAt;
    private Instant savedAt;

    public enum ReportStatus {
        READY, VIEWED, ARCHIVED
    }
}