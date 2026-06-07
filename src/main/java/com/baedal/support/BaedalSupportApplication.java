package com.baedal.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BaedalSupportApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaedalSupportApplication.class, args);
    }
}
