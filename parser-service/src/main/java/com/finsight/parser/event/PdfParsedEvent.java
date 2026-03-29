package com.finsight.parser.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfParsedEvent {

    private String documentId;
    private String filename;
    private String parsedDocumentId;

    // extracted financial metrics passed directly to agent-service
    private Map<String, Object> financialMetrics;
    private String rawText;
    private int pageCount;
    private Instant parsedAt;
}