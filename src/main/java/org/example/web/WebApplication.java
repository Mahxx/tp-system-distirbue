package org.example.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

// Désactiver la sécurité par défaut de Spring Security pour utiliser notre propre logique JWT
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class WebApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
