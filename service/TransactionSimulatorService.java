package com.alertsystem.service;

import com.alertsystem.model.Transaction;
import com.alertsystem.model.Transaction.TransactionStatus;
import com.alertsystem.model.Transaction.TransactionType;
import com.alertsystem.producer.TransactionProducerService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSimulatorService {

    private final TransactionProducerService producerService;

    @Value("${simulator.enabled:true}")
    private boolean simulatorEnabled;

    private final Random random = new Random();
    private final AtomicBoolean demoRan = new AtomicBoolean(false);

    private static final List<String> ACCOUNTS = List.of(
        "ACC-001", "ACC-002", "ACC-003", "ACC-004", "ACC-005"
    );
    private static final List<String> NORMAL_MERCHANTS = List.of(
        "Amazon", "Walmart", "Starbucks", "Netflix", "Uber",
        "McDonald's", "Target", "Apple Store", "Shell Gas", "CVS Pharmacy"
    );
    private static final List<String> SUSPICIOUS_MERCHANTS = List.of(
        "CryptoFast", "QuickCash247", "UnknownMerchant", "OffshoreVault", "AnonPay"
    );
    private static final List<String> LOCATIONS = List.of(
        "New York, US", "Los Angeles, US", "Chicago, US",
        "London, UK", "Paris, France", "Tokyo, Japan", "Dubai, UAE"
    );

    /**
     * Run demo scenarios once on startup (after 3s delay for Kafka to connect).
     */
    @PostConstruct
    public void scheduleDemo() {
        if (!simulatorEnabled) return;
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                runDemoScenarios();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "demo-simulator").start();
    }

    /**
     * After demo, keep sending random transactions every 10 seconds.
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 20_000)
    public void sendRandomTransaction() {
        if (!simulatorEnabled) return;
        producerService.sendTransaction(generateNormalTransaction());
    }

    public void runDemoScenarios() throws InterruptedException {
        log.info("\n\n========= DEMO SCENARIO 1: Normal everyday transactions =========");
        for (int i = 0; i < 5; i++) {
            producerService.sendTransaction(generateNormalTransaction());
            Thread.sleep(300);
        }

        log.info("\n========= DEMO SCENARIO 2: Large transaction ($15,000) =========");
        producerService.sendTransaction(makeTransaction("ACC-001", 15_000, "USD", TransactionType.TRANSFER, "International Wire", "New York, US", TransactionStatus.PENDING));
        Thread.sleep(500);

        log.info("\n========= DEMO SCENARIO 3: CRITICAL transaction ($75,000) =========");
        producerService.sendTransaction(makeTransaction("ACC-002", 75_000, "USD", TransactionType.TRANSFER, "International Wire", "London, UK", TransactionStatus.PENDING));
        Thread.sleep(500);

        log.info("\n========= DEMO SCENARIO 4: Rapid burst — 6 transactions in 2 sec =========");
        for (int i = 0; i < 6; i++) {
            producerService.sendTransaction(makeTransaction("ACC-003", 200 + random.nextDouble() * 800, "USD", TransactionType.DEBIT, "Amazon", "Chicago, US", TransactionStatus.PENDING));
            Thread.sleep(200);
        }

        log.info("\n========= DEMO SCENARIO 5: Suspicious merchant =========");
        String suspMerchant = SUSPICIOUS_MERCHANTS.get(random.nextInt(SUSPICIOUS_MERCHANTS.size()));
        producerService.sendTransaction(makeTransaction("ACC-004", 1500, "USD", TransactionType.DEBIT, suspMerchant, "Dubai, UAE", TransactionStatus.PENDING));
        Thread.sleep(500);

        log.info("\n========= DEMO SCENARIO 6: Round amount ($5,000 exactly) =========");
        producerService.sendTransaction(makeTransaction("ACC-005", 5_000, "USD", TransactionType.WITHDRAWAL, "ATM Withdrawal", "Paris, France", TransactionStatus.PENDING));
        Thread.sleep(500);

        log.info("\n========= DEMO SCENARIO 7: Multiple consecutive failures =========");
        for (int i = 0; i < 4; i++) {
            producerService.sendTransaction(makeTransaction("ACC-001", 300, "USD", TransactionType.DEBIT, "Target", "New York, US", TransactionStatus.FAILED));
            Thread.sleep(200);
        }

        log.info("\n✅ All demo scenarios sent. Continuous mode active — sending every 10 seconds.");
        demoRan.set(true);
    }

    public Transaction generateNormalTransaction() {
        return makeTransaction(
            ACCOUNTS.get(random.nextInt(ACCOUNTS.size())),
            Math.round((10 + random.nextDouble() * 990) * 100.0) / 100.0,
            "USD",
            TransactionType.values()[random.nextInt(TransactionType.values().length)],
            NORMAL_MERCHANTS.get(random.nextInt(NORMAL_MERCHANTS.size())),
            LOCATIONS.get(random.nextInt(LOCATIONS.size())),
            TransactionStatus.PENDING
        );
    }

    private Transaction makeTransaction(String accountId, double amount, String currency,
                                        TransactionType type, String merchant,
                                        String location, TransactionStatus status) {
        Transaction t = Transaction.create(accountId, amount, currency, type, merchant, location);
        t.setStatus(status);
        return t;
    }
}
