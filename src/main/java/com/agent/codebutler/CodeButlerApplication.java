package com.agent.codebutler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = BatchAutoConfiguration.class)
@EnableAsync
public class CodeButlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeButlerApplication.class, args);
    }
}
