package com.pedidos360.rabbitadmin.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/** Verifica que el claim "aud" del token corresponda a esta API. */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedAudience;

    public AudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            return OAuth2TokenValidatorResult.success();
        }
        // Entra ID a veces emite el aud como el Client ID "pelado" (sin api://) aunque el
        // Application ID URI sea api://<client-id>. Se aceptan ambas formas.
        String bareId = expectedAudience.startsWith("api://")
                ? expectedAudience.substring("api://".length()) : expectedAudience;
        String prefixedId = expectedAudience.startsWith("api://")
                ? expectedAudience : "api://" + expectedAudience;

        List<String> audiences = jwt.getAudience();
        if (audiences != null && (audiences.contains(bareId) || audiences.contains(prefixedId))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                "El audience del token (" + audiences + ") no coincide con " + expectedAudience, null));
    }
}
