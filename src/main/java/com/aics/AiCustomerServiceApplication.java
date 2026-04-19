package com.aics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan("com.aics")
@EnableAsync
@EnableScheduling
@EnableRetry
public class AiCustomerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiCustomerServiceApplication.class, args);
    }
}
