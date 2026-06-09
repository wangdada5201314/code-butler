package com.agent.codebutler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;

@SpringBootApplication(exclude = BatchAutoConfiguration.class)
public class CodeButlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeButlerApplication.class, args);
    }
}
