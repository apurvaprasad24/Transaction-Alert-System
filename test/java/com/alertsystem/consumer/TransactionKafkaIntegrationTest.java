package com.alertsystem.consumer;

import com.alertsystem.model.Transaction;
import com.alertsystem.model.Transaction.TransactionStatus;
import com.alertsystem.model.Transaction.TransactionType;
import com.alertsystem.producer.TransactionProducerService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = {
        "financial-transactions",
        "transaction-alerts",
        "audit-log"
    }
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "simulator.enabled=false"   // disable auto-simulation during tests
})
@DisplayName("Kafka Producer → Consumer Integration")
class TransactionKafkaIntegrationTest {

    @Autowired
    private TransactionProducerService producerService;

    @Autowired
    private TransactionConsumer transactionConsumer;

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<Transaction> received = new AtomicReference<>();

    @KafkaListener(topics = "financial-transactions", groupId = "test-group")
    public void testListener(ConsumerRecord<String, Transaction> record) {
        received.set(record.value());
        latch.countDown();
    }

    @Test
    @DisplayName("Transaction published by producer is consumed by listener")
    void producerSendsTransactionAndConsumerReceivesIt() throws InterruptedException {
        Transaction tx = Transaction.create(
            "ACC-TEST", 250.00, "USD",
            TransactionType.DEBIT, "Amazon", "New York, US"
        );

        producerService.sendTransaction(tx);

        boolean messageReceived = latch.await(10, TimeUnit.SECONDS);

        assertThat(messageReceived).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().getAccountId()).isEqualTo("ACC-TEST");
        assertThat(received.get().getAmount()).isEqualTo(250.00);
        assertThat(received.get().getMerchant()).isEqualTo("Amazon");
    }

    @Test
    @DisplayName("Large transaction increments alert count")
    void largeTransactionTriggersAlert() throws InterruptedException {
        long alertsBefore = transactionConsumer.getAlertCount();

        Transaction tx = Transaction.create(
            "ACC-BIG", 80_000.00, "USD",
            TransactionType.TRANSFER, "International Wire", "London, UK"
        );
        tx.setStatus(TransactionStatus.PENDING);
        producerService.sendTransaction(tx);

        Thread.sleep(3000); // allow consumer to process

        assertThat(transactionConsumer.getAlertCount()).isGreaterThan(alertsBefore);
    }

    @Test
    @DisplayName("Consumer processed count increments per transaction")
    void processedCountIncrementsCorrectly() throws InterruptedException {
        long before = transactionConsumer.getProcessedCount();

        for (int i = 0; i < 3; i++) {
            producerService.sendTransaction(
                Transaction.create("ACC-CNT", 100, "USD", TransactionType.DEBIT, "Starbucks", "Chicago, US")
            );
        }

        Thread.sleep(3000);
        assertThat(transactionConsumer.getProcessedCount()).isGreaterThanOrEqualTo(before + 3);
    }
}
