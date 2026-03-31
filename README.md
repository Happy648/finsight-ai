# FinSight AI — Agentic Financial Report Analyser

FinSight AI is a microservices-based intelligent financial document analysis system. It accepts uploaded financial PDF reports, autonomously extracts key metrics, detects anomalies, and generates plain-English risk summaries using an LLM agent powered by the Claude API.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Microservices](#microservices)
- [Prerequisites](#prerequisites)
- [Project Setup](#project-setup)
- [Running the Application](#running-the-application)
- [Testing the Pipeline](#testing-the-pipeline)
- [API Reference](#api-reference)
- [Project Structure](#project-structure)
- [Performance Targets](#performance-targets)
- [Environment Variables](#environment-variables)
- [Running Tests](#running-tests)
- [Stopping the Application](#stopping-the-application)

---

## Project Overview

FinSight AI solves a common problem in financial analysis — manually reviewing lengthy PDF reports to identify risks. The system automates this by:

1. Accepting a financial PDF via HTTP upload
2. Extracting raw text and financial metrics using Apache PDFBox
3. Running an autonomous Claude AI agent that detects anomalies and calculates ratios
4. Generating a plain-English risk summary with risk level classification
5. Persisting the report and exposing it via a REST API

The entire pipeline runs end-to-end in under 3 seconds.

---

## Architecture

```
Client
  │
  │  POST /api/v1/documents/upload (multipart PDF)
  ▼
┌──────────────────────┐
│  ingestion-service   │  :8081
│  Stores PDF in       │──── pdf.uploaded ────►
│  MongoDB GridFS      │                        │
└──────────────────────┘                        │
                                                ▼
                                  ┌──────────────────────┐
                                  │  parser-service       │  :8082
                                  │  PDFBox extraction    │──── pdf.parsed ────►
                                  │  Saves to MongoDB     │                      │
                                  └──────────────────────┘                      │
                                                                                 ▼
                                                                ┌──────────────────────┐
                                                                │  agent-service        │  :8083
                                                                │  Claude API           │◄──► Claude API
                                                                │  Tool-use loop        │──── report.ready ────►
                                                                └──────────────────────┘                        │
                                                                                                                ▼
                                                                                            ┌──────────────────────┐
                                                                                            │  report-service       │  :8084
                                                                                            │  Persists report      │
                                                                                            │  Exposes REST API     │
                                                                                            └──────────────────────┘
```

All inter-service communication happens via **Apache Kafka** events. Each service is independently deployable.

---

## Technology Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Runtime — virtual threads for high concurrency |
| Spring Boot | 4.0.3 | Application framework |
| Apache Kafka | 3.7.0 | Async event bus between services |
| MongoDB | 7.0 | Document storage + GridFS for PDF binary |
| Apache PDFBox | 3.0.2 | PDF text extraction |
| Claude API | claude-sonnet-4 | LLM agent for risk analysis |
| Spring Cloud Contract | 4.2.0 | Consumer driven contract testing |
| Gradle | 8.x | Multi-module build system |
| Docker | Latest | Local infrastructure |

---

## Microservices

| Service | Port | Responsibility |
|---|---|---|
| [ingestion-service](./ingestion-service/README.md) | 8081 | Accepts PDF uploads, stores in GridFS, publishes Kafka event |
| [parser-service](./parser-service/README.md) | 8082 | Extracts financial metrics from PDF using PDFBox |
| [agent-service](./agent-service/README.md) | 8083 | Claude AI agent — anomaly detection and risk summary |
| [report-service](./report-service/README.md) | 8084 | Persists reports, exposes REST API |

---

## Prerequisites

Before running the project make sure you have the following installed:

- **Java 21** — [Download here](https://adoptium.net)
- **Docker Desktop** — [Download here](https://www.docker.com/products/docker-desktop)
- **Git** — [Download here](https://git-scm.com)
- **Anthropic API Key** — [Get one here](https://console.anthropic.com) *(required for live Claude analysis)*

Verify your Java installation:

```bash
java -version
# Should output: openjdk version "21.x.x"
```

Verify Docker installation:

```bash
docker --version
docker compose version
```

---

## Project Setup

### Step 1 — Clone the repository

```bash
git clone https://github.com/your-username/finsight-ai.git
cd finsight-ai
```

### Step 2 — Set up environment variables

Copy the example environment file and fill in your values:

```bash
# Windows
copy .env.example .env

# Mac/Linux
cp .env.example .env
```

Open `.env` and add your values:

```properties
ANTHROPIC_API_KEY=sk-ant-your-actual-key-here
MONGODB_URI=mongodb://localhost:27017/finsight
KAFKA_BROKERS=localhost:9092
```

> **Important:** Never commit `.env` to version control. It is already listed in `.gitignore`.

### Step 3 — Start infrastructure

```bash
docker compose up -d
```

Verify all containers are running:

```bash
docker compose ps
```

Expected output:

```
NAME                    STATUS          PORTS
finsight-zookeeper      Up              2181/tcp
finsight-kafka          Up              0.0.0.0:9092->9092/tcp
finsight-mongodb        Up              0.0.0.0:27017->27017/tcp
```

### Step 4 — Build the project

```bash
# Windows
.\gradlew.bat clean build -x contractTest -x generateContractTests

# Mac/Linux
./gradlew clean build -x contractTest -x generateContractTests
```

Expected output: `BUILD SUCCESSFUL`

---

## Running the Application

Open **4 separate terminals** and start each service:

**Terminal 1 — ingestion-service:**

```bash
.\gradlew.bat :ingestion-service:bootRun
```

**Terminal 2 — parser-service:**

```bash
.\gradlew.bat :parser-service:bootRun
```

**Terminal 3 — agent-service:**

```bash
# Windows — set API key first in the same terminal
$env:ANTHROPIC_API_KEY="sk-ant-your-key-here"
.\gradlew.bat :agent-service:bootRun

# Mac/Linux
export ANTHROPIC_API_KEY="sk-ant-your-key-here"
./gradlew :agent-service:bootRun
```

**Terminal 4 — report-service:**

```bash
.\gradlew.bat :report-service:bootRun
```

Wait for all 4 services to show:

```
Started IngestionServiceApplication in X.XXX seconds
```

### Verify all services are healthy

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

Each should return: `{ "status": "UP" }`

---

## Testing the Pipeline

### Step 1 — Get a test PDF

Use any financial report PDF. Options:

- Download a real annual report from [annualreports.com](https://annualreports.com)
- Use Apple, Microsoft or any public company investor page
- Create a simple test PDF containing financial figures like revenue, net income and debt

### Step 2 — Upload the PDF

**Windows PowerShell:**

```powershell
curl -X POST http://localhost:8081/api/v1/documents/upload `
  -F "file=@C:\path\to\your\annual-report.pdf;type=application/pdf"
```

**Mac/Linux:**

```bash
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@/path/to/annual-report.pdf;type=application/pdf"
```

**Postman:**

1. Method: `POST`
2. URL: `http://localhost:8081/api/v1/documents/upload`
3. Body → form-data → key: `file`, type: `File`
4. Select your PDF and set content type to `application/pdf`
5. Click Send

### Step 3 — Note the documentId

```json
{
  "documentId": "3f7a1c2e-8b4d-4e9f-a2c1-d5e6f7890abc",
  "status": "UPLOADED"
}
```

### Step 4 — Watch the logs

You should see events flowing across all 4 terminals within 3 seconds.

### Step 5 — Fetch the final report

```bash
curl http://localhost:8084/api/v1/reports/3f7a1c2e-8b4d-4e9f-a2c1-d5e6f7890abc
```

Expected response:

```json
{
  "documentId": "3f7a1c2e-...",
  "filename": "annual-report.pdf",
  "riskSummary": "This company presents a HIGH risk profile...",
  "anomalies": [
    "ALERT: Company is reporting a net loss",
    "HIGH RISK: Debt-to-revenue ratio is 2.31"
  ],
  "calculatedRatios": {
    "profitMarginPct": -6.54,
    "debtToRevenueRatio": 2.31
  },
  "riskLevel": "HIGH",
  "status": "READY"
}
```

---

## API Reference

### ingestion-service — `http://localhost:8081`

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/documents/upload` | Upload a PDF for analysis |
| GET | `/actuator/health` | Service health check |

### report-service — `http://localhost:8084`

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/reports/{documentId}` | Get report by document ID |
| GET | `/api/v1/reports` | Get all reports |
| GET | `/api/v1/reports?riskLevel=HIGH` | Filter reports by risk level |
| GET | `/actuator/health` | Service health check |

---

## Project Structure

```
finsight-ai/
├── docker-compose.yml              ← Kafka + MongoDB infrastructure
├── build.gradle                    ← Root Gradle config
├── settings.gradle                 ← Module declarations
├── gradle/
│   └── libs.versions.toml          ← Centralised dependency versions
├── .env                            ← Local secrets (gitignored)
├── .env.example                    ← Template for environment variables
├── ingestion-service/              ← PDF upload and storage
├── parser-service/                 ← PDF text extraction
├── agent-service/                  ← Claude AI agent
└── report-service/                 ← Report persistence and API
```

---

## Performance Targets

| Metric | Target | How achieved |
|---|---|---|
| End-to-end processing | < 3 seconds | Virtual threads + parallel tool execution |
| Anomaly detection accuracy | 95%+ | Deterministic Java tools, not LLM estimation |
| Test coverage | 85%+ | JUnit 5 unit tests across all services |

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | Yes | Claude API key from console.anthropic.com |
| `MONGODB_URI` | No | MongoDB URI (defaults to localhost:27017) |
| `KAFKA_BROKERS` | No | Kafka address (defaults to localhost:9092) |

---

## Running Tests

```bash
# Run all tests
.\gradlew.bat test

# Run tests for a specific service
.\gradlew.bat :ingestion-service:test
.\gradlew.bat :parser-service:test
.\gradlew.bat :agent-service:test
.\gradlew.bat :report-service:test
```

---

## Stopping the Application

Stop all services with `Ctrl+C` in each terminal, then stop Docker infrastructure:

```bash
docker compose down
```

To also remove MongoDB data:

```bash
docker compose down -v
```