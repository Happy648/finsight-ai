package com.finsight.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "parsed_documents")
public class ParsedDocument {

    @Id
    private String id;
    private String documentId;
    private String filename;
    private String rawText;
    private int pageCount;
    private Map<String, Object> financialMetrics;
    private ParseStatus status;
    private Instant parsedAt;

    public enum ParseStatus {
        SUCCESS, PARTIAL, FAILED
    }
}