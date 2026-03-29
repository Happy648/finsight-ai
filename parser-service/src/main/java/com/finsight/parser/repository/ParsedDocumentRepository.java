package com.finsight.parser.repository;

import com.finsight.parser.model.ParsedDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParsedDocumentRepository extends MongoRepository<ParsedDocument, String> {

    Optional<ParsedDocument> findByDocumentId(String documentId);
}