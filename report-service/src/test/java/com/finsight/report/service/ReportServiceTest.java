package com.finsight.report.service;

import com.finsight.report.event.ReportReadyEvent;
import com.finsight.report.exception.ReportNotFoundException;
import com.finsight.report.model.Report;
import com.finsight.report.model.Report.ReportStatus;
import com.finsight.report.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ReportService reportService;

    private ReportReadyEvent buildEvent(String riskLevel) {
        return ReportReadyEvent.builder()
                .documentId("doc-123")
                .filename("TechCorp-Q3-2024.pdf")
                .riskSummary("TechCorp presents HIGH risk due to net loss...")
                .anomalies(List.of(
                        "ALERT: Company is reporting a net loss",
                        "HIGH RISK: Debt-to-revenue ratio is 2.31"))
                .calculatedRatios(Map.of(
                        "profitMarginPct", -6.54,
                        "debtToRevenueRatio", 2.31))
                .riskLevel(riskLevel)
                .generatedAt(Instant.now())
                .build();
    }

    @Test
    void save_validEvent_persistsReport() {
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Report result = reportService.save(buildEvent("HIGH"));

        assertThat(result.getDocumentId()).isEqualTo("doc-123");
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
        assertThat(result.getStatus()).isEqualTo(ReportStatus.READY);
        assertThat(result.getAnomalies()).hasSize(2);
        assertThat(result.getSavedAt()).isNotNull();
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    void getByDocumentId_existingDocument_returnsReport() {
        Report mockReport = Report.builder()
                .documentId("doc-123")
                .riskLevel("HIGH")
                .status(ReportStatus.READY)
                .build();

        when(reportRepository.findByDocumentId("doc-123"))
                .thenReturn(Optional.of(mockReport));

        Report result = reportService.getByDocumentId("doc-123");

        assertThat(result.getDocumentId()).isEqualTo("doc-123");
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void getByDocumentId_notFound_throwsException() {
        when(reportRepository.findByDocumentId("unknown"))
                .thenReturn(Optional.empty());

        assertThrows(ReportNotFoundException.class,
                () -> reportService.getByDocumentId("unknown"));
    }

    @Test
    void getByRiskLevel_returnsFilteredReports() {
        List<Report> mockReports = List.of(
                Report.builder().documentId("doc-1").riskLevel("HIGH").build(),
                Report.builder().documentId("doc-2").riskLevel("HIGH").build()
        );

        when(reportRepository.findByRiskLevel("HIGH"))
                .thenReturn(mockReports);

        List<Report> result = reportService.getByRiskLevel("high");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> "HIGH".equals(r.getRiskLevel()));
    }
}