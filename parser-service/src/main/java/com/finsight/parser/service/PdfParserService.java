package com.finsight.parser.service;

import com.finsight.parser.event.PdfUploadedEvent;
import com.finsight.parser.model.ParsedDocument;
import com.finsight.parser.model.ParsedDocument.ParseStatus;
import com.finsight.parser.repository.ParsedDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfParserService {

    private final GridFsTemplate gridFsTemplate;
    private final ParsedDocumentRepository repository;
    private final FinancialDataNormaliser normaliser;

    public ParsedDocument parse(PdfUploadedEvent event) {
        log.info("Starting parse for documentId={}", event.getDocumentId());

        try {
            // 1. Fetch PDF bytes from GridFS using the gridFsId
            GridFsResource resource = gridFsTemplate.getResource(
                    gridFsTemplate.findOne(
                            Query.query(Criteria.where("_id")
                                    .is(new ObjectId(event.getGridFsId())))
                    )
            );

            // 2. Extract raw text using PDFBox
            String rawText;
            int pageCount;

            try (InputStream inputStream = resource.getInputStream();
                 PDDocument pdDocument = Loader.loadPDF(inputStream.readAllBytes())) {

                PDFTextStripper stripper = new PDFTextStripper();
                rawText = stripper.getText(pdDocument);
                pageCount = pdDocument.getNumberOfPages();

                log.info("Extracted {} characters from {} pages for documentId={}",
                        rawText.length(), pageCount, event.getDocumentId());
            }

            // 3. Normalise raw text into structured financial metrics
            Map<String, Object> metrics = normaliser.normalise(rawText);

            // 4. Save ParsedDocument to MongoDB
            ParsedDocument parsed = ParsedDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .documentId(event.getDocumentId())
                    .filename(event.getFilename())
                    .rawText(rawText)
                    .pageCount(pageCount)
                    .financialMetrics(metrics)
                    .status(ParseStatus.SUCCESS)
                    .parsedAt(Instant.now())
                    .build();

            repository.save(parsed);
            log.info("Saved ParsedDocument for documentId={}", event.getDocumentId());

            return parsed;

        } catch (Exception e) {
            log.error("Failed to parse documentId={}: {}", event.getDocumentId(), e.getMessage());

            // save a FAILED record so agent-service knows something went wrong
            ParsedDocument failed = ParsedDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .documentId(event.getDocumentId())
                    .filename(event.getFilename())
                    .status(ParseStatus.FAILED)
                    .parsedAt(Instant.now())
                    .build();

            repository.save(failed);
            throw new RuntimeException("PDF parsing failed for documentId="
                    + event.getDocumentId(), e);
        }
    }
}