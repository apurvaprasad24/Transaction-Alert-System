package com.alertsystem.service;

import com.alertsystem.config.AlertThresholdProperties;
import com.alertsystem.model.Alert;
import com.alertsystem.model.Alert.AlertSeverity;
import com.alertsystem.model.Alert.AlertType;
import com.alertsystem.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {

    private final AlertThresholdProperties thresholds;

    private static final Set<String> SUSPICIOUS_MERCHANTS = Set.of(
        "CryptoFast", "QuickCash247", "UnknownMerchant", "OffshoreVault", "AnonPay"
    );

    // Per-account in-memory state
    private final Map<String, List<Long>> transactionTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, String> lastLocations = new ConcurrentHashMap<>();

    /**
     * Run all detection rules against a transaction and return any triggered alerts.
     */
    public List<Alert> analyze(Transaction transaction) {
        List<Alert> alerts = new ArrayList<>();
        String accountId = transaction.getAccountId();

        trackTransaction(accountId);

        alerts.addAll(checkLargeTransaction(transaction));
        alerts.addAll(checkRapidTransactions(transaction));
        alerts.addAll(checkUnusualLocation(transaction));
        alerts.addAll(checkSuspiciousMerchant(transaction));
        alerts.addAll(checkRoundAmount(transaction));
        alerts.addAll(checkOffHours(transaction));
        alerts.addAll(checkMultipleFailures(transaction));

        return alerts;
    }

    // ── Rule 1: Large / Critical transaction ─────────────────────────────────
    private List<Alert> checkLargeTransaction(Transaction tx) {
        if (tx.getAmount() >= thresholds.getCriticalTransaction()) {
            return List.of(Alert.of(tx, AlertType.LARGE_TRANSACTION, AlertSeverity.CRITICAL,
                String.format("CRITICAL: %.2f %s exceeds high-risk limit of %.0f",
                    tx.getAmount(), tx.getCurrency(), thresholds.getCriticalTransaction())));
        }
        if (tx.getAmount() >= thresholds.getLargeTransaction()) {
            return List.of(Alert.of(tx, AlertType.LARGE_TRANSACTION, AlertSeverity.HIGH,
                String.format("Large transaction: %.2f %s exceeds %.0f threshold",
                    tx.getAmount(), tx.getCurrency(), thresholds.getLargeTransaction())));
        }
        return List.of();
    }

    // ── Rule 2: Too many transactions in one minute ───────────────────────────
    private List<Alert> checkRapidTransactions(Transaction tx) {
        List<Long> timestamps = transactionTimestamps.getOrDefault(tx.getAccountId(), List.of());
        long oneMinuteAgo = System.currentTimeMillis() - 60_000;
        long recentCount = timestamps.stream().filter(t -> t > oneMinuteAgo).count();

        if (recentCount > thresholds.getMaxTransactionsPerMinute()) {
            return List.of(Alert.of(tx, AlertType.RAPID_TRANSACTIONS, AlertSeverity.HIGH,
                String.format("Rapid transactions: %d in last 60s (limit: %d)",
                    recentCount, thresholds.getMaxTransactionsPerMinute())));
        }
        return List.of();
    }

    // ── Rule 3: Location changed since last transaction ────────────────────
    private List<Alert> checkUnusualLocation(Transaction tx) {
        String prev = lastLocations.get(tx.getAccountId());
        lastLocations.put(tx.getAccountId(), tx.getLocation());

        if (prev != null && !prev.equals(tx.getLocation())) {
            return List.of(Alert.of(tx, AlertType.UNUSUAL_LOCATION, AlertSeverity.MEDIUM,
                String.format("Location change: '%s' → '%s'", prev, tx.getLocation())));
        }
        return List.of();
    }

    // ── Rule 4: Merchant on watchlist ─────────────────────────────────────
    private List<Alert> checkSuspiciousMerchant(Transaction tx) {
        if (SUSPICIOUS_MERCHANTS.contains(tx.getMerchant())) {
            return List.of(Alert.of(tx, AlertType.SUSPICIOUS_MERCHANT, AlertSeverity.CRITICAL,
                String.format("Transaction with flagged merchant: '%s'", tx.getMerchant())));
        }
        return List.of();
    }

    // ── Rule 5: Suspiciously round amount ─────────────────────────────────
    private List<Alert> checkRoundAmount(Transaction tx) {
        if (tx.getAmount() >= thresholds.getRoundAmountMinimum() && tx.getAmount() % 1000 == 0) {
            return List.of(Alert.of(tx, AlertType.ROUND_AMOUNT, AlertSeverity.LOW,
                String.format("Round amount detected: %.0f %s", tx.getAmount(), tx.getCurrency())));
        }
        return List.of();
    }

    // ── Rule 6: Off-hours (11 PM – 5 AM) ──────────────────────────────────
    private List<Alert> checkOffHours(Transaction tx) {
        LocalTime now = LocalTime.now();
        if (now.isAfter(LocalTime.of(23, 0)) || now.isBefore(LocalTime.of(5, 0))) {
            return List.of(Alert.of(tx, AlertType.OFF_HOURS_TRANSACTION, AlertSeverity.LOW,
                String.format("Off-hours transaction at %s", now)));
        }
        return List.of();
    }

    // ── Rule 7: Multiple consecutive failures ─────────────────────────────
    private List<Alert> checkMultipleFailures(Transaction tx) {
        if (tx.getStatus() == Transaction.TransactionStatus.FAILED) {
            int count = failureCounts.merge(tx.getAccountId(), 1, Integer::sum);
            if (count >= thresholds.getFailureCount()) {
                return List.of(Alert.of(tx, AlertType.MULTIPLE_FAILURES, AlertSeverity.HIGH,
                    String.format("Account has %d consecutive failed transactions", count)));
            }
        } else {
            failureCounts.put(tx.getAccountId(), 0);
        }
        return List.of();
    }

    private void trackTransaction(String accountId) {
        transactionTimestamps
            .computeIfAbsent(accountId, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(System.currentTimeMillis());
    }
}
