package com.example.common.web.security;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties("common.security")
public class CorsProperties {

    @JsonProperty("cors")
    private Cors cors;

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
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
