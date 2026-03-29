package com.finsight.parser.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfUploadedEvent {

    private String documentId;
    private String filename;
    private String gridFsId;
    private long fileSize;
    private Instant uploadedAt;
}
