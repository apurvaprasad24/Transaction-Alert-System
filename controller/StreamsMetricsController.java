package com.alertsystem.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/streams")
@RequiredArgsConstructor
public class StreamsMetricsController {

    private final StreamsBuilderFactoryBean streamsFactory;

    /**
     * GET /api/streams/status
     * Returns the Kafka Streams application state.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStreamsStatus() {
        KafkaStreams streams = streamsFactory.getKafkaStreams();
        String state = streams != null ? streams.state().toString() : "NOT_STARTED";
        return ResponseEntity.ok(Map.of(
            "state", state,
            "description", describeState(state)
        ));
    }

    /**
     * GET /api/streams/alert-counts
     * Reads from the materialized alert-count-store (running totals by severity).
     */
    @GetMapping("/alert-counts")
    public ResponseEntity<Map<String, Object>> getAlertCounts() {
        KafkaStreams streams = streamsFactory.getKafkaStreams();
        Map<String, Object> result = new HashMap<>();

        if (streams == null || !streams.state().isRunningOrRebalancing()) {
            result.put("status", "Streams not running");
            return ResponseEntity.ok(result);
        }

        try {
            ReadOnlyKeyValueStore<String, Long> store = streams.store(
                StoreQueryParameters.fromNameAndType("alert-count-store",
                    QueryableStoreTypes.keyValueStore())
            );

            Map<String, Long> counts = new HashMap<>();
            store.all().forEachRemaining(kv -> counts.put(kv.key, kv.value));
            result.put("alertsBySeverity", counts);
            result.put("status", "OK");
        } catch (Exception e) {
            log.error("Error querying alert-count-store: {}", e.getMessage());
            result.put("status", "Store unavailable — " + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    private String describeState(String state) {
        return switch (state) {
            case "RUNNING"     -> "Streams running normally";
            case "REBALANCING" -> "Consumer group rebalancing";
            case "CREATED"     -> "Initialized, not yet started";
            case "PENDING_SHUTDOWN" -> "Shutting down";
            case "NOT_RUNNING" -> "Stopped";
            case "ERROR"       -> "Error — check logs";
            default            -> "Unknown state";
        };
    }
}
