package com.example.auth.integration.resend;

import com.example.auth.configuration.ResendProperties;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Component
public class ResendEmailClient {

    private static final int MAX_LOG_BODY = 500;

    private final RestTemplate restTemplate;
    private final ResendProperties properties;

    public ResendEmailClient(RestTemplate restTemplate, ResendProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public void sendEmail(String to, String subject, String text) {
        String url = normalizeBaseUrl(properties.getBaseUrl()) + "/emails";
        ResendEmailRequest request = new ResendEmailRequest(
                properties.getFrom(),
                List.of(to),
                subject,
                text
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(request, headers),
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Resend send failed status={} body={}",
                        response.getStatusCodeValue(),
                        truncateBody(response.getBody()));
                throw new BusinessException(
                        CommonErrorCode.INTERNAL_ERROR,
                        "Failed to send email. Please try again later."
                );
            }
        } catch (RestClientException ex) {
            log.error("Resend send failed: {}", ex.getMessage(), ex);
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "Failed to send email. Please try again later."
            );
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private static String truncateBody(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() <= MAX_LOG_BODY) {
            return body;
        }
        return body.substring(0, MAX_LOG_BODY);
    }

    private record ResendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String text
    ) {
    }
}


