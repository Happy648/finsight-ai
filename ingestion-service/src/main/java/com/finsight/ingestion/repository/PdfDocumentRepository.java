package com.finsight.ingestion.repository;

import com.finsight.ingestion.model.PdfDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PdfDocumentRepository extends MongoRepository<PdfDocument, String> {
}
