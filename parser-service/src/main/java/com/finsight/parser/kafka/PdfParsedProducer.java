package com.finsight.parser.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.parser.event.PdfParsedEvent;
import com.finsight.parser.model.ParsedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfParsedProducer {

    private static final String TOPIC = "pdf.parsed";
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;        // injected by Spring

    public void publish(ParsedDocument doc) {
        try {
            PdfParsedEvent event = PdfParsedEvent.builder()
                    .documentId(doc.getDocumentId())
                    .filename(doc.getFilename())
                    .parsedDocumentId(doc.getId())
                    .financialMetrics(doc.getFinancialMetrics())
                    .rawText(doc.getRawText())
                    .pageCount(doc.getPageCount())
                    .parsedAt(Instant.now())
                    .build();

            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, doc.getDocumentId(), message);
            log.info("Published pdf.parsed event for documentId={}", doc.getDocumentId());

        } catch (Exception e) {
            log.error("Failed to publish pdf.parsed event: {}", e.getMessage());
        }
    }
}