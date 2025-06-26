package com.betfanatics.exchange.order.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final String LOCAL_PROFILE = "local";

    // Replace these with your actual scopes
    protected static final String AUTHORITY_READ = "SCOPE_microservice-kickstarter/inventory:read";
    protected static final String AUTHORITY_WRITE = "SCOPE_microservice-kickstarter/inventory:write";

    // Replace this with your actual secure path(s)
    private static final String SECURE_PATH_1 = "/inventory/v1/**";
    private static final String SECURE_PATH_2 = "/foo/bar/**";

    @Bean
    @Profile(LOCAL_PROFILE)
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
    
    @Bean
    @Profile("!" + LOCAL_PROFILE)
    public SecurityFilterChain securedFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(
                new NegatedRequestMatcher(
                    new OrRequestMatcher(

                        // Include paths that are excluded from auth checks
                        new AntPathRequestMatcher("/actuator/**"),
                        new AntPathRequestMatcher("/swagger-ui/**")
                        )
                )
            )
            .securityMatcher(SECURE_PATH_1)
            .securityMatcher(SECURE_PATH_2)
            .authorizeHttpRequests((authz) ->
                authz
                    .requestMatchers(HttpMethod.GET).hasAuthority(AUTHORITY_READ)
                    .requestMatchers(HttpMethod.POST).hasAuthority(AUTHORITY_WRITE)
                    .anyRequest()
                    .denyAll()
            )
            .csrf(AbstractHttpConfigurer::disable)
            .oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(
                exceptionHandling ->
                    exceptionHandling.accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                            log.info("Access Denied: {}", request.getRequestURI());
                            log.info("Reason: {}", accessDeniedException.getMessage());
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("Access Denied");
                        }));

        return http.build();
    }

}