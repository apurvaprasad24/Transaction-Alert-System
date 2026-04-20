package com.alertsystem.consumer;

import com.alertsystem.model.Alert;
import com.alertsystem.model.Transaction;
import com.alertsystem.service.AlertPublisherService;
import com.alertsystem.service.AnomalyDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionConsumer {

    private final AnomalyDetectionService detectionService;
    private final AlertPublisherService alertPublisherService;

    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong alertCount = new AtomicLong(0);

    /**
     * Listens to the financial-transactions topic.
     * groupId and topic are pulled from application.yml.
     * concurrency=3 means 3 consumer threads — one per partition.
     */
    @KafkaListener(
        topics = "${kafka.topics.transactions}",
        groupId = "${spring.kafka.consumer.group-id}",
        concurrency = "3"
    )
    public void consume(ConsumerRecord<String, Transaction> record, Acknowledgment ack) {
        Transaction transaction = record.value();
        long count = processedCount.incrementAndGet();

        log.info("[{}] Processing → account={} amount={} {} merchant={} type={}",
            count,
            transaction.getAccountId(),
            transaction.getAmount(),
            transaction.getCurrency(),
            transaction.getMerchant(),
            transaction.getType());

        try {
            List<Alert> alerts = detectionService.analyze(transaction);

            if (alerts.isEmpty()) {
                log.info("  ✅ Clean — no anomalies detected.");
            } else {
                alertCount.addAndGet(alerts.size());
                alertPublisherService.publishAlerts(alerts);
            }

            // Manual acknowledgment — commits offset only after successful processing
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing transaction [{}]: {}",
                transaction.getTransactionId(), e.getMessage(), e);
            // Do NOT ack — message will be redelivered
        }
    }

    /**
     * Listens to the alerts topic — logs confirmed alerts for audit trail.
     */
    @KafkaListener(
        topics = "${kafka.topics.alerts}",
        groupId = "audit-logger-group"
    )
    public void auditAlert(ConsumerRecord<String, Alert> record, Acknowledgment ack) {
        Alert alert = record.value();
        log.info("📋 AUDIT → alertId={} type={} severity={} account={}",
            alert.getAlertId(), alert.getAlertType(), alert.getSeverity(), alert.getAccountId());
        ack.acknowledge();
    }

    public long getProcessedCount() { return processedCount.get(); }
    public long getAlertCount() { return alertCount.get(); }
}
