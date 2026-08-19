package com.brainhealth.lab;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.brainhealth.lab", "com.brainhealth.common"})
public class LabServiceApplication {
    public static void main(String[] args) { SpringApplication.run(LabServiceApplication.class, args); }
}
