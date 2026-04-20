package com.alertsystem.producer;

import com.alertsystem.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProducerService {

    private final KafkaTemplate<String, Transaction> kafkaTemplate;

    @Value("${kafka.topics.transactions}")
    private String transactionsTopic;

    /**
     * Send a transaction asynchronously — non-blocking with callback logging.
     */
    public void sendTransaction(Transaction transaction) {
        CompletableFuture<SendResult<String, Transaction>> future =
            kafkaTemplate.send(transactionsTopic, transaction.getAccountId(), transaction);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("❌ Failed to send transaction [{}] for account [{}]: {}",
                    transaction.getTransactionId(), transaction.getAccountId(), ex.getMessage());
            } else {
                log.info("✅ Sent → topic={} partition={} offset={} | account={} amount={} {}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    transaction.getAccountId(),
                    transaction.getAmount(),
                    transaction.getCurrency());
            }
        });
    }

    /**
     * Send a transaction synchronously — blocks until broker acknowledges.
     */
    public SendResult<String, Transaction> sendTransactionSync(Transaction transaction) {
        try {
            SendResult<String, Transaction> result =
                kafkaTemplate.send(transactionsTopic, transaction.getAccountId(), transaction).get();
            log.info("✅ Sync sent → partition={} offset={}",
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
            return result;
        } catch (Exception e) {
            log.error("Sync send failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send transaction", e);
        }
    }
}
