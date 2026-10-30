package com.proxyai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiQuotaApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiQuotaApplication.class, args);
    }
}
