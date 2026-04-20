package com.alertsystem.service;

import com.alertsystem.config.AlertThresholdProperties;
import com.alertsystem.model.Alert;
import com.alertsystem.model.Alert.AlertSeverity;
import com.alertsystem.model.Alert.AlertType;
import com.alertsystem.model.Transaction;
import com.alertsystem.model.Transaction.TransactionStatus;
import com.alertsystem.model.Transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnomalyDetectionService")
class AnomalyDetectionServiceTest {

    private AnomalyDetectionService detectionService;

    @BeforeEach
    void setUp() {
        AlertThresholdProperties thresholds = new AlertThresholdProperties();
        thresholds.setLargeTransaction(10_000.0);
        thresholds.setCriticalTransaction(50_000.0);
        thresholds.setMaxTransactionsPerMinute(5);
        thresholds.setRoundAmountMinimum(1_000.0);
        thresholds.setFailureCount(3);
        detectionService = new AnomalyDetectionService(thresholds);
    }

    // ── Helper ────────────────────────────────────────────────────────────
    private Transaction tx(String accountId, double amount, String merchant,
                            String location, TransactionStatus status) {
        Transaction t = Transaction.create(accountId, amount, "USD",
            TransactionType.DEBIT, merchant, location);
        t.setStatus(status);
        return t;
    }

    // ── Rule 1: Large Transaction ─────────────────────────────────────────
    @Nested
    @DisplayName("Rule 1 — Large Transaction")
    class LargeTransactionTests {

        @Test
        @DisplayName("No alert for normal amount")
        void noAlertBelowThreshold() {
            List<Alert> alerts = detectionService.analyze(tx("ACC-1", 500, "Amazon", "NY", TransactionStatus.PENDING));
            assertThat(alerts).noneMatch(a -> a.getAlertType() == AlertType.LARGE_TRANSACTION);
        }

        @Test
        @DisplayName("HIGH alert for amount >= 10,000")
        void highAlertForLargeAmount() {
            List<Alert> alerts = detectionService.analyze(tx("ACC-1", 15_000, "Wire", "NY", TransactionStatus.PENDING));
            assertThat(alerts)
                .anyMatch(a -> a.getAlertType() == AlertType.LARGE_TRANSACTION
                    && a.getSeverity() == AlertSeverity.HIGH);
        }

        @Test
        @DisplayName("CRITICAL alert for amount >= 50,000")
        void criticalAlertForVeryLargeAmount() {
            List<Alert> alerts = detectionService.analyze(tx("ACC-1", 75_000, "Wire", "NY", TransactionStatus.PENDING));
            assertThat(alerts)
                .anyMatch(a -> a.getAlertType() == AlertType.LARGE_TRANSACTION
                    && a.getSeverity() == AlertSeverity.CRITICAL);
        }

        @Test
        @DisplayName("Exactly at large threshold triggers HIGH")
        void exactlyAtLargeThreshold() {
            List<Alert> alerts = detectionService.analyze(tx("ACC-1", 10_000, "Wire", "NY", TransactionStatus.PENDING));
            assertThat(alerts)
                .anyMatch(a -> a.getAlertType() == AlertType.LARGE_TRANSACTION
                    && a.getSeverity() == AlertSeverity.HIGH);
        }
    }

    // ── Rule 3: Unusual Location ──────────────────────────────────────────
    @Nested
    @DisplayName("Rule 3 — Unusual Location")
    class UnusualLocationTests {

        @Test
        @DisplayName("No alert on first transaction for an account")
        void noAlertOnFirstTransaction() {
            List<Alert> alerts = detectionService.analyze(tx("ACC-LOC-1", 200, "Amazon", "New York, US", TransactionStatus.PENDING));
            assertThat(alerts).noneMatch(a -> a.getAlertType() == AlertType.UNUSUAL_LOCATION);
        }

        @Test
        @DisplayName("Alert when location changes")
        void alertOnLocationChange() {
            detectionService.analyze(tx("ACC-LOC-2", 200, "Amazon", "New York, US", TransactionStatus.PENDING));
            List<Alert> alerts = detectionService.analyze(tx("ACC-LOC-2", 200, "Starbucks", "Tokyo, Japan", TransactionStatus.PENDING));
            assertThat(alerts).anyMatch(a -> a.getAlertType() == AlertType.UNUSUAL_LOCATION);
        }

        @Test
        @DisplayName("No alert when location stays the same")
        void noAlertSameLocation() {
            detectionService.analyze(tx("ACC-LOC-3", 200, "Amazon", "London, UK", TransactionStatus.PENDING));
            List<Alert> alerts = detectionService.analyze(tx("ACC-LOC-3", 200, "Tesco", "London, UK", TransactionStatus.PENDING));
            assertThat(alerts).noneMatch(a -> a.getAlertType() == AlertType.UNUSUAL_LOCATION);
        }
    }

    // ── Rule 4: Suspicious Merchant ───────────────────────────────────────
    @Nested
    @DisplayName("Rule 4 — Suspicious Merchant")
    class SuspiciousMerchantTests {

        @Test
        @DisplayName("CRITICAL alert for flagged merchant")
        void criticalAlertForSuspiciousMerchant() {
            List<Alert> alerts = detectionService.analyze(tx("ACC-1", 500, "CryptoFast", "NY", TransactionStatus.PENDING));
            assertThat(alerts)
                .anyMatch(a -> a.getAlertType() == AlertType.SUSPICIOUS_MERCHANT
                    && a.getSeverity() == AlertSeverity.CRITICAL);
        }

        @Test
        @DisplayName("No alert for legitimate merchant")
        void noAlertForLegitMerchant() {
            List<Alert> alerts = detectionService.analyze(tx("ACC-1", 500, "Walmart", "NY", TransactionStatus.PENDING));
            assertThat(alerts).noneMatch(a -> a.getAlertType() == AlertType.SUSPICIOUS_MERCHANT);
        }
    }

    // ── Rule 5: Round Amount ──────────────────────────────────────────────
    @Nested
    @DisplayName("Rule 5 — Round Amount")
    class RoundAmountTests {

        @Test
        @DisplayName("LOW alert for round amount >= 1000")
        void alertForRoundAmount() {
            List<Alert> alerts = detectionService.analyze(tx("ACC-1", 5_000, "ATM", "NY", TransactionStatus.PENDING));
            assertThat(alerts).anyMatch(a -> a.getAlertType() == AlertType.ROUND_AMOUNT);
        }

        @Test
        @DisplayName("No alert for non-round amount")
        void noAlertForNonRoundAmount() {
            List<Alert> alerts = detectionService.analyze(tx("ACC-1", 1_234.56, "Coffee", "NY", TransactionStatus.PENDING));
            assertThat(alerts).noneMatch(a -> a.getAlertType() == AlertType.ROUND_AMOUNT);
        }

        @Test
        @DisplayName("No alert for small round amount below minimum")
        void noAlertSmallRoundAmount() {
            List<Alert> alerts = detectionService.analyze(tx("ACC-1", 100, "Cafe", "NY", TransactionStatus.PENDING));
            assertThat(alerts).noneMatch(a -> a.getAlertType() == AlertType.ROUND_AMOUNT);
        }
    }

    // ── Rule 7: Multiple Failures ─────────────────────────────────────────
    @Nested
    @DisplayName("Rule 7 — Multiple Failures")
    class MultipleFailuresTests {

        @Test
        @DisplayName("No alert for fewer than 3 failures")
        void noAlertBeforeThreshold() {
            detectionService.analyze(tx("ACC-F1", 100, "Target", "NY", TransactionStatus.FAILED));
            List<Alert> alerts = detectionService.analyze(tx("ACC-F1", 100, "Target", "NY", TransactionStatus.FAILED));
            assertThat(alerts).noneMatch(a -> a.getAlertType() == AlertType.MULTIPLE_FAILURES);
        }

        @Test
        @DisplayName("HIGH alert on 3rd consecutive failure")
        void alertOnThirdFailure() {
            detectionService.analyze(tx("ACC-F2", 100, "Target", "NY", TransactionStatus.FAILED));
            detectionService.analyze(tx("ACC-F2", 100, "Target", "NY", TransactionStatus.FAILED));
            List<Alert> alerts = detectionService.analyze(tx("ACC-F2", 100, "Target", "NY", TransactionStatus.FAILED));
            assertThat(alerts)
                .anyMatch(a -> a.getAlertType() == AlertType.MULTIPLE_FAILURES
                    && a.getSeverity() == AlertSeverity.HIGH);
        }

        @Test
        @DisplayName("Counter resets after a successful transaction")
        void counterResetsOnSuccess() {
            detectionService.analyze(tx("ACC-F3", 100, "Target", "NY", TransactionStatus.FAILED));
            detectionService.analyze(tx("ACC-F3", 100, "Target", "NY", TransactionStatus.FAILED));
            detectionService.analyze(tx("ACC-F3", 100, "Target", "NY", TransactionStatus.COMPLETED)); // reset
            List<Alert> alerts = detectionService.analyze(tx("ACC-F3", 100, "Target", "NY", TransactionStatus.FAILED));
            assertThat(alerts).noneMatch(a -> a.getAlertType() == AlertType.MULTIPLE_FAILURES);
        }
    }

    // ── Alert metadata ────────────────────────────────────────────────────
    @Nested
    @DisplayName("Alert metadata")
    class AlertMetadataTests {

        @Test
        @DisplayName("Alert carries correct transactionId and accountId")
        void alertHasCorrectIds() {
            Transaction t = tx("ACC-META", 75_000, "Wire", "NY", TransactionStatus.PENDING);
            List<Alert> alerts = detectionService.analyze(t);
            assertThat(alerts).allMatch(a ->
                a.getTransactionId().equals(t.getTransactionId()) &&
                a.getAccountId().equals("ACC-META")
            );
        }

        @Test
        @DisplayName("Alert ID is always unique")
        void alertIdIsUnique() {
            List<Alert> a1 = detectionService.analyze(tx("ACC-U1", 75_000, "Wire", "NY", TransactionStatus.PENDING));
            List<Alert> a2 = detectionService.analyze(tx("ACC-U2", 75_000, "Wire", "NY", TransactionStatus.PENDING));
            assertThat(a1.get(0).getAlertId()).isNotEqualTo(a2.get(0).getAlertId());
        }
    }
}
