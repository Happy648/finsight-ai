# parser-service

The parser-service is the **extraction engine** of the FinSight AI pipeline. It listens for uploaded PDF events, fetches the raw PDF from MongoDB GridFS, extracts financial metrics using Apache PDFBox, and publishes structured data for the AI agent to analyse.

---

## Table of Contents

- [Responsibility](#responsibility)
- [How It Works](#how-it-works)
- [Financial Data Extraction](#financial-data-extraction)
- [Kafka Events](#kafka-events)
- [MongoDB Storage](#mongodb-storage)
- [Configuration](#configuration)
- [Running Locally](#running-locally)
- [Running Tests](#running-tests)
- [Package Structure](#package-structure)

---

## Responsibility

The parser-service is both a **Kafka consumer and a Kafka producer**. It sits in the middle of the pipeline — consuming raw PDF events and producing structured financial data events.

```
pdf.uploaded (consumed)
        │
        ▼
PdfUploadedConsumer
        │
        ▼
PdfParserService
  ├── fetches PDF bytes from GridFS
  ├── extracts text with PDFBox
  └── normalises text into financial metrics
        │
        ▼
FinancialDataNormaliser
  ├── extracts revenue, net income, debt, cash flow
  ├── flags anomalies (missing data, net loss)
  └── returns structured Map<String, Object>
        │
        ▼
ParsedDocument saved to MongoDB
        │
        ▼
pdf.parsed (produced)
```

---

## How It Works

### 1. Consumes pdf.uploaded event

The Kafka listener receives the `pdf.uploaded` event from ingestion-service. The event contains the `gridFsId` pointing to the raw PDF bytes in MongoDB GridFS.

### 2. Fetches PDF from GridFS

Using the `gridFsId` from the event, the service fetches the raw PDF binary from MongoDB GridFS. The PDF never travels over Kafka — only the pointer does.

### 3. Extracts text with Apache PDFBox

PDFBox 3.x `Loader.loadPDF()` opens the PDF and `PDFTextStripper` extracts all text content. This gives a raw string of the entire document text.

### 4. Normalises into financial metrics

`FinancialDataNormaliser` uses regex patterns to extract specific financial figures from the raw text and sets anomaly flags for missing or negative values.

### 5. Saves ParsedDocument and publishes event

The extracted data is saved to MongoDB and published to the `pdf.parsed` topic for the agent-service to consume.

---

## Financial Data Extraction

### Extracted Metrics

| Metric | Key | Example value |
|---|---|---|
| Revenue | `revenue` | `5200000000.0` |
| Net income | `netIncome` | `1100000000.0` |
| Total debt | `totalDebt` | `3400000000.0` |
| Cash flow | `cashFlow` | `890000000.0` |
| Earnings per share | `earningsPerShare` | `4.23` |
| Negative income flag | `hasNegativeNetIncome` | `true / false` |
| Missing revenue flag | `missingRevenue` | `true / false` |
| Missing cash flow flag | `missingCashFlow` | `true / false` |

Both `million` and `billion` suffixes are supported and values are automatically scaled to actual numbers.

### Example extractions from raw PDF text

```
Total Revenue: $5,200 million      → revenue: 5200000000.0
Net Income: $1,100 million         → netIncome: 1100000000.0
Net Loss: $340 million             → hasNegativeNetIncome: true
Operating Cash Flow: $890 million  → cashFlow: 890000000.0
Basic Earnings Per Share: $4.23    → earningsPerShare: 4.23
```

---

## Kafka Events

### Consumed — `pdf.uploaded`

| Field | Description |
|---|---|
| `documentId` | Canonical document ID |
| `gridFsId` | MongoDB GridFS pointer to fetch PDF |
| `filename` | Original filename |

### Produced — `pdf.parsed`

**Topic:** `pdf.parsed`  
**Key:** `documentId`  
**Partitions:** 3

**Payload:**

```json
{
  "documentId": "3f7a1c2e-8b4d-4e9f-a2c1-d5e6f7890abc",
  "filename": "Q3-2025-annual-report.pdf",
  "parsedDocumentId": "a1b2c3d4-...",
  "financialMetrics": {
    "revenue": 5200000000.0,
    "netIncome": 1100000000.0,
    "totalDebt": 3400000000.0,
    "cashFlow": 890000000.0,
    "earningsPerShare": 4.23,
    "hasNegativeNetIncome": false,
    "missingRevenue": false,
    "missingCashFlow": false
  },
  "rawText": "Annual Financial Report 2024...",
  "pageCount": 48,
  "parsedAt": "2025-10-01T09:32:15.441Z"
}
```

---

## MongoDB Storage

### Collection — `parsed_documents`

```json
{
  "_id": "a1b2c3d4-...",
  "documentId": "3f7a1c2e-...",
  "filename": "Q3-2025-annual-report.pdf",
  "rawText": "Annual Financial Report 2024...",
  "pageCount": 48,
  "financialMetrics": { "..." },
  "status": "SUCCESS",
  "parsedAt": "2025-10-01T09:32:15.441Z"
}
```

### Parse Status

| Status | Meaning |
|---|---|
| `SUCCESS` | All metrics extracted successfully |
| `PARTIAL` | Some metrics extracted, some missing |
| `FAILED` | PDF could not be parsed — record still saved so agent-service knows |

---

## Configuration

`src/main/resources/application.properties`:

```properties
spring.application.name=parser-service
server.port=8082

spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/finsight}

spring.kafka.bootstrap-servers=${KAFKA_BROKERS:localhost:9092}
spring.kafka.consumer.group-id=parser-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer

management.endpoints.web.exposure.include=health,info,metrics
```

---

## Running Locally

**Prerequisites:** Docker Desktop running and ingestion-service running on `:8081`

```bash
# Windows
.\gradlew.bat :parser-service:bootRun

# Mac/Linux
./gradlew :parser-service:bootRun
```

Service starts on `http://localhost:8082`

---

## Running Tests

```bash
# Windows
.\gradlew.bat :parser-service:test

# Mac/Linux
./gradlew :parser-service:test
```

### Tests included

| Test class | What it tests |
|---|---|
| `FinancialDataNormaliserTest` | Revenue extraction, net loss detection, missing data flags, billion/million scaling |
| `PdfParserServiceTest` | GridFS failure saves FAILED record, repository always called |

---

## Package Structure

```
src/main/java/com/finsight/parser/
├── ParserServiceApplication.java
├── config/
│   ├── KafkaConfig.java                 ← Kafka topic definition
│   ├── JacksonConfig.java               ← ObjectMapper configuration
│   └── PdfBoxWarmup.java                ← Pre-warms PDFBox on startup
├── model/
│   └── ParsedDocument.java              ← MongoDB document model
├── repository/
│   └── ParsedDocumentRepository.java    ← Spring Data MongoDB repository
└── service/
    ├── PdfParserService.java            ← Core parsing orchestration
    ├── FinancialDataNormaliser.java     ← Regex-based metric extraction
    ├── PdfUploadedConsumer.java         ← Listens to pdf.uploaded topic
    ├── PdfParsedProducer.java           ← Publishes to pdf.parsed topic
    └── event/
        ├── PdfUploadedEvent.java        ← Consumed event from ingestion-service
        └── PdfParsedEvent.java          ← Produced event to agent-service
```