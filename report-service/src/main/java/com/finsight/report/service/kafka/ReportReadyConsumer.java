package com.finsight.report.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.report.event.ReportReadyEvent;
import com.finsight.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportReadyConsumer {

    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "report.ready",
            groupId = "report-group"
    )
    public void consume(String message) {
        try {
            ReportReadyEvent event = objectMapper.readValue(
                    message, ReportReadyEvent.class);

            log.info("Received report.ready event for documentId={}",
                    event.getDocumentId());

            reportService.save(event);

        } catch (Exception e) {
            log.error("Failed to process report.ready message: {}",
                    e.getMessage());
        }
    }
}