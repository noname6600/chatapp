package com.example.common.web.cors;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

@Configuration
@ConfigurationProperties("common.web")
public class CorsProperties implements EnvironmentAware {

    private static final String LEGACY_CORS_PREFIX = "common.security.cors";

    private Cors cors;
    private Environment environment;

    public Cors getCors() {
        if (cors == null && environment != null) {
            // Try canonical prefix first, then fall back to legacy
            cors = Binder.get(environment)
                    .bind("common.web.cors", Bindable.of(Cors.class))
                    .orElseGet(() -> Binder.get(environment)
                            .bind(LEGACY_CORS_PREFIX, Bindable.of(Cors.class))
                            .orElse(null));
        }
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public static class Cors {
        @JsonProperty("allowed-origins")
        private List<String> allowedOrigins;

        @JsonProperty("allowed-methods")
        private List<String> allowedMethods;

        @JsonProperty("allowed-headers")
        private List<String> allowedHeaders;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        @Override
        public String toString() {
            return "Cors{" +
                    "allowedOrigins=" + allowedOrigins +
                    ", allowedMethods=" + allowedMethods +
                    ", allowedHeaders=" + allowedHeaders +
                    '}';
        }
    }
}
