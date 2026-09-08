package com.dams;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // FEAT-42 nightly owner digest (the only scheduled job; org opt-in)
public class DamsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DamsApplication.class, args);
    }
}
