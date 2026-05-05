package com.example.service4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class Service4Application {

    public static void main(String[] args) {
        SpringApplication.run(Service4Application.class, args);
    }

}
