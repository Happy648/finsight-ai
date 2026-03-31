# report-service

The report-service is the **final stage** of the FinSight AI pipeline. It consumes completed analysis events from the agent-service, persists the full risk report to MongoDB, and exposes a REST API for clients to retrieve reports.

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

The report-service is a **Kafka consumer with a REST API**. Its job is to safely persist what the agent produced and make it accessible to clients.

```
report.ready (consumed)
        │
        ▼
ReportReadyConsumer
        │
        ▼
ReportService
  ├── maps event to Report document
  ├── sets status to READY
  └── saves to MongoDB reports collection
        │
        ▼
Client polls:
GET /api/v1/reports/{documentId}
```

---

## How It Works

### 1. Consumes report.ready event

The Kafka listener receives the `report.ready` event from agent-service. This event contains the complete analysis result — risk summary, anomalies, calculated ratios and risk level.

### 2. Persists to MongoDB

The event is mapped to a `Report` document and saved to the `reports` collection. A `savedAt` timestamp is added to track when the report became available.

### 3. REST API for retrieval

Clients retrieve reports by `documentId` obtained from the original upload response, or filter all reports by `riskLevel` for compliance dashboards.

---

## API Endpoints

### Get report by document ID

```
GET /api/v1/reports/{documentId}
```

**Response — 200 OK:**

```json
{
  "id": "c3d4e5f6-...",
  "documentId": "3f7a1c2e-8b4d-4e9f-a2c1-d5e6f7890abc",
  "filename": "TechCorp-Q3-2024.pdf",
  "riskSummary": "TechCorp presents a HIGH risk profile. The company recorded a $340M net loss despite $5.2B revenue...",
  "anomalies": [
    "ALERT: Company is reporting a net loss",
    "HIGH RISK: Debt-to-revenue ratio is 2.31 — exceeds 2.0 threshold",
    "WARNING: Negative operating cash flow detected"
  ],
  "calculatedRatios": {
    "profitMarginPct": -6.54,
    "debtToRevenueRatio": 2.31,
    "cashFlowToRevenuePct": -1.71
  },
  "riskLevel": "HIGH",
  "status": "READY",
  "generatedAt": "2025-10-01T09:33:57.221Z",
  "savedAt": "2025-10-01T09:33:57.891Z"
}
```

**Error responses:**

| Status | Reason |
|---|---|
| 404 | Report not found for the given documentId |

**Example:**

```bash
curl http://localhost:8084/api/v1/reports/3f7a1c2e-8b4d-4e9f-a2c1-d5e6f7890abc
```

---

### Get all reports

```
GET /api/v1/reports
```

Returns all reports in the system.

**Example:**

```bash
curl http://localhost:8084/api/v1/reports
```

---

### Get reports filtered by risk level

```
GET /api/v1/reports?riskLevel={riskLevel}
```

**Risk level values:** `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`

**Example — get all HIGH risk reports:**

```bash
curl http://localhost:8084/api/v1/reports?riskLevel=HIGH
```

This endpoint is useful for compliance dashboards that need to surface only the highest risk documents.

---

### Health check

```
GET /actuator/health
```

**Response:** `{ "status": "UP" }`

---

## Kafka Events

### Consumed — `report.ready`

**Topic:** `report.ready`  
**Consumer group:** `report-group`

**Fields consumed:**

| Field | Description |
|---|---|
| `documentId` | Canonical document ID |
| `filename` | Original PDF filename |
| `riskSummary` | Plain-English risk narrative from Claude |
| `anomalies` | List of detected anomalies |
| `calculatedRatios` | Financial ratios from agent-service |
| `riskLevel` | LOW / MEDIUM / HIGH / CRITICAL |
| `generatedAt` | When Claude generated the analysis |

---

## MongoDB Storage

### Collection — `reports`

```json
{
  "_id": "c3d4e5f6-...",
  "documentId": "3f7a1c2e-...",
  "filename": "TechCorp-Q3-2024.pdf",
  "riskSummary": "TechCorp presents a HIGH risk profile...",
  "anomalies": [
    "ALERT: Net loss",
    "HIGH RISK: Debt ratio 2.31"
  ],
  "calculatedRatios": {
    "profitMarginPct": -6.54,
    "debtToRevenueRatio": 2.31
  },
  "riskLevel": "HIGH",
  "status": "READY",
  "generatedAt": "2025-10-01T09:33:57.221Z",
  "savedAt": "2025-10-01T09:33:57.891Z"
}
```

`documentId` has a **unique index** to prevent duplicate reports for the same document.

### Report Status

| Status | Meaning |
|---|---|
| `READY` | Report is available for retrieval |
| `VIEWED` | Report has been accessed (future use) |
| `ARCHIVED` | Report has been archived (future use) |

---

## Configuration

`src/main/resources/application.properties`:

```properties
spring.application.name=report-service
server.port=8084

spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/finsight}

spring.kafka.bootstrap-servers=${KAFKA_BROKERS:localhost:9092}
spring.kafka.consumer.group-id=report-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer

management.endpoints.web.exposure.include=health,info,metrics
```

---

## Running Locally

**Prerequisites:** Docker Desktop running and all other 3 services running

```bash
# Windows
.\gradlew.bat :report-service:bootRun

# Mac/Linux
./gradlew :report-service:bootRun
```

Service starts on `http://localhost:8084`

---

## Running Tests

```bash
# Windows
.\gradlew.bat :report-service:test

# Mac/Linux
./gradlew :report-service:test
```

### Tests included

| Test class | What it tests |
|---|---|
| `ReportServiceTest` | Valid event persists with READY status, unknown documentId throws 404, risk level filter works |
| `ReportControllerTest` | GET by documentId returns 200, unknown returns 404, GET all returns full list, risk level filter returns filtered list |

---

## Package Structure

```
src/main/java/com/finsight/report/
├── ReportServiceApplication.java
├── config/
│   ├── KafkaConfig.java                 ← Consumer configuration
│   └── JacksonConfig.java               ← ObjectMapper configuration
├── controller/
│   └── ReportController.java            ← REST endpoints
├── exception/
│   └── ReportNotFoundException.java     ← 404 exception handler
├── model/
│   └── Report.java                      ← MongoDB document model
├── repository/
│   └── ReportRepository.java            ← Spring Data MongoDB repository
└── service/
    ├── ReportService.java               ← Business logic
    ├── ReportReadyConsumer.java         ← Listens to report.ready topic
    └── event/
        └── ReportReadyEvent.java        ← Consumed event from agent-service
```