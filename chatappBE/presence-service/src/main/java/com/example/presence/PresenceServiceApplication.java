package com.example.presence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.common","com.example.presence"})
public class PresenceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PresenceServiceApplication.class, args);

	}

}
