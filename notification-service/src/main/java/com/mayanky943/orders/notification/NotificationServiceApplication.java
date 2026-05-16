package com.mayanky943.orders.notification;

import com.mayanky943.orders.common.config.CommonAutoConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@Import(CommonAutoConfig.class)
@EnableJpaRepositories(basePackages = {
        "com.mayanky943.orders.notification",
        "com.mayanky943.orders.common.outbox"
})
@EntityScan(basePackages = {
        "com.mayanky943.orders.notification",
        "com.mayanky943.orders.common.outbox"
})
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
