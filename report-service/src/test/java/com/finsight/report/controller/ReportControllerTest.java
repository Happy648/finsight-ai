package com.finsight.report.controller;

import com.finsight.report.exception.ReportNotFoundException;
import com.finsight.report.model.Report;
import com.finsight.report.model.Report.ReportStatus;
import com.finsight.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @Test
    void getReport_existingDocument_returns200() throws Exception {
        Report mockReport = Report.builder()
                .id("report-id-1")
                .documentId("doc-123")
                .filename("TechCorp-Q3-2024.pdf")
                .riskLevel("HIGH")
                .riskSummary("TechCorp presents HIGH risk...")
                .status(ReportStatus.READY)
                .generatedAt(Instant.now())
                .build();

        when(reportService.getByDocumentId("doc-123"))
                .thenReturn(mockReport);

        mockMvc.perform(get("/api/v1/reports/doc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-123"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void getReport_notFound_returns404() throws Exception {
        when(reportService.getByDocumentId("unknown"))
                .thenThrow(new ReportNotFoundException(
                        "Report not found for documentId=unknown"));

        mockMvc.perform(get("/api/v1/reports/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllReports_noFilter_returnsAllReports() throws Exception {
        List<Report> mockReports = List.of(
                Report.builder().documentId("doc-1").riskLevel("HIGH").build(),
                Report.builder().documentId("doc-2").riskLevel("LOW").build()
        );

        when(reportService.getAllReports()).thenReturn(mockReports);

        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAllReports_withRiskLevelFilter_returnsFilteredReports() throws Exception {
        List<Report> highRiskReports = List.of(
                Report.builder().documentId("doc-1").riskLevel("HIGH").build()
        );

        when(reportService.getByRiskLevel("HIGH"))
                .thenReturn(highRiskReports);

        mockMvc.perform(get("/api/v1/reports?riskLevel=HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].riskLevel").value("HIGH"));
    }
}