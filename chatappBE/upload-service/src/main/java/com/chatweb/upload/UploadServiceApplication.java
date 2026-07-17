package com.chatweb.upload;

import com.chatweb.common.web.cors.CorsProperties;
import com.chatweb.common.web.exception.GlobalExceptionHandler;
import com.chatweb.upload.config.UploadPolicyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({CorsProperties.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(UploadPolicyProperties.class)
public class UploadServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadServiceApplication.class, args);
    }
}
