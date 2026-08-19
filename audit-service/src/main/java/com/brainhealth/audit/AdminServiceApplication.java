package com.brainhealth.audit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.brainhealth.audit", "com.brainhealth.common"})
public class AdminServiceApplication {
    public static void main(String[] args) { SpringApplication.run(AdminServiceApplication.class, args); }
}
