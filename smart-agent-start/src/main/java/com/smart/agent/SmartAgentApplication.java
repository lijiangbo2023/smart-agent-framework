package com.smart.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Smart Agent framework bootstrap class
 *
 * @description Spring Boot application entry point
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
public class SmartAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartAgentApplication.class, args);
    }
}
