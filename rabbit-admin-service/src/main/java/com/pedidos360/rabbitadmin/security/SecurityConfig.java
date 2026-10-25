package com.pedidos360.rabbitadmin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Seguridad del microservicio administrador. Igual que el backend de pedidos, actua como
 * OAuth2 Resource Server: valida firma, issuer, audience y vigencia del JWT de Entra ID.
 *
 * Autorizacion:
 *   GET    /api/**   -> cualquier usuario autenticado (consulta)
 *   POST/DELETE      -> rol "Admin" del token (modifica el broker)
 *   /actuator/health -> publico (health check)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${pedidos360.security.audience}")
    private String expectedAudience;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    @Profile("!local")
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator(expectedAudience)));
        return decoder;
    }

    /** Claim "roles" de Entra ID (["Admin"]) -> authority ROLE_ADMIN. */
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (jwt.getClaims().get("roles") instanceof List<?> roles) {
                for (Object role : roles) {
                    String r = String.valueOf(role).trim();
                    if (!r.isEmpty()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()));
                    }
                }
            }
            String principal = jwt.getClaimAsString("preferred_username") != null
                    ? jwt.getClaimAsString("preferred_username") : jwt.getSubject();
            return new JwtAuthenticationToken(jwt, authorities, principal);
        };
    }

    /** 401 en JSON cuando falta el token o es invalido/expirado. */
    @Bean
    AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.AuthenticationException ex) ->
                writeJson(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                        "Token JWT ausente, invalido o expirado", request.getRequestURI());
    }

    /** 403 en JSON cuando el token es valido pero falta el rol Admin. */
    @Bean
    AccessDeniedHandler jsonAccessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response, org.springframework.security.access.AccessDeniedException ex) ->
                writeJson(response, HttpStatus.FORBIDDEN, "Forbidden",
                        "Se requiere el rol Admin para modificar la configuracion de RabbitMQ", request.getRequestURI());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${pedidos360.security.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static void writeJson(HttpServletResponse response, HttpStatus status, String error,
                                  String message, String path) throws java.io.IOException {
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        String safePath = path.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status.value()
                + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\",\"path\":\"" + safePath + "\"}");
    }
}
