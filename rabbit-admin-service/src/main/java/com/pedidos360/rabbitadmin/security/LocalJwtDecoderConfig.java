package com.pedidos360.rabbitadmin.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * SOLO DESARROLLO LOCAL (perfil "local"): verifica tokens HS256 firmados con el secreto de la
 * variable de entorno LOCAL_JWT_SECRET (nunca va en el repositorio). Sigue validando issuer,
 * audience y vigencia. Fuera del perfil "local" esta clase no se carga y se usa Entra ID.
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
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator(audience)));
        return decoder;
    }
}
