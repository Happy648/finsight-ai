package com.finsight.parser.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.parser.event.PdfUploadedEvent;
import com.finsight.parser.model.ParsedDocument;
import com.finsight.parser.service.PdfParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfUploadedConsumer {

    private final PdfParserService parserService;
    private final PdfParsedProducer parsedProducer;
    private final ObjectMapper objectMapper;        // injected by Spring

    @KafkaListener(
            topics = "pdf.uploaded",
            groupId = "parser-group"
    )
    public void consume(String message) {
        try {
            PdfUploadedEvent event = objectMapper.readValue(message, PdfUploadedEvent.class);
            log.info("Received pdf.uploaded event for documentId={}", event.getDocumentId());

            ParsedDocument parsed = parserService.parse(event);
            parsedProducer.publish(parsed);

            log.info("Completed parsing for documentId={}", event.getDocumentId());

        } catch (Exception e) {
            log.error("Failed to process pdf.uploaded message: {}", e.getMessage());
        }
    }
}