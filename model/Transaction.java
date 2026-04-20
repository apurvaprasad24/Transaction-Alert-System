package com.alertsystem.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    public enum TransactionType {
        DEBIT, CREDIT, TRANSFER, WITHDRAWAL, DEPOSIT
    }

    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, FLAGGED
    }

    private String transactionId;

    @NotBlank(message = "Account ID is required")
    private String accountId;

    @Min(value = 0, message = "Amount must be positive")
    private double amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    private String merchant;
    private String location;
    private String ipAddress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // Factory method for quick creation
    public static Transaction create(String accountId, double amount, String currency,
                                     TransactionType type, String merchant, String location) {
        return Transaction.builder()
            .transactionId(UUID.randomUUID().toString())
            .accountId(accountId)
            .amount(amount)
            .currency(currency)
            .type(type)
            .merchant(merchant)
            .location(location)
            .status(TransactionStatus.PENDING)
            .timestamp(LocalDateTime.now())
            .ipAddress(generateRandomIp())
            .build();
    }

    private static String generateRandomIp() {
        return String.format("%d.%d.%d.%d",
            (int)(Math.random() * 255), (int)(Math.random() * 255),
            (int)(Math.random() * 255), (int)(Math.random() * 255));
    }
}
