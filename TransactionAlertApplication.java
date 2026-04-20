package com.alertsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableKafka
@EnableScheduling
public class TransactionAlertApplication {

    public static void main(String[] args) {
        printBanner();
        SpringApplication.run(TransactionAlertApplication.class, args);
    }

    private static void printBanner() {
        System.out.println("""
            ╔══════════════════════════════════════════════════════════╗
            ║       TRANSACTION ALERT SYSTEM  v2.0                     ║
            ║       Spring Boot  +  Apache Kafka                       ║
            ║       Real-time Financial Anomaly Detection              ║
            ╚══════════════════════════════════════════════════════════╝
            """);
    }
}
