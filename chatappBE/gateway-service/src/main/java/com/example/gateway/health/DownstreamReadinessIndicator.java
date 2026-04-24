package com.example.gateway.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("downstreamRoutes")
public class DownstreamReadinessIndicator implements ReactiveHealthIndicator {

    private final WebClient webClient;
    private final GatewayReadinessProperties properties;

    public DownstreamReadinessIndicator(WebClient.Builder webClientBuilder,
                                        GatewayReadinessProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public Mono<Health> health() {
        if (!properties.isEnabled()) {
            return Mono.just(Health.up().withDetail("disabled", true).build());
        }

        List<String> required = properties.getRequiredServices();
        if (required == null || required.isEmpty()) {
            return Mono.just(Health.up().withDetail("requiredServices", 0).build());
        }

        Duration timeout = properties.getTimeout();
        String healthPath = properties.getHealthPath();

        return Flux.fromIterable(required)
                .flatMap(service -> probe(service, healthPath, timeout))
                .collectMap(ProbeResult::service, ProbeResult::up, LinkedHashMap::new)
                .map(results -> {
                    boolean allUp = results.values().stream().allMatch(Boolean::booleanValue);
                    Health.Builder builder = allUp ? Health.up() : Health.down();
                    builder.withDetail("downstream", results);
                    return builder.build();
                });
    }

    private Mono<ProbeResult> probe(String service, String healthPath, Duration timeout) {
        if (!StringUtils.hasText(service)) {
            return Mono.just(new ProbeResult("<blank>", false));
        }

        URI uri = buildHealthUri(service.trim(), healthPath);

        return webClient.get()
                .uri(uri)
                .exchangeToMono(this::isOk)
                .timeout(timeout)
                .onErrorReturn(false)
                .map(up -> new ProbeResult(service, up));
    }

    private Mono<Boolean> isOk(ClientResponse response) {
        int rawStatus = response.statusCode().value();
        boolean ok = rawStatus >= 200 && rawStatus < 500;
        return response.releaseBody().thenReturn(ok);
    }

    private URI buildHealthUri(String base, String healthPath) {
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (StringUtils.hasText(healthPath)) {
            String path = healthPath.startsWith("/") ? healthPath : "/" + healthPath;
            if (!normalized.endsWith(path)) {
                normalized = normalized + path;
            }
        }
        return URI.create(normalized);
    }

    private record ProbeResult(String service, boolean up) {}
}
