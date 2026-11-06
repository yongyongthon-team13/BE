package com.yongyong.backend.account;

import java.util.List;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    static final String ISSUER = "https://auth.yongyongthon.example/phone";
    static final String AUDIENCE = "yongyongthon-api";
    @Value("${app.auth.jwt-secret:}") private String encodedSecret;
    @Value("${app.data-key:}") private String encodedDataKey;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Account and assigned mobility APIs require JWT; API documentation stays public.
        http.securityMatcher("/api/v1/auth/**", "/api/v1/elder-profiles/**",
                "/api/v1/guardian-links/**", "/api/v1/notifications/**",
                "/api/v1/places/**", "/api/v1/route-plans/**", "/api/v1/trips/**",
                "/api/v1/guardian/**", "/api/v1/appointments/**",
                "/api/v1/appointment-extractions/**", "/api/v1/weather-risk/**",
                "/swagger-ui/**", "/swagger-ui.html",
                "/v3/api-docs/**");
        http.csrf(csrf -> csrf.disable());
        if (configured()) {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/api/v1/auth/phone-verifications", "/api/v1/auth/login").permitAll()
                    .anyRequest().authenticated());
            http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}));
        } else {
            // No signing key: account data stays closed without blocking other modules.
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    .anyRequest().denyAll());
        }
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        if (!configured()) {
            return token -> { throw new IllegalStateException("APP_JWT_SECRET is required"); };
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey()).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
            jwt.getAudience().contains(AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            List.of(JwtValidators.createDefaultWithIssuer(ISSUER), audienceValidator)));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder() {
        if (!configured()) return parameters -> { throw new IllegalStateException("APP_JWT_SECRET is required"); };
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    SecretKey secretKey() {
        try {
            byte[] raw = Base64.getDecoder().decode(encodedSecret);
            if (raw.length < 32) throw new IllegalArgumentException("APP_JWT_SECRET must contain at least 32 bytes");
            return new SecretKeySpec(raw, "HmacSHA256");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_JWT_SECRET must be Base64 encoded and at least 32 bytes", e);
        }
    }

    boolean configured() {
        return !encodedSecret.isBlank() && !encodedDataKey.isBlank();
    }
}
