package com.brainhealth.scale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.brainhealth")
public class ScaleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScaleServiceApplication.class, args);
    }
}
