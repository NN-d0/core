package com.radio.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
2026/3/13
*/
@MapperScan("com.radio.core.mapper")
@EnableScheduling
@SpringBootApplication
public class CoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
        System.out.println("CoreApplication started successfully!");
    }
}
