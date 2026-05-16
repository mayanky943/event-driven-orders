package com.mayanky943.orders.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mayanky943.orders.common.idempotency.IdempotencyService;
import com.mayanky943.orders.common.outbox.OutboxPublisher;
import com.mayanky943.orders.common.outbox.OutboxService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.mayanky943.orders.common.outbox")
@ComponentScan(basePackageClasses = {
        IdempotencyService.class,
        OutboxService.class,
        OutboxPublisher.class
})
public class CommonAutoConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
