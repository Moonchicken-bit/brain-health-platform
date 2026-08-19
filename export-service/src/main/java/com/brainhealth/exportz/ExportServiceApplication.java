package com.brainhealth.exportz;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.brainhealth.exportz", "com.brainhealth.common"})
public class ExportServiceApplication {
    public static void main(String[] args) { SpringApplication.run(ExportServiceApplication.class, args); }
}
