package com.chatweb.friendship.configuration;

import com.chatweb.common.web.cors.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
        public InternalServiceAuthFilter internalServiceAuthFilter(
                        @Value("${internal.auth.header:X-Internal-Service-Token}") String headerName,
                                @Value("${internal.auth.token:}") String token,
                                @Value("${spring.profiles.active:}") String activeProfiles
        ) {
                        return new InternalServiceAuthFilter(headerName, token, activeProfiles);
        }

    @Bean
        public SecurityFilterChain filterChain(
                        HttpSecurity http,
                        CorsConfigurationSource corsConfig,
                        InternalServiceAuthFilter internalServiceAuthFilter
        ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                        "/ws/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/.well-known/jwks.json",
                                "/actuator/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/internal/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(Customizer.withDefaults())
                );
        http.addFilterBefore(internalServiceAuthFilter, UsernamePasswordAuthenticationFilter.class);
        this.cors(http);

        return http.build();
    }

    protected void cors(HttpSecurity http) throws Exception {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", CorsProperties.buildCorsConfiguration(this.securityProperties));
        http.cors(cors -> cors.configurationSource(source));
    }
}


