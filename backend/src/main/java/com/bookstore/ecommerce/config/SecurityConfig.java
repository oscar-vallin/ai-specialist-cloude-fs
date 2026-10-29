package com.bookstore.ecommerce.config;

import com.bookstore.ecommerce.common.ApiProblem;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.common.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Stateless JWT security. The role of every operation is the {@code x-required-role} of {@code api/openapi.yaml}.
 * Failures are written as RFC 7807 problems: 401 {@code UNAUTHENTICATED}, 403 {@code FORBIDDEN}.
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (roles != null) {
                roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
            JwtAuthenticationConverter converter) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, ex) -> write(mapper, response,
                ApiProblem.of(ErrorCode.UNAUTHENTICATED, "Authentication is required or the token is invalid.",
                        request.getRequestURI(), RequestIdFilter.current()));
        AccessDeniedHandler forbidden = (request, response, ex) -> write(mapper, response,
                ApiProblem.of(ErrorCode.FORBIDDEN, "You are not allowed to perform this operation.",
                        request.getRequestURI(), RequestIdFilter.current()));

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .oauth2ResourceServer(o -> o
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(j -> j.jwtAuthenticationConverter(converter)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/logout").hasAnyRole("CUSTOMER", "ADMIN")
                        // Catalog and shipping estimates are public (the catalog filters by entitlement itself).
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/recommended").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/brands", "/api/v1/products",
                                "/api/v1/products/*", "/api/v1/products/*/related",
                                "/api/v1/shipping/rates", "/api/v1/shipping/estimate").permitAll()
                        // Carts may be held by guests; the cart service decides who owns which cart.
                        .requestMatchers("/api/v1/cart", "/api/v1/cart/**").permitAll()
                        .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").hasAnyRole("CUSTOMER", "ADMIN")
                        // Orders: CUSTOMER only (guests and admins do not place orders).
                        .requestMatchers("/api/v1/orders", "/api/v1/orders/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/*/refund").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/*").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/shipments/*").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/shipments/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/shipments/*/return").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .build();
    }

    private static void write(ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse response, ApiProblem problem)
            throws java.io.IOException {
        response.setStatus(problem.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
