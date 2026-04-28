package com.example.gateway.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamReadinessIndicatorTest {

    @Test
    void health_reportsDown_whenAnyRequiredServiceIsUnhealthy() {
        GatewayReadinessProperties props = new GatewayReadinessProperties();
        props.setRequiredServices(List.of("http://auth", "http://user"));
        props.setTimeout(Duration.ofSeconds(1));

        ExchangeFunction exchangeFunction = request -> {
            if (request.url().toString().contains("auth")) {
                return Mono.just(ClientResponse.create(HttpStatus.OK).build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
        };

        DownstreamReadinessIndicator indicator = new DownstreamReadinessIndicator(
                WebClient.builder().exchangeFunction(exchangeFunction),
                props
        );

        Health health = indicator.health().block();

        assertThat(health).isNotNull();
        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).containsKey("downstream");
    }

    @Test
    void health_reportsUp_whenAllRequiredServicesAreHealthy() {
        GatewayReadinessProperties props = new GatewayReadinessProperties();
        props.setRequiredServices(List.of("http://auth", "http://user"));
        props.setTimeout(Duration.ofSeconds(1));

        ExchangeFunction exchangeFunction = request ->
            Mono.just(ClientResponse.create(HttpStatus.OK).build());

        DownstreamReadinessIndicator indicator = new DownstreamReadinessIndicator(
                WebClient.builder().exchangeFunction(exchangeFunction),
                props
        );

        Health health = indicator.health().block();

        assertThat(health).isNotNull();
        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsKey("downstream");
    }
}
