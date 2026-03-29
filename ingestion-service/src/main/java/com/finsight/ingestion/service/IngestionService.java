package com.finsight.ingestion.service;

import com.finsight.ingestion.kafka.PdfUploadedProducer;
import com.finsight.ingestion.model.PdfDocument;
import com.finsight.ingestion.model.PdfDocument.DocumentStatus;
import com.finsight.ingestion.repository.PdfDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final GridFsTemplate gridFsTemplate;
    private final PdfDocumentRepository documentRepository;
    private final PdfUploadedProducer kafkaProducer;

    public PdfDocument ingest(MultipartFile file) throws IOException {

        // 1. Store raw PDF bytes in GridFS
        String gridFsId = gridFsTemplate.store(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType()
        ).toString();

        log.info("Stored PDF in GridFS with id={}", gridFsId);

        // 2. Create metadata record
        PdfDocument doc = PdfDocument.builder()
                .documentId(UUID.randomUUID().toString())
                .filename(file.getOriginalFilename())
                .gridFsId(gridFsId)
                .fileSize(file.getSize())
                .status(DocumentStatus.UPLOADED)
                .uploadedAt(Instant.now())
                .build();

        documentRepository.save(doc);
        log.info("Saved PdfDocument record with documentId={}", doc.getDocumentId());

        // 3. Publish Kafka event
        kafkaProducer.publish(doc);

        return doc;
    }
}