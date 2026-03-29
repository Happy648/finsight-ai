package com.finsight.ingestion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.ingestion.model.PdfDocument;
import com.finsight.ingestion.model.PdfDocument.DocumentStatus;
import com.finsight.ingestion.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    @MockitoBean
    private ObjectMapper objectMapper;

    @Test
    void uploadPdf_validFile_returns202WithDocumentId() throws Exception {

        // Arrange
        PdfDocument mockDoc = PdfDocument.builder()
                .documentId(UUID.randomUUID().toString())
                .filename("test-report.pdf")
                .gridFsId("gridfs-id-123")
                .fileSize(1024L)
                .status(DocumentStatus.UPLOADED)
                .uploadedAt(Instant.now())
                .build();

        when(ingestionService.ingest(any())).thenReturn(mockDoc);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-report.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "PDF content".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/documents/upload").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    void uploadPdf_emptyFile_returns400() throws Exception {

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/documents/upload").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File is empty"));
    }

    @Test
    void uploadPdf_nonPdfFile_returns415() throws Exception {

        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "document.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "some text content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/documents/upload").file(textFile))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("Only PDF files are accepted"));
    }
}