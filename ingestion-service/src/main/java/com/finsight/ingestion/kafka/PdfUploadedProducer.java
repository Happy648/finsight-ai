package com.finsight.ingestion.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.ingestion.event.PdfUploadedEvent;
import com.finsight.ingestion.model.PdfDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfUploadedProducer {

    private static final String TOPIC = "pdf.uploaded";
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(PdfDocument doc) {
        try {
            PdfUploadedEvent event = PdfUploadedEvent.builder()
                    .documentId(doc.getDocumentId())
                    .filename(doc.getFilename())
                    .gridFsId(doc.getGridFsId())
                    .fileSize(doc.getFileSize())
                    .uploadedAt(doc.getUploadedAt())
                    .build();

            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, doc.getDocumentId(), message);
            log.info("Published pdf.uploaded event for documentId={}", doc.getDocumentId());

        } catch (Exception e) {
            log.error("Failed to publish pdf.uploaded event: {}", e.getMessage());
        }
    }
}