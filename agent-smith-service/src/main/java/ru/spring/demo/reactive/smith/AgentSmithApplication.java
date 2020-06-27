package ru.spring.demo.reactive.smith;

import java.util.concurrent.RejectedExecutionHandler;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class AgentSmithApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentSmithApplication.class, args);
    }

    @Bean
    public RejectedExecutionHandler rejectedExecutionHandler() {
        return (r, executor) -> log.info("Miss. Not enough soldiers!");
    }
}

