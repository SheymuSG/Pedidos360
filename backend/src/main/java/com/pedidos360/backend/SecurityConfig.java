package com.pedidos360.backend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Configuracion de seguridad de Pedidos360.
 * El backend actua como OAuth2 Resource Server: valida el JWT emitido por
 * Microsoft Entra ID (firma, issuer, audience y vigencia) antes de permitir
 * el acceso a los endpoints /api/**.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /**
     * Audience esperado por la API, ej: api://<client-id-de-Pedidos360-API>.
     * Se define en application.properties o en la variable de entorno AZURE_API_AUDIENCE.
     */
    @Value("${pedidos360.security.audience}")
    private String expectedAudience;

    /** Escribe un JSON simple sin depender de una libreria externa. */
    private static String toJson(Map<String, Object> body) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            String value = String.valueOf(entry.getValue())
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
            sb.append('"').append(entry.getKey()).append("\":\"").append(value).append('"');
        }
        return sb.append('}').toString();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                .accessDeniedHandler(jsonAccessDeniedHandler())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Todo lo demas exige un JWT valido emitido por el tenant configurado
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    /**
     * Decodificador que ademas de firma y vigencia (por defecto) valida el issuer
     * (el tenant de Entra ID) y el audience (esta API), rechazando cualquier otro token.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(expectedAudience);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    /**
     * Traduce el claim "roles" de Azure AD (ej: ["Admin"]) en authorities de
     * Spring Security (ROLE_ADMIN), para poder usar hasRole()/@PreAuthorize.
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            List<GrantedAuthority> authorities = new java.util.ArrayList<>();
            Object rolesClaim = jwt.getClaims().get("roles");
            if (rolesClaim instanceof List<?> roles) {
                for (Object role : roles) {
                    String r = String.valueOf(role).trim();
                    if (!r.isEmpty()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()));
                    }
                }
            }
            String principal = jwt.getClaimAsString("preferred_username") != null
                ? jwt.getClaimAsString("preferred_username")
                : jwt.getSubject();
            return new JwtAuthenticationToken(jwt, authorities, principal);
        };
    }

    /** Responde 401 en JSON cuando falta el token o es invalido/expirado. */
    @Bean
    public AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.AuthenticationException authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", 401,
                "error", "Unauthorized",
                "message", "Token JWT ausente, invalido o expirado",
                "path", request.getRequestURI()
            );
            response.getWriter().write(toJson(body));
        };
    }

    /** Responde 403 en JSON cuando el token es valido pero falta el rol/scope requerido. */
    @Bean
    public AccessDeniedHandler jsonAccessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response, org.springframework.security.access.AccessDeniedException accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", 403,
                "error", "Forbidden",
                "message", "El usuario autenticado no tiene el rol requerido para este recurso",
                "path", request.getRequestURI()
            );
            response.getWriter().write(toJson(body));
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
        @Value("${pedidos360.security.allowed-origins}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** Verifica que el claim "aud" del token corresponda a esta API. */
    static class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String expectedAudience;

        AudienceValidator(String expectedAudience) {
            this.expectedAudience = expectedAudience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            if (expectedAudience == null || expectedAudience.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }
            // Entra ID a veces emite el aud como el Client ID "pelado" (sin api://)
            // aunque el Application ID URI configurado sea api://<client-id>.
            // Se acepta cualquiera de las dos formas.
            String bareId = expectedAudience.startsWith("api://")
                ? expectedAudience.substring("api://".length())
                : expectedAudience;
            String prefixedId = expectedAudience.startsWith("api://")
                ? expectedAudience
                : "api://" + expectedAudience;

            List<String> audiences = jwt.getAudience();
            if (audiences != null && (audiences.contains(bareId) || audiences.contains(prefixedId))) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "El audience del token (" + audiences + ") no coincide con " + expectedAudience,
                null
            );
            return OAuth2TokenValidatorResult.failure(error);
        }
    }
}
