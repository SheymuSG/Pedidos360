package com.pedidos360.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * SOLO DESARROLLO LOCAL (perfil "local"). Permite probar el flujo completo sin un
 * login real de Entra ID verificando tokens HS256 firmados con un secreto que se
 * entrega por la variable de entorno LOCAL_JWT_SECRET (nunca va en el repositorio).
 * Se siguen validando issuer, audience y vigencia. En cualquier otro perfil esta
 * clase no se carga y la firma se valida contra las claves publicas de Entra ID.
 */
@Configuration
@Profile("local")
class LocalJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${pedidos360.security.local-jwt-secret}") String secret,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${pedidos360.security.audience}") String audience) {

        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new SecurityConfig.AudienceValidator(audience));
        decoder.setJwtValidator(validators);
        return decoder;
    }
}
