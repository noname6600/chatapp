package com.example.auth.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "resend")
public class ResendProperties {

    @NotBlank
    private String apiKey;

    @NotBlank
    private String from;

    @NotBlank
    private String baseUrl = "https://api.resend.com";
}
