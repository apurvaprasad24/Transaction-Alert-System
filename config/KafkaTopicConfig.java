package com.alertsystem.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;

@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topics.transactions}")
    private String transactionsTopic;

    @Value("${kafka.topics.alerts}")
    private String alertsTopic;

    @Value("${kafka.topics.audit}")
    private String auditTopic;

    // Spring auto-creates these topics on startup if they don't exist
    @Bean
    public NewTopic transactionsTopic() {
        return TopicBuilder.name(transactionsTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic alertsTopic() {
        return TopicBuilder.name(alertsTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic auditTopic() {
        return TopicBuilder.name(auditTopic)
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public RecordMessageConverter messageConverter() {
        return new JsonMessageConverter();
    }
}
