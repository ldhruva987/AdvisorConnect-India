package com.advisorconnect.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} drives {@code BookingCompletionScheduler}'s CONFIRMED→COMPLETED sweep. */
@SpringBootApplication
@EnableScheduling
public class BookingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
