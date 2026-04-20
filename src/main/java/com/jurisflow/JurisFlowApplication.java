package com.jurisflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class JurisFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(JurisFlowApplication.class, args);
    }
}
