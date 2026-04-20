package com.alertsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alert.thresholds")
public class AlertThresholdProperties {

    private double largeTransaction = 10_000.0;
    private double criticalTransaction = 50_000.0;
    private int maxTransactionsPerMinute = 5;
    private double roundAmountMinimum = 1_000.0;
    private int failureCount = 3;
}
