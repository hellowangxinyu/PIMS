package com.pengyuan.pims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PimsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PimsApplication.class, args);
    }
}
