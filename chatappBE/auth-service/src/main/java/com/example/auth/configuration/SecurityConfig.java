package com.example.auth.configuration;

import com.example.common.web.cors.CorsProperties;
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
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
    private final GoogleOAuthAuthenticationSuccessHandler googleOAuthAuthenticationSuccessHandler;
    private final GoogleOAuthAuthenticationFailureHandler googleOAuthAuthenticationFailureHandler;


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfig) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                    "/oauth2/authorization/**",
                    "/login/oauth2/code/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/.well-known/jwks.json",
                                "/actuator/health/**"
                        ).permitAll()

                        .anyRequest().authenticated()
                )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(googleOAuthAuthenticationSuccessHandler)
                .failureHandler(googleOAuthAuthenticationFailureHandler)
            )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );
        this.cors(http);

        return http.build();
    }

    protected void cors(HttpSecurity http) throws Exception {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", CorsProperties.buildCorsConfiguration(this.securityProperties));
        http.cors(cors -> cors.configurationSource(source));
    }
}

