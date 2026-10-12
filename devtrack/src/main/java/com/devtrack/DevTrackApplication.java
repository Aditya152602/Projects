package com.devtrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication // enables component scan + auto-config for everything under com.devtrack
public class DevTrackApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevTrackApplication.class, args); // boots Tomcat + Spring context
    }
}
