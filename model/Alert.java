package com.alertsystem.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AlertType {
        LARGE_TRANSACTION,
        RAPID_TRANSACTIONS,
        UNUSUAL_LOCATION,
        SUSPICIOUS_MERCHANT,
        ROUND_AMOUNT,
        OFF_HOURS_TRANSACTION,
        MULTIPLE_FAILURES
    }

    @Builder.Default
    private String alertId = UUID.randomUUID().toString();

    private String transactionId;
    private String accountId;
    private AlertType alertType;
    private AlertSeverity severity;
    private String message;
    private double amount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Builder.Default
    private boolean resolved = false;

    // Convenience factory
    public static Alert of(Transaction tx, AlertType type, AlertSeverity severity, String message) {
        return Alert.builder()
            .transactionId(tx.getTransactionId())
            .accountId(tx.getAccountId())
            .alertType(type)
            .severity(severity)
            .message(message)
            .amount(tx.getAmount())
            .build();
    }
}
