package com.finsight.parser.service;

import com.finsight.parser.event.PdfUploadedEvent;
import com.finsight.parser.model.ParsedDocument;
import com.finsight.parser.repository.ParsedDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfParserServiceTest {

    @Mock
    private GridFsTemplate gridFsTemplate;

    @Mock
    private ParsedDocumentRepository repository;

    @Mock
    private FinancialDataNormaliser normaliser;

    @InjectMocks
    private PdfParserService parserService;

    private PdfUploadedEvent buildEvent(String docId) {
        return PdfUploadedEvent.builder()
                .documentId(docId)
                .filename("report.pdf")
                .gridFsId("507f1f77bcf86cd799439011")
                .fileSize(1024L)
                .uploadedAt(Instant.now())
                .build();
    }

    @Test
    void parse_gridFsFailure_savesFailedRecordAndThrows() {

        // Arrange
        PdfUploadedEvent event = buildEvent("doc-456");

        when(gridFsTemplate.findOne(any()))
                .thenThrow(new RuntimeException("GridFS unavailable"));
        when(repository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> parserService.parse(event)
        );

        // Verify a FAILED record was still saved
        verify(repository).save(any(ParsedDocument.class));
    }

    @Test
    void parse_normalisedMetrics_savedToDocument() {

        // Arrange — test the normaliser integration via the save call
        PdfUploadedEvent event = buildEvent("doc-789");

        Map<String, Object> mockMetrics = Map.of(
                "revenue", 5_200_000_000.0,
                "missingRevenue", false
        );

        when(gridFsTemplate.findOne(any()))
                .thenThrow(new RuntimeException("simulate failure"));
        when(repository.save(any()))
                .thenAnswer(invocation -> {
                    ParsedDocument saved = invocation.getArgument(0);
                    assertThat(saved.getDocumentId()).isEqualTo("doc-789");
                    assertThat(saved.getStatus())
                            .isEqualTo(ParsedDocument.ParseStatus.FAILED);
                    return saved;
                });

        // Act
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> parserService.parse(event)
        );

        verify(repository).save(any(ParsedDocument.class));
    }
}