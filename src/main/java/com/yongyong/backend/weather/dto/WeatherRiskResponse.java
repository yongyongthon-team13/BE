package com.yongyong.backend.weather.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record WeatherRiskResponse(
        RiskLevel riskLevel,
        boolean recommendCar,
        List<Hazard> hazardCodes,
        List<String> warnings,
        boolean warningChecked,
        boolean simulated,
        Integer temperatureC,
        Integer precipitationProbability,
        OffsetDateTime forecastFor) {

    public enum RiskLevel { NORMAL, CAUTION, WARNING }

    public enum Hazard { RAIN, SNOW, HEAVY_RAIN, HEAVY_SNOW, STRONG_WIND, HEAT, COLD }
}
