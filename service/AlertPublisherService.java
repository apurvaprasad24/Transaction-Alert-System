package com.alertsystem.service;

import com.alertsystem.model.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertPublisherService {

    private final KafkaTemplate<String, Alert> kafkaTemplate;

    @Value("${kafka.topics.alerts}")
    private String alertsTopic;

    private final AtomicLong totalAlerts = new AtomicLong(0);
    private final Map<Alert.AlertSeverity, AtomicLong> countBySeverity = new EnumMap<>(Alert.AlertSeverity.class) {{
        for (Alert.AlertSeverity s : Alert.AlertSeverity.values()) put(s, new AtomicLong(0));
    }};

    public void publishAlerts(List<Alert> alerts) {
        alerts.forEach(this::publishAlert);
    }

    private void publishAlert(Alert alert) {
        logAlert(alert);

        kafkaTemplate.send(alertsTopic, alert.getAccountId(), alert)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish alert [{}]: {}", alert.getAlertId(), ex.getMessage());
                } else {
                    log.debug("Alert published → partition={} offset={}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });

        totalAlerts.incrementAndGet();
        countBySeverity.get(alert.getSeverity()).incrementAndGet();
    }

    private void logAlert(Alert alert) {
        String emoji = switch (alert.getSeverity()) {
            case CRITICAL -> "🚨";
            case HIGH     -> "🔴";
            case MEDIUM   -> "🟡";
            case LOW      -> "🟢";
        };
        String line = "═".repeat(65);
        log.warn("\n{}\n{} ALERT [{} — {}]\n  Account  : {}\n  Message  : {}\n  Amount   : {}\n  Txn ID   : {}\n  Alert ID : {}\n{}",
            line, emoji, alert.getSeverity(), alert.getAlertType(),
            alert.getAccountId(), alert.getMessage(), alert.getAmount(),
            alert.getTransactionId(), alert.getAlertId(), line);
    }

    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new java.util.LinkedHashMap<>();
        stats.put("total", totalAlerts.get());
        countBySeverity.forEach((k, v) -> stats.put(k.name(), v.get()));
        return stats;
    }
}
