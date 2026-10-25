package com.flowboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FlowboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowboardApplication.class, args);
    }
}
