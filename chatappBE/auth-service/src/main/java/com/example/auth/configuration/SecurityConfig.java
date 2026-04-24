package com.example.auth.configuration;

import com.example.common.web.security.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    protected final CorsProperties securityProperties;


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfig) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/.well-known/jwks.json",
                                "/actuator/health/**"
                        ).permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );
        this.cors(http);

        return http.build();
    }

    protected void cors(HttpSecurity http) throws Exception {
        CorsConfiguration configuration = buildCorsConfiguration();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        http.cors(cors -> cors.configurationSource(source));
    }

    protected CorsConfiguration buildCorsConfiguration() {
        CorsProperties.Cors cors = this.securityProperties.getCors();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        if (Objects.nonNull(cors)) {
            if (Objects.nonNull(cors.getAllowedOrigins())) {
                List<String> origins = cors.getAllowedOrigins().stream()
                        .filter(Objects::nonNull)
                        .flatMap(allowedOrigin -> Arrays.stream(allowedOrigin.split("\\s*,\\s*")))
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toList();
                configuration.setAllowedOrigins(origins);
            }

            if (Objects.nonNull(cors.getAllowedMethods())) {
                configuration.setAllowedMethods(cors.getAllowedMethods());
            }

            if (Objects.nonNull(cors.getAllowedHeaders())) {
                configuration.setAllowedHeaders(cors.getAllowedHeaders());
            }
        }
        return configuration;
    }
}

