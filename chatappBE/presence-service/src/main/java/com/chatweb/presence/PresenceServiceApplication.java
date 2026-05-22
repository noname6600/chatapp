package com.chatweb.presence;

import com.chatweb.common.web.filter.TraceIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ComponentScan(basePackages = {"com.chatweb.common","com.chatweb.presence"})
@Import(TraceIdFilter.class)
public class PresenceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PresenceServiceApplication.class, args);

	}

}
