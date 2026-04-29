package com.moneymate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = {"com.moneymate.user", "com.moneymate.sync"})
@EnableMongoRepositories(basePackages = "com.moneymate.sms")
public class MoneymateApplication {
    public static void main(String[] args) {
        SpringApplication.run(MoneymateApplication.class, args);
    }
}
