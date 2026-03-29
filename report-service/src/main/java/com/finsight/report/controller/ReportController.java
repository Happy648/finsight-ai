package com.finsight.report.controller;

import com.finsight.report.model.Report;
import com.finsight.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // get report by documentId
    @GetMapping("/{documentId}")
    public ResponseEntity<Report> getReport(
            @PathVariable String documentId) {

        log.info("Fetching report for documentId={}", documentId);
        Report report = reportService.getByDocumentId(documentId);
        return ResponseEntity.ok(report);
    }

    // get all reports optionally filtered by risk level
    @GetMapping
    public ResponseEntity<List<Report>> getAllReports(
            @RequestParam(required = false) String riskLevel) {

        List<Report> reports = riskLevel != null
                ? reportService.getByRiskLevel(riskLevel)
                : reportService.getAllReports();

        return ResponseEntity.ok(reports);
    }
}