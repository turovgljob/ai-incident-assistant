package com.example.incidents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IncidentAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentAssistantApplication.class, args);
    }
}
