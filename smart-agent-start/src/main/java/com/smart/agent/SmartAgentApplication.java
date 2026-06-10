package com.smart.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Smart Agent 框架启动类
 *
 * @description Spring Boot 应用入口，启动智能体服务
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@SpringBootApplication
@EnableScheduling
public class SmartAgentApplication {

    /**
     * 应用启动入口
     *
     * @description 启动 Spring Boot 应用
     * @param args 命令行参数
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static void main(String[] args) {
        SpringApplication.run(SmartAgentApplication.class, args);
    }
}
