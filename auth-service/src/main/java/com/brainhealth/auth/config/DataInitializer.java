package com.brainhealth.auth.config;

import com.brainhealth.auth.entity.User;
import com.brainhealth.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (!userRepository.existsByUsername("admin")) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setRealName("系统管理员");
                admin.setEmail("admin@brainhealth.local");
                admin.setIsActive(true);
                admin.setIsLocked(false);
                userRepository.save(admin);
                System.out.println("=== Admin user created: admin / admin123 ===");
            } else {
                System.out.println("=== Admin user already exists ===");
            }
        };
    }
}
