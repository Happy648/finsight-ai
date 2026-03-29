package com.finsight.report.repository;

import com.finsight.report.model.Report;
import com.finsight.report.model.Report.ReportStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends MongoRepository<Report, String> {

    Optional<Report> findByDocumentId(String documentId);

    List<Report> findByRiskLevel(String riskLevel);
}