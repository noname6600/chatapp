package com.chatweb.friendship;

import com.chatweb.common.web.filter.TraceIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;


@SpringBootApplication
@EnableFeignClients
@ComponentScan(basePackages = {"com.chatweb.common","com.chatweb.friendship"})
@Import(TraceIdFilter.class)
public class FriendshipServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FriendshipServiceApplication.class, args);

	}

}
