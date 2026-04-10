package com.example.friendship;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;


@SpringBootApplication
@EnableFeignClients
@ComponentScan(basePackages = {"com.example.common","com.example.friendship"})
public class FriendshipServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FriendshipServiceApplication.class, args);

	}

}
