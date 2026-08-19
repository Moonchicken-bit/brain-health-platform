package com.brainhealth.genetics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.brainhealth.genetics", "com.brainhealth.common"})
public class GeneticsServiceApplication {
    public static void main(String[] args) { SpringApplication.run(GeneticsServiceApplication.class, args); }
}
