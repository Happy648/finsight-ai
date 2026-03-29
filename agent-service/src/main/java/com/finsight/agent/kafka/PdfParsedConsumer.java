package com.finsight.agent.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.agent.event.PdfParsedEvent;
import com.finsight.agent.model.AgentResult;
import com.finsight.agent.service.AgentOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfParsedConsumer {

    private final AgentOrchestrationService orchestrationService;
    private final ReportReadyProducer reportReadyProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "pdf.parsed",
            groupId = "agent-group"
    )
    public void consume(String message) {
        try {
            PdfParsedEvent event = objectMapper.readValue(
                    message, PdfParsedEvent.class);

            log.info("Received pdf.parsed event for documentId={}",
                    event.getDocumentId());

            AgentResult result = orchestrationService.orchestrate(event);
            reportReadyProducer.publish(result);

        } catch (Exception e) {
            log.error("Failed to process pdf.parsed message: {}", e.getMessage());
        }
    }
}