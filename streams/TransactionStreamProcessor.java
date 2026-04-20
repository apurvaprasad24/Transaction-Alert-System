package com.alertsystem.streams;

import com.alertsystem.model.Alert;
import com.alertsystem.model.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.streams.StreamsConfig.*;

/**
 * Kafka Streams layer — real-time aggregation on top of raw events.
 *
 * Pipelines:
 *  1. Transaction volume per account (tumbling 1-min window)
 *  2. Alert severity counts (continuous running total)
 *  3. High-value transaction filter → dedicated topic
 */
@Slf4j
@Configuration
@EnableKafkaStreams
public class TransactionStreamProcessor {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.topics.transactions}")
    private String transactionsTopic;

    @Value("${kafka.topics.alerts}")
    private String alertsTopic;

    private static final String HIGH_VALUE_TOPIC = "high-value-transactions";
    private static final String VOLUME_STORE     = "transaction-volume-store";
    private static final String ALERT_COUNT_STORE = "alert-count-store";

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    // ── Streams Application Config ────────────────────────────────────────
    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration streamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(APPLICATION_ID_CONFIG, "transaction-alert-streams");
        props.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(PROCESSING_GUARANTEE_CONFIG, EXACTLY_ONCE_V2);
        props.put(COMMIT_INTERVAL_MS_CONFIG, 1000);
        props.put(STATE_DIR_CONFIG, "/tmp/kafka-streams");
        return new KafkaStreamsConfiguration(props);
    }

    // ── Pipeline 1: Per-account transaction count in 1-minute windows ─────
    @Bean
    public KStream<String, String> transactionVolumeStream(StreamsBuilder builder) {
        KStream<String, String> txStream = builder.stream(transactionsTopic);

        txStream
            .groupByKey()
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
            .count(Materialized.as(VOLUME_STORE))
            .toStream()
            .peek((windowedKey, count) ->
                log.info("📊 [Volume] account={} window={} count={}",
                    windowedKey.key(),
                    windowedKey.window().startTime(),
                    count))
            .filter((k, count) -> count > 5)
            .peek((k, count) ->
                log.warn("⚡ [Streams] High-frequency detected: account={} count={} in 1 min",
                    k.key(), count));

        return txStream;
    }

    // ── Pipeline 2: Filter high-value transactions → dedicated topic ───────
    @Bean
    public KStream<String, String> highValueStream(StreamsBuilder builder) {
        KStream<String, String> txStream = builder.stream(transactionsTopic);

        txStream
            .filter((accountId, txJson) -> {
                try {
                    Transaction tx = mapper.readValue(txJson, Transaction.class);
                    return tx.getAmount() >= 10_000;
                } catch (Exception e) {
                    log.error("Stream filter parse error: {}", e.getMessage());
                    return false;
                }
            })
            .peek((k, v) -> log.info("💰 [Streams] High-value transaction routed → {}", HIGH_VALUE_TOPIC))
            .to(HIGH_VALUE_TOPIC);

        return txStream;
    }

    // ── Pipeline 3: Alert severity aggregation (running total) ────────────
    @Bean
    public KStream<String, String> alertSeverityStream(StreamsBuilder builder) {
        KStream<String, String> alertStream = builder.stream(alertsTopic);

        // Count per severity (keyed by severity name)
        alertStream
            .selectKey((accountId, alertJson) -> {
                try {
                    Alert alert = mapper.readValue(alertJson, Alert.class);
                    return alert.getSeverity().name();
                } catch (Exception e) {
                    return "UNKNOWN";
                }
            })
            .groupByKey()
            .count(Materialized.as(ALERT_COUNT_STORE))
            .toStream()
            .peek((severity, count) ->
                log.info("📈 [Streams] Alert tally — severity={} total={}", severity, count));

        return alertStream;
    }
}
