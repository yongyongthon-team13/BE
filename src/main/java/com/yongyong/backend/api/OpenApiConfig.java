package com.yongyong.backend.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "길벗 API",
        version = "v1",
        description = "어르신 병원 이동·보호자 연동 API"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
    @Bean
    GroupedOpenApi assignedApi() {
        return GroupedOpenApi.builder()
                .group("assigned-api")
                .pathsToMatch("/api/v1/places/**", "/api/v1/route-plans/**", "/api/v1/trips/**",
                        "/api/v1/guardian/**", "/api/v1/appointments/**",
                        "/api/v1/appointment-extractions/**", "/api/v1/weather-risk/**")
                .build();
    }
}
