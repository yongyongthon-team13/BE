package com.yongyong.backend.weather;

import com.yongyong.backend.weather.dto.WeatherRiskResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather-risk")
@RequiredArgsConstructor
public class WeatherRiskController {

    private final WeatherRiskService weatherRiskService;

    @GetMapping
    public WeatherRiskResponse assess(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam("for") OffsetDateTime departureAt) {
        return weatherRiskService.assess(latitude, longitude, departureAt);
    }
}
