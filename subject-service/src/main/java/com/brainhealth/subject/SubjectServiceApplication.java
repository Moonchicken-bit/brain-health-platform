package com.brainhealth.subject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.brainhealth")
public class SubjectServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubjectServiceApplication.class, args);
    }
}
