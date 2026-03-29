package com.finsight.agent.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.agent.event.ReportReadyEvent;
import com.finsight.agent.model.AgentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportReadyProducer {

    private static final String TOPIC = "report.ready";
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(AgentResult result) {
        try {
            ReportReadyEvent event = ReportReadyEvent.builder()
                    .documentId(result.getDocumentId())
                    .filename(result.getFilename())
                    .riskSummary(result.getRiskSummary())
                    .anomalies(result.getAnomalies())
                    .calculatedRatios(result.getCalculatedRatios())
                    .riskLevel(result.getRiskLevel())
                    .generatedAt(Instant.now())
                    .build();

            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, result.getDocumentId(), message);
            log.info("Published report.ready event for documentId={}",
                    result.getDocumentId());

        } catch (Exception e) {
            log.error("Failed to publish report.ready event: {}", e.getMessage());
        }
    }
}