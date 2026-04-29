package com.example.auth.integration.resend;

import com.example.auth.configuration.ResendProperties;
import com.example.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailClientTest {

    @Test
    void sendEmail_success() {
        ResendProperties properties = new ResendProperties();
        properties.setApiKey("test-key");
        properties.setFrom("no-reply@test.local");
        properties.setBaseUrl("http://resend.test");

        RestTemplate restTemplate = new RestTemplateBuilder().build();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        ResendEmailClient client = new ResendEmailClient(restTemplate, properties);

        server.expect(requestTo("http://resend.test/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"id\":\"email_123\"}", MediaType.APPLICATION_JSON));

        client.sendEmail("user@test.local", "Subject", "Body");

        server.verify();
    }

    @Test
    void sendEmail_errorResponseThrows() {
        ResendProperties properties = new ResendProperties();
        properties.setApiKey("test-key");
        properties.setFrom("no-reply@test.local");
        properties.setBaseUrl("http://resend.test");

        RestTemplate restTemplate = new RestTemplateBuilder().build();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        ResendEmailClient client = new ResendEmailClient(restTemplate, properties);

        server.expect(requestTo("http://resend.test/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"bad request\"}"));

        assertThrows(BusinessException.class,
                () -> client.sendEmail("user@test.local", "Subject", "Body"));

        server.verify();
    }
}

