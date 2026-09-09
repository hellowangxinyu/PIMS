package com.pengyuan.pims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PimsApplication {

    public static void main(String[] args) {
        // v8.10.1：--init-db 空库自举（jar 内置基线 DDL，无需 sqlite3 CLI）
        if (args.length > 0 && "--init-db".equals(args[0])) {
            BaselineInit.initIfNeeded();
        }
        SpringApplication.run(PimsApplication.class, args);
    }
}
