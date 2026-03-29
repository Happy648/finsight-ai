package com.finsight.ingestion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "pdf_documents")
public class PdfDocument {

    @Id
    private String documentId;
    private String filename;
    private String gridFsId;
    private long fileSize;
    private DocumentStatus status;
    private Instant uploadedAt;

    public enum DocumentStatus {
        UPLOADED, PARSING, PARSED, ANALYSING, DONE, FAILED
    }
}