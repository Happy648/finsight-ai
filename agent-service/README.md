# agent-service

The agent-service is the **intelligence layer** of the FinSight AI pipeline. It implements an autonomous AI agent using the Claude API tool-use pattern to orchestrate financial analysis, detect anomalies, calculate ratios, and generate plain-English risk summaries.

---

## Table of Contents

- [Responsibility](#responsibility)
- [How the Agentic Loop Works](#how-the-agentic-loop-works)
- [Available Tools](#available-tools)
- [Real Example — End to End](#real-example--end-to-end)
- [Kafka Events](#kafka-events)
- [MongoDB Storage](#mongodb-storage)
- [Mock Mode](#mock-mode)
- [Configuration](#configuration)
- [Running Locally](#running-locally)
- [Running Tests](#running-tests)
- [Package Structure](#package-structure)

---

## Responsibility

The agent-service:

1. Consumes structured financial metrics from the `pdf.parsed` topic
2. Sends them to Claude API along with available tool definitions
3. Runs a tool-use loop — executing Java tools whenever Claude requests them
4. Collects the final plain-English risk summary when Claude is done
5. Saves the result to MongoDB and publishes to `report.ready`

---

## How the Agentic Loop Works

Unlike a simple API call, the agent uses a **multi-turn conversation loop**. Claude decides which tools to call and in what order. Your Java code executes whatever Claude requests and feeds results back.

```
1. Build message with financial data + tool definitions
           │
           ▼
2. Call Claude API
           │
           ▼
3. Check stop_reason
     ├── "tool_use" → execute requested Java tool
     │                add result to conversation history
     │                loop back to step 2
     │
     └── "end_turn" → extract final plain-English summary
                      save to MongoDB
                      publish report.ready event
```

### What the conversation looks like

The `messages` list grows with every round trip. Claude sees the full history each time so it knows what it has already done:

```
Turn 1: user      → "Analyse TechCorp financials. Revenue: 5.2B, Net Loss: 340M..."
Turn 2: assistant → tool_use: detect_anomalies({ financial_metrics: {...} })
Turn 3: user      → tool_result: ["ALERT: Net loss", "HIGH RISK: Debt ratio 2.31"]
Turn 4: assistant → tool_use: calculate_ratios({ financial_metrics: {...} })
Turn 5: user      → tool_result: { profitMarginPct: -6.54, debtToRevenueRatio: 2.31 }
Turn 6: assistant → tool_use: determine_risk_level({ anomalies: [...], ratios: {...} })
Turn 7: user      → tool_result: "HIGH"
Turn 8: assistant → end_turn: "TechCorp presents HIGH risk. Net loss of $340M..."
```

---

## Available Tools

Three tools are defined and exposed to Claude. Claude autonomously decides when and in what order to call them.

### `detect_anomalies`

Analyses financial metrics for irregularities and risk indicators.

**Input:** `financial_metrics` map from parser-service  
**Returns:** List of anomaly strings, each prefixed with severity

```json
[
  "ALERT: Company is reporting a net loss",
  "HIGH RISK: Debt-to-revenue ratio is 2.31 — exceeds 2.0 threshold",
  "WARNING: Negative operating cash flow detected"
]
```

Severity prefixes:

| Prefix | Meaning |
|---|---|
| `CRITICAL` | Critical data is missing |
| `HIGH RISK` | Debt ratio exceeds threshold |
| `ALERT` | Net loss detected |
| `WARNING` | Negative cash flow or missing optional data |

### `calculate_ratios`

Computes standard financial ratios from raw metric values.

**Input:** `financial_metrics` map  
**Returns:** Map of computed ratios

| Ratio | Formula |
|---|---|
| `profitMarginPct` | (netIncome / revenue) × 100 |
| `debtToRevenueRatio` | totalDebt / revenue |
| `cashFlowToRevenuePct` | (cashFlow / revenue) × 100 |

```json
{
  "profitMarginPct": -6.54,
  "debtToRevenueRatio": 2.31,
  "cashFlowToRevenuePct": -1.71
}
```

### `determine_risk_level`

Classifies overall risk based on anomalies and ratios.

**Input:** `anomalies` list + `ratios` map  
**Returns:** `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`

| Condition | Risk level |
|---|---|
| Any CRITICAL anomaly | `CRITICAL` |
| Any HIGH RISK or ALERT anomaly | `HIGH` |
| More than 2 anomalies | `MEDIUM` |
| No anomalies | `LOW` |

---

## Real Example — End to End

**Input metrics from parser-service:**

```json
{
  "revenue": 5200000000,
  "netIncome": -340000000,
  "totalDebt": 12000000000,
  "cashFlow": -89000000,
  "hasNegativeNetIncome": true,
  "missingRevenue": false
}
```

**Claude calls `detect_anomalies` → your Java tool returns:**

```json
[
  "ALERT: Company is reporting a net loss",
  "HIGH RISK: Debt-to-revenue ratio is 2.31 — exceeds 2.0 threshold",
  "WARNING: Negative operating cash flow detected"
]
```

**Claude calls `calculate_ratios` → your Java tool returns:**

```json
{
  "profitMarginPct": -6.54,
  "debtToRevenueRatio": 2.31,
  "cashFlowToRevenuePct": -1.71
}
```

**Claude calls `determine_risk_level` → your Java tool returns:**

```
"HIGH"
```

**Claude produces final `end_turn` response:**

```
TechCorp's Q3 2024 financial report presents a HIGH risk profile that warrants
serious attention. The company recorded a net loss of $340 million despite
generating $5.2 billion in revenue, reflecting a profit margin of -6.5%.
The debt burden of $12 billion is more than twice annual revenue, combined with
negative operating cash flow indicating the company cannot currently fund its
own operations.
```

---

## Kafka Events

### Consumed — `pdf.parsed`

Receives structured financial metrics from parser-service.

### Produced — `report.ready`

**Topic:** `report.ready`  
**Key:** `documentId`

**Payload:**

```json
{
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
  "generatedAt": "2025-10-01T09:33:57.221Z"
}
```

---

## MongoDB Storage

### Collection — `agent_results`

```json
{
  "_id": "b2c3d4e5-...",
  "documentId": "3f7a1c2e-...",
  "filename": "TechCorp-Q3-2024.pdf",
  "riskSummary": "TechCorp presents a HIGH risk profile...",
  "anomalies": ["ALERT: Net loss", "HIGH RISK: Debt ratio 2.31"],
  "calculatedRatios": { "profitMarginPct": -6.54 },
  "riskLevel": "HIGH",
  "toolCallCount": 3,
  "processingTimeMs": 1843,
  "generatedAt": "2025-10-01T09:33:57.221Z"
}
```

`processingTimeMs` is used to monitor performance against the sub-3 second target.

---

## Mock Mode

To test the pipeline without an Anthropic API key or credits, enable mock mode in `application.properties`:

```properties
anthropic.mock-mode=true
```

Mock mode runs all three Java tools with real anomaly detection and ratio calculation but generates a templated summary instead of calling Claude. The pipeline is fully functional — only the narrative summary text is mocked.

To switch to live Claude responses:

```properties
anthropic.mock-mode=false
```

---

## Configuration

`src/main/resources/application.properties`:

```properties
spring.application.name=agent-service
server.port=8083

spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/finsight}

spring.kafka.bootstrap-servers=${KAFKA_BROKERS:localhost:9092}
spring.kafka.consumer.group-id=agent-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer

anthropic.api.key=${ANTHROPIC_API_KEY}
anthropic.model=claude-sonnet-4-20250514
anthropic.max-tokens=4096
anthropic.mock-mode=false

management.endpoints.web.exposure.include=health,info,metrics
```

---

## Running Locally

**Prerequisites:** Docker Desktop running, all other services running, `ANTHROPIC_API_KEY` set

```bash
# Windows — set API key in the same terminal session
$env:ANTHROPIC_API_KEY="sk-ant-your-key-here"
.\gradlew.bat :agent-service:bootRun

# Mac/Linux
export ANTHROPIC_API_KEY="sk-ant-your-key-here"
./gradlew :agent-service:bootRun
```

Service starts on `http://localhost:8083`

---

## Running Tests

```bash
# Windows
.\gradlew.bat :agent-service:test

# Mac/Linux
./gradlew :agent-service:test
```

### Tests included

| Test class | What it tests |
|---|---|
| `AnomalyDetectionToolTest` | Missing revenue CRITICAL, high debt HIGH RISK, net loss ALERT, healthy returns empty list |
| `RatioCalculatorToolTest` | Full metrics return all ratios, missing fields are skipped, empty metrics return empty map |
| `ToolExecutorServiceTest` | Each tool name routes to correct Java method, unknown tool returns error JSON |

---

## Package Structure

```
src/main/java/com/finsight/agent/
├── AgentServiceApplication.java           ← Spring Boot entry point (@EnableRetry)
├── config/
│   ├── AnthropicConfig.java               ← RestClient setup for Claude API
│   ├── KafkaConfig.java                   ← Kafka topic definition
│   └── JacksonConfig.java                 ← ObjectMapper configuration
├── model/
│   └── AgentResult.java                   ← MongoDB document model
├── repository/
│   └── AgentResultRepository.java         ← Spring Data MongoDB repository
├── tools/
│   ├── AnomalyDetectionTool.java          ← Detects financial anomalies
│   ├── RatioCalculatorTool.java           ← Computes financial ratios
│   └── RiskSummaryTool.java               ← Classifies risk level
└── service/
    ├── AgentOrchestrationService.java     ← Claude tool-use loop
    ├── ToolExecutorService.java           ← Executes tool calls from Claude
    ├── PdfParsedConsumer.java             ← Listens to pdf.parsed topic
    ├── ReportReadyProducer.java           ← Publishes to report.ready topic
    └── event/
        ├── PdfParsedEvent.java            ← Consumed event from parser-service
        └── ReportReadyEvent.java          ← Produced event to report-service
```