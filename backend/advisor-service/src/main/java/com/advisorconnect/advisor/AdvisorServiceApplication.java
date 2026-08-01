package com.advisorconnect.advisor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class AdvisorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdvisorServiceApplication.class, args);
    }
}
