package com.example.service5;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class Service5Application {

    public static void main(String[] args) {
        SpringApplication.run(Service5Application.class, args);
    }

}
