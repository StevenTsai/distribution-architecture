package com.godzilla.distribution;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.godzilla.distribution.mapper")
@EnableScheduling
public class DistributionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributionApplication.class, args);
    }
}
