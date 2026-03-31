# ingestion-service

The ingestion-service is the **entry point** of the FinSight AI pipeline. It accepts financial PDF uploads from clients, stores them safely in MongoDB GridFS, and publishes a Kafka event to trigger downstream processing.

---

## Table of Contents

- [Responsibility](#responsibility)
- [How It Works](#how-it-works)
- [API Endpoints](#api-endpoints)
- [Kafka Events](#kafka-events)
- [MongoDB Storage](#mongodb-storage)
- [Configuration](#configuration)
- [Running Locally](#running-locally)
- [Running Tests](#running-tests)
- [Package Structure](#package-structure)

---

## Responsibility

This service has one job — accept a PDF and hand it off to the pipeline. It does not parse or analyse the PDF. It stores it and fires an event.

```
Client
  │
  │  POST /api/v1/documents/upload
  ▼
IngestionController
  │
  ├── validates file type and size
  │
  ├── IngestionService
  │     ├── stores PDF bytes in MongoDB GridFS
  │     ├── saves PdfDocument metadata record
  │     └── publishes pdf.uploaded Kafka event
  │
  └── returns 202 Accepted + documentId
```

---

## How It Works

### 1. Client uploads PDF via HTTP

The client sends a `multipart/form-data` POST request with the PDF file. The controller validates:

- File is not empty
- File is a PDF by content type or `.pdf` extension

### 2. PDF stored in MongoDB GridFS

Raw PDF bytes are stored in GridFS — MongoDB's solution for files larger than the 16MB BSON document limit. GridFS splits files into 255KB chunks stored across two collections:

- `fs.files` — file metadata
- `fs.chunks` — binary chunks

### 3. Metadata record saved

A lightweight `PdfDocument` record is saved to the `pdf_documents` collection tracking the `documentId`, `gridFsId`, `status` and `uploadedAt` timestamp.

### 4. Kafka event published

A `pdf.uploaded` event is published to the `pdf.uploaded` topic. This event contains no PDF bytes — only metadata so parser-service knows what to fetch. The `documentId` is used as the Kafka message key to guarantee ordering per document.

---

## API Endpoints

### Upload PDF

```
POST /api/v1/documents/upload
Content-Type: multipart/form-data
```

**Request:**

| Field | Type | Description |
|---|---|---|
| `file` | File | The PDF file to upload |

**Response — 202 Accepted:**

```json
{
  "documentId": "3f7a1c2e-8b4d-4e9f-a2c1-d5e6f7890abc",
  "status": "UPLOADED"
}
```

**Error Responses:**

| Status | Reason |
|---|---|
| 400 | File is empty |
| 415 | File is not a PDF |
| 500 | Internal server error |

**Example — Windows PowerShell:**

```powershell
curl -X POST http://localhost:8081/api/v1/documents/upload `
  -F "file=@C:\path\to\report.pdf;type=application/pdf"
```

**Example — Mac/Linux:**

```bash
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@/path/to/report.pdf;type=application/pdf"
```

**Example — Postman:**

1. Method: `POST`
2. URL: `http://localhost:8081/api/v1/documents/upload`
3. Body → form-data → key: `file`, type: `File`
4. Select PDF and set content type to `application/pdf`
5. Click Send

---

## Kafka Events

### Produced — `pdf.uploaded`

Published after every successful PDF upload.

**Topic:** `pdf.uploaded`  
**Key:** `documentId`  
**Partitions:** 3

**Payload:**

```json
{
  "documentId": "3f7a1c2e-8b4d-4e9f-a2c1-d5e6f7890abc",
  "filename": "Q3-2025-annual-report.pdf",
  "gridFsId": "6672a1f3e4b0c8d9f1234567",
  "fileSize": 2457600,
  "uploadedAt": "2025-10-01T09:32:14.221Z"
}
```

| Field | Description |
|---|---|
| `documentId` | Canonical ID that flows through the entire pipeline |
| `filename` | Original uploaded filename |
| `gridFsId` | MongoDB GridFS file ID used by parser-service to fetch bytes |
| `fileSize` | File size in bytes |
| `uploadedAt` | Upload timestamp in ISO 8601 format |

---

## MongoDB Storage

### Collection — `pdf_documents`

```json
{
  "_id": "3f7a1c2e-8b4d-4e9f-a2c1-d5e6f7890abc",
  "filename": "Q3-2025-annual-report.pdf",
  "gridFsId": "6672a1f3e4b0c8d9f1234567",
  "fileSize": 2457600,
  "status": "UPLOADED",
  "uploadedAt": "2025-10-01T09:32:14.221Z"
}
```

### Document Status Lifecycle

```
UPLOADED → PARSING → PARSED → ANALYSING → DONE
                                         → FAILED
```

### GridFS Collections

| Collection | Contents |
|---|---|
| `fs.files` | File metadata (name, size, upload date) |
| `fs.chunks` | Binary chunks (255KB each) |

---

## Configuration

`src/main/resources/application.properties`:

```properties
spring.application.name=ingestion-service
server.port=8081

spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/finsight}

spring.kafka.bootstrap-servers=${KAFKA_BROKERS:localhost:9092}
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer

spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

management.endpoints.web.exposure.include=health,info,metrics
```

---

## Running Locally

**Prerequisites:** Docker Desktop running with `docker compose up -d`

```bash
# Windows
.\gradlew.bat :ingestion-service:bootRun

# Mac/Linux
./gradlew :ingestion-service:bootRun
```

Service starts on `http://localhost:8081`

---

## Running Tests

```bash
# Windows
.\gradlew.bat :ingestion-service:test

# Mac/Linux
./gradlew :ingestion-service:test
```

### Tests included

| Test class | What it tests |
|---|---|
| `IngestionControllerTest` | Valid PDF upload returns 202, empty file returns 400, non-PDF returns 415 |

---

## Package Structure

```
src/main/java/com/finsight/ingestion/
├── IngestionServiceApplication.java
├── config/
│   ├── KafkaConfig.java                 ← Kafka topic definition
│   └── JacksonConfig.java               ← ObjectMapper with JavaTimeModule
├── controller/
│   └── IngestionController.java         ← REST endpoint POST /upload
├── model/
│   └── PdfDocument.java                 ← MongoDB document model
├── repository/
│   └── PdfDocumentRepository.java       ← Spring Data MongoDB repository
└── service/
    ├── IngestionService.java            ← Core business logic
    ├── PdfUploadedProducer.java         ← Publishes to pdf.uploaded topic
    └── event/
        └── PdfUploadedEvent.java        ← Kafka event payload
```