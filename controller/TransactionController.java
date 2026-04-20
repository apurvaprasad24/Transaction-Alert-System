package com.alertsystem.controller;

import com.alertsystem.consumer.TransactionConsumer;
import com.alertsystem.model.Transaction;
import com.alertsystem.producer.TransactionProducerService;
import com.alertsystem.service.AlertPublisherService;
import com.alertsystem.service.TransactionSimulatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionProducerService producerService;
    private final TransactionSimulatorService simulatorService;
    private final AlertPublisherService alertPublisherService;
    private final TransactionConsumer transactionConsumer;

    /**
     * POST /api/transactions
     * Manually publish a single transaction to Kafka.
     *
     * Example body:
     * {
     *   "accountId": "ACC-001",
     *   "amount": 5000,
     *   "currency": "USD",
     *   "type": "DEBIT",
     *   "merchant": "Amazon",
     *   "location": "New York, US"
     * }
     */
    @PostMapping("/transactions")
    public ResponseEntity<Map<String, String>> publishTransaction(
            @Valid @RequestBody Transaction transaction) {

        if (transaction.getTransactionId() == null) {
            transaction = Transaction.create(
                transaction.getAccountId(), transaction.getAmount(),
                transaction.getCurrency(), transaction.getType(),
                transaction.getMerchant(), transaction.getLocation()
            );
        }
        producerService.sendTransaction(transaction);
        log.info("Transaction published via REST: {}", transaction.getTransactionId());
        return ResponseEntity.accepted().body(Map.of(
            "status", "accepted",
            "transactionId", transaction.getTransactionId(),
            "message", "Transaction sent to Kafka for processing"
        ));
    }

    /**
     * POST /api/simulate
     * Re-run all 7 demo scenarios on demand.
     */
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, String>> runSimulation() throws InterruptedException {
        log.info("Manual simulation triggered via REST");
        new Thread(() -> {
            try {
                simulatorService.runDemoScenarios();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        return ResponseEntity.accepted().body(Map.of(
            "status", "started",
            "message", "Demo scenarios are running — check logs for alerts"
        ));
    }

    /**
     * GET /api/stats
     * Returns alert statistics and consumer metrics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
            "consumer", Map.of(
                "processedTransactions", transactionConsumer.getProcessedCount(),
                "alertsGenerated", transactionConsumer.getAlertCount()
            ),
            "alerts", alertPublisherService.getStatistics()
        ));
    }

    /**
     * GET /api/health
     * Simple health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "transaction-alert-system"
        ));
    }
}
