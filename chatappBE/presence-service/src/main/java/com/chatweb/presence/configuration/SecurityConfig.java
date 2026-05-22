package com.chatweb.presence.configuration;

import com.chatweb.common.web.cors.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    protected final CorsProperties securityProperties;

        @Bean
        public InternalServiceIngressAuthFilter internalServiceIngressAuthFilter(
                        @Value("${internal.auth.header:X-Internal-Service-Token}") String headerName,
                                @Value("${internal.auth.token:}") String token,
                                @Value("${spring.profiles.active:}") String activeProfiles
        ) {
                        return new InternalServiceIngressAuthFilter(headerName, token, activeProfiles);
        }

    @Bean
        public SecurityFilterChain filterChain(
                        HttpSecurity http,
                        CorsConfigurationSource corsConfig,
                        InternalServiceIngressAuthFilter internalServiceIngressAuthFilter
        ) throws Exception {

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
                                "/ws/**",
                                "/actuator/health/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(Customizer.withDefaults())
                );

        http.addFilterBefore(internalServiceIngressAuthFilter, UsernamePasswordAuthenticationFilter.class);
        this.cors(http);

        return http.build();
    }

    protected void cors(HttpSecurity http) throws Exception {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", CorsProperties.buildCorsConfiguration(this.securityProperties));
        http.cors(cors -> cors.configurationSource(source));
    }
}

