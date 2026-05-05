package com.example.service7;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class Service7Application {

    public static void main(String[] args) {
        SpringApplication.run(Service7Application.class, args);
    }

}
