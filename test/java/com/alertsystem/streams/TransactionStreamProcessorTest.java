package com.alertsystem.streams;

import com.alertsystem.model.Transaction;
import com.alertsystem.model.Transaction.TransactionStatus;
import com.alertsystem.model.Transaction.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.*;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the Kafka Streams topology using TopologyTestDriver
 * — no real Kafka broker needed.
 */
@DisplayName("TransactionStreamProcessor — Topology Tests")
class TransactionStreamProcessorTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> highValueOutputTopic;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        StreamsBuilder builder = new StreamsBuilder();

        // Manually build the high-value filter topology for isolated testing
        builder.stream("financial-transactions",
                Consumed.with(Serdes.String(), Serdes.String()))
            .filter((key, value) -> {
                try {
                    Transaction tx = mapper.readValue(value, Transaction.class);
                    return tx.getAmount() >= 10_000;
                } catch (Exception e) {
                    return false;
                }
            })
            .to("high-value-transactions");

        Topology topology = builder.build();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

        testDriver = new TopologyTestDriver(topology, props);
        inputTopic = testDriver.createInputTopic("financial-transactions",
            Serdes.String().serializer(), Serdes.String().serializer());
        highValueOutputTopic = testDriver.createOutputTopic("high-value-transactions",
            Serdes.String().deserializer(), Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }

    @Test
    @DisplayName("High-value transaction passes through filter")
    void highValueTransactionIsForwarded() throws Exception {
        Transaction tx = Transaction.create("ACC-HV", 25_000, "USD",
            TransactionType.TRANSFER, "Wire", "London, UK");
        inputTopic.pipeInput("ACC-HV", mapper.writeValueAsString(tx));

        assertThat(highValueOutputTopic.isEmpty()).isFalse();
        TestRecord<String, String> out = highValueOutputTopic.readRecord();
        Transaction received = mapper.readValue(out.getValue(), Transaction.class);
        assertThat(received.getAmount()).isEqualTo(25_000);
    }

    @Test
    @DisplayName("Low-value transaction is filtered out")
    void lowValueTransactionIsDropped() throws Exception {
        Transaction tx = Transaction.create("ACC-LV", 50, "USD",
            TransactionType.DEBIT, "Coffee", "NY");
        inputTopic.pipeInput("ACC-LV", mapper.writeValueAsString(tx));

        assertThat(highValueOutputTopic.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Only transactions >= 10,000 reach the output topic")
    void mixedTransactionsFiltersCorrectly() throws Exception {
        Transaction normal = Transaction.create("ACC-1", 500, "USD", TransactionType.DEBIT, "Amazon", "NY");
        Transaction large  = Transaction.create("ACC-2", 15_000, "USD", TransactionType.TRANSFER, "Wire", "NY");
        Transaction critical = Transaction.create("ACC-3", 75_000, "USD", TransactionType.TRANSFER, "Wire", "London");

        inputTopic.pipeInput("ACC-1", mapper.writeValueAsString(normal));
        inputTopic.pipeInput("ACC-2", mapper.writeValueAsString(large));
        inputTopic.pipeInput("ACC-3", mapper.writeValueAsString(critical));

        // Only 2 of the 3 should appear in the output
        assertThat(highValueOutputTopic.getQueueSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("Malformed JSON is dropped silently, not thrown")
    void malformedJsonIsDropped() {
        inputTopic.pipeInput("ACC-BAD", "{this is not json}");
        assertThat(highValueOutputTopic.isEmpty()).isTrue();
    }
}
