package com.pengyuan.pims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PimsApplication {

    public static void main(String[] args) {
        // v12.0：PG 为唯一数据库，空库由 SchemaInitializer 启动时自动建表，--init-db 自举已下线
        SpringApplication.run(PimsApplication.class, args);
    }
}
