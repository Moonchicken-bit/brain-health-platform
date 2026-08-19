package com.brainhealth.search;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.brainhealth.search", "com.brainhealth.common"})
public class SearchServiceApplication {
    public static void main(String[] args) { SpringApplication.run(SearchServiceApplication.class, args); }
}
