package com.brainhealth.adni;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.brainhealth.adni", "com.brainhealth.common"})
public class AdniServiceApplication {
    public static void main(String[] args) { SpringApplication.run(AdniServiceApplication.class, args); }
}
