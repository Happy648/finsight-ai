package com.finsight.report.service;

import com.finsight.report.event.ReportReadyEvent;
import com.finsight.report.exception.ReportNotFoundException;
import com.finsight.report.model.Report;
import com.finsight.report.model.Report.ReportStatus;
import com.finsight.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    public Report save(ReportReadyEvent event) {
        Report report = Report.builder()
                .id(UUID.randomUUID().toString())
                .documentId(event.getDocumentId())
                .filename(event.getFilename())
                .riskSummary(event.getRiskSummary())
                .anomalies(event.getAnomalies())
                .calculatedRatios(event.getCalculatedRatios())
                .riskLevel(event.getRiskLevel())
                .status(ReportStatus.READY)
                .generatedAt(event.getGeneratedAt())
                .savedAt(Instant.now())
                .build();

        reportRepository.save(report);
        log.info("Saved report for documentId={} with riskLevel={}",
                event.getDocumentId(), event.getRiskLevel());

        return report;
    }

    public Report getByDocumentId(String documentId) {
        return reportRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new ReportNotFoundException(
                        "Report not found for documentId=" + documentId));
    }

    public List<Report> getByRiskLevel(String riskLevel) {
        return reportRepository.findByRiskLevel(riskLevel.toUpperCase());
    }

    public List<Report> getAllReports() {
        return reportRepository.findAll();
    }
}