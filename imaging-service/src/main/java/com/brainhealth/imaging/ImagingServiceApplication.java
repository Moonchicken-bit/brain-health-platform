package com.brainhealth.imaging;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.brainhealth.imaging", "com.brainhealth.common"})
public class ImagingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImagingServiceApplication.class, args);
    }
}
