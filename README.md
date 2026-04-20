# Transaction-Alert-System
Real-time financial transaction monitoring with anomaly detection.

## Tech Stack
- **Java 17**
- **Spring Boot 3.2** — auto-configuration, REST APIs, scheduling, validation
- **Spring Kafka** — `@KafkaListener`, `KafkaTemplate`, topic auto-creation
- **Lombok** — `@Slf4j`, `@Data`, `@Builder`, `@RequiredArgsConstructor`
- **Apache Kafka 3.6 (KRaft)** — no Zookeeper required

---

## Project Structure

```
src/main/java/com/alertsystem/
├── TransactionAlertApplication.java      ← @SpringBootApplication entry point
├── config/
│   ├── KafkaTopicConfig.java             ← Topic beans (auto-created by Spring)
│   └── AlertThresholdProperties.java     ← @ConfigurationProperties from yml
├── model/
│   ├── Transaction.java                  ← @Data @Builder Lombok model
│   └── Alert.java                        ← @Data @Builder Lombok model
├── producer/
│   └── TransactionProducerService.java   ← KafkaTemplate<String, Transaction>
├── consumer/
│   └── TransactionConsumer.java          ← @KafkaListener (concurrency=3)
├── service/
│   ├── AnomalyDetectionService.java      ← 7 detection rules
│   ├── AlertPublisherService.java        ← KafkaTemplate<String, Alert>
│   └── TransactionSimulatorService.java  ← @PostConstruct + @Scheduled
└── controller/
    ├── TransactionController.java        ← REST endpoints
    └── GlobalExceptionHandler.java       ← @RestControllerAdvice
```

---

## Execution — Without Docker (Native Kafka)

### Prerequisites
```bash
java -version    # Java 17+
mvn -version     # Maven 3.8+
```

### Step 1 — Set up Kafka (KRaft mode, no Zookeeper)

```bash
# Download and configure Kafka
chmod +x scripts/setup-kafka-native.sh
./scripts/setup-kafka-native.sh
```

### Step 2 — Start Kafka (Terminal 1)

```bash
cd kafka_2.13-3.6.1
bin/kafka-server-start.sh config/kraft/server.properties
```

Wait for: `INFO Kafka Server started`

### Step 3 — Create Topics (Terminal 2)

```bash
cd kafka_2.13-3.6.1
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic financial-transactions --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic transaction-alerts --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic audit-log --partitions 1 --replication-factor 1
```

> Note: Spring Boot will also auto-create these topics on startup via `NewTopic` beans. Manual creation is optional but ensures they exist before first message.

### Step 4 — Build the Project (Terminal 2)

```bash
cd transaction-alert-system
mvn clean package -q
```

### Step 5 — Run the Application (Terminal 2)

```bash
java -jar target/transaction-alert-system-1.0.0.jar
```

The app will:
- Connect to Kafka at `localhost:9092`
- Start 3 consumer threads (one per partition)
- Run 7 demo scenarios after 3 seconds
- Continue sending random transactions every 10 seconds
- Expose REST APIs on port 8080

### Step 6 — Watch Transactions and Alerts (Terminal 3 & 4)

```bash
# Watch raw transactions
cd kafka_2.13-3.6.1
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic financial-transactions --from-beginning

# Watch generated alerts
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic transaction-alerts --from-beginning
```

---

## REST API Reference

### POST /api/transactions — Manually publish a transaction
```bash
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC-001",
    "amount": 75000,
    "currency": "USD",
    "type": "TRANSFER",
    "merchant": "International Wire",
    "location": "London, UK"
  }'
```

### POST /api/simulate — Re-run all 7 demo scenarios
```bash
curl -X POST http://localhost:8080/api/simulate
```

### GET /api/stats — Alert and consumer statistics
```bash
curl http://localhost:8080/api/stats
```

### GET /api/health — Health check
```bash
curl http://localhost:8080/api/health
```

### GET /actuator/health — Spring Boot Actuator health
```bash
curl http://localhost:8080/actuator/health
```

---

## Alert Thresholds (Tunable in application.yml)

```yaml
alert:
  thresholds:
    large-transaction: 10000.0       # HIGH alert
    critical-transaction: 50000.0    # CRITICAL alert
    max-transactions-per-minute: 5   # RAPID_TRANSACTIONS trigger
    round-amount-minimum: 1000.0     # ROUND_AMOUNT trigger
    failure-count: 3                 # MULTIPLE_FAILURES trigger
```

---

## Demo Scenarios

| # | Account | Scenario | Alert Expected |
|---|---------|----------|---------------|
| 1 | Mixed | Normal transactions | None |
| 2 | ACC-001 | $15,000 transfer | LARGE_TRANSACTION HIGH |
| 3 | ACC-002 | $75,000 transfer | LARGE_TRANSACTION CRITICAL |
| 4 | ACC-003 | 6 transactions in 2 sec | RAPID_TRANSACTIONS HIGH |
| 5 | ACC-004 | Flagged merchant | SUSPICIOUS_MERCHANT CRITICAL |
| 6 | ACC-005 | $5,000 round amount | ROUND_AMOUNT LOW |
| 7 | ACC-001 | 4 consecutive failures | MULTIPLE_FAILURES HIGH |

---

## Optional: Docker (if preferred over native)

```bash
docker-compose up -d
# Topics auto-created by Spring Boot on startup
mvn clean package -q
java -jar target/transaction-alert-system-1.0.0.jar
```
