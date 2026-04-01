package com.patitoland;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PatitolandApplication {
    public static void main(String[] args) {
        SpringApplication.run(PatitolandApplication.class, args);
    }
}
