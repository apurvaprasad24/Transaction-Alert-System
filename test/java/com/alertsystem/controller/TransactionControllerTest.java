package com.alertsystem.controller;

import com.alertsystem.model.Transaction;
import com.alertsystem.model.Transaction.TransactionType;
import com.alertsystem.producer.TransactionProducerService;
import com.alertsystem.service.AlertPublisherService;
import com.alertsystem.service.TransactionSimulatorService;
import com.alertsystem.consumer.TransactionConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@DisplayName("TransactionController REST API")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionProducerService producerService;

    @MockBean
    private TransactionSimulatorService simulatorService;

    @MockBean
    private AlertPublisherService alertPublisherService;

    @MockBean
    private TransactionConsumer transactionConsumer;

    // ── POST /api/transactions ─────────────────────────────────────────────
    @Test
    @DisplayName("POST /api/transactions returns 202 Accepted")
    void postTransactionReturnsAccepted() throws Exception {
        Transaction tx = Transaction.create("ACC-001", 500, "USD",
            TransactionType.DEBIT, "Amazon", "New York, US");

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tx)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("accepted"))
            .andExpect(jsonPath("$.transactionId").isNotEmpty());

        verify(producerService, times(1)).sendTransaction(any(Transaction.class));
    }

    @Test
    @DisplayName("POST /api/transactions returns 400 for missing accountId")
    void postTransactionValidationFails() throws Exception {
        Transaction tx = new Transaction(); // missing accountId, amount etc.

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tx)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isNotEmpty());

        verify(producerService, never()).sendTransaction(any());
    }

    // ── POST /api/simulate ─────────────────────────────────────────────────
    @Test
    @DisplayName("POST /api/simulate returns 202 Accepted")
    void postSimulateReturnsAccepted() throws Exception {
        mockMvc.perform(post("/api/simulate"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("started"));
    }

    // ── GET /api/stats ─────────────────────────────────────────────────────
    @Test
    @DisplayName("GET /api/stats returns stats object")
    void getStatsReturnsStats() throws Exception {
        when(transactionConsumer.getProcessedCount()).thenReturn(42L);
        when(transactionConsumer.getAlertCount()).thenReturn(7L);
        when(alertPublisherService.getStatistics()).thenReturn(
            Map.of("total", 7L, "CRITICAL", 2L, "HIGH", 5L)
        );

        mockMvc.perform(get("/api/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consumer.processedTransactions").value(42))
            .andExpect(jsonPath("$.consumer.alertsGenerated").value(7))
            .andExpect(jsonPath("$.alerts.total").value(7));
    }

    // ── GET /api/health ────────────────────────────────────────────────────
    @Test
    @DisplayName("GET /api/health returns UP")
    void getHealthReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
