package io.github.ikemoon.lifeservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("io.github.ikemoon.lifeservice.**.mapper")
public class LifeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeServiceApplication.class, args);
    }
}
