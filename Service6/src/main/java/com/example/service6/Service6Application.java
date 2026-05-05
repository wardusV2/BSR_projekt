package com.example.service6;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class Service6Application {

    public static void main(String[] args) {
        SpringApplication.run(Service6Application.class, args);
    }

}
