package com.yongyong.backend.weather;

import com.yongyong.backend.weather.GridConverter.Grid;
import com.yongyong.backend.weather.KmaClient.ForecastItem;
import com.yongyong.backend.weather.dto.WeatherRiskResponse;
import com.yongyong.backend.weather.dto.WeatherRiskResponse.Hazard;
import com.yongyong.backend.weather.dto.WeatherRiskResponse.RiskLevel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;

@Slf4j
@Service
public class WeatherRiskService {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter SLOT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private static final Pattern WARNING_LINE =
            Pattern.compile("o\\s*((?:호우|대설|태풍|폭염|한파|강풍)(?:주의보|경보))\\s*:\\s*(.*)");
    private static final Pattern GYEONGGI = Pattern.compile("경기도(?:\\(([^)]*)\\))?");
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final KmaClient kmaClient;
    private final String simulatedWarning;

    public WeatherRiskService(KmaClient kmaClient, @Value("${weather.simulated-warning}") String simulatedWarning) {
        this.kmaClient = kmaClient;
        this.simulatedWarning = simulatedWarning;
    }

    public WeatherRiskResponse assess(double latitude, double longitude, OffsetDateTime departureAt) {
        OffsetDateTime now = OffsetDateTime.now(KST);
        Map<String, String> forecast = closestForecast(fetchForecast(latitude, longitude, now), departureAt);

        List<Hazard> hazards = hazardsOf(forecast);
        RiskLevel level = levelOf(forecast, hazards);

        List<String> warnings = new ArrayList<>();
        boolean warningChecked = false;
        boolean simulated = !simulatedWarning.isBlank();
        if (simulated) {
            warnings.add(simulatedWarning);
            warningChecked = true;
        } else if (isYongin(latitude, longitude)
                && departureAt.isBefore(now.plusHours(24))) {
            try {
                warnings.addAll(warningsForYongin(kmaClient.getCurrentWarnings()));
                warningChecked = true;
            } catch (RestClientException | JacksonException | IllegalStateException e) {
                log.warn("기상특보 조회 실패: {}", e.getClass().getSimpleName());
            }
        }
        if (!warnings.isEmpty()) {
            level = RiskLevel.WARNING;
        }

        return new WeatherRiskResponse(level, level != RiskLevel.NORMAL, hazards, warnings, warningChecked, simulated,
                integer(forecast.get("TMP")), integer(forecast.get("POP")), departureAt);
    }

    private List<ForecastItem> fetchForecast(double latitude, double longitude, OffsetDateTime now) {
        Grid grid = GridConverter.toGrid(latitude, longitude);
        LocalDateTime base = latestBaseTime(now.toLocalDateTime());
        try {
            return kmaClient.getForecast(base.format(DATE), base.format(TIME), grid.nx(), grid.ny());
        } catch (RestClientException | JacksonException | IllegalStateException e) {
            log.warn("단기예보 조회 실패: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "날씨 정보를 가져오지 못했습니다.");
        }
    }

    // 발표 후 10분 뒤부터 조회 가능
    static LocalDateTime latestBaseTime(LocalDateTime now) {
        LocalDateTime available = now.minusMinutes(10);
        int hour = available.getHour();
        int baseHour = hour < 2 ? -1 : hour - (hour + 1) % 3;
        return available.toLocalDate().atStartOfDay().plusHours(baseHour);
    }

    private Map<String, String> closestForecast(List<ForecastItem> items, OffsetDateTime departureAt) {
        LocalDateTime target = departureAt.atZoneSameInstant(KST).toLocalDateTime();
        Optional<String> slot = items.stream()
                .map(item -> item.fcstDate() + item.fcstTime())
                .distinct()
                .filter(key -> minutesBetween(key, target) <= 60)
                .min(Comparator.comparingLong(key -> minutesBetween(key, target)));
        if (slot.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "예보 범위를 벗어난 시간입니다.");
        }
        return items.stream()
                .filter(item -> slot.get().equals(item.fcstDate() + item.fcstTime()))
                .collect(Collectors.toMap(ForecastItem::category, ForecastItem::fcstValue));
    }

    private long minutesBetween(String slot, LocalDateTime target) {
        return Math.abs(Duration.between(LocalDateTime.parse(slot, SLOT), target).toMinutes());
    }

    static List<Hazard> hazardsOf(Map<String, String> forecast) {
        List<Hazard> hazards = new ArrayList<>();
        int precipitationType = number(forecast.get("PTY")).intValue();
        if (precipitationType == 1 || precipitationType == 2 || precipitationType == 4
                || number(forecast.get("POP")) >= 60) {
            hazards.add(Hazard.RAIN);
        }
        if (precipitationType == 2 || precipitationType == 3) hazards.add(Hazard.SNOW);
        if (number(forecast.get("PCP")) >= 30) hazards.add(Hazard.HEAVY_RAIN);
        if (number(forecast.get("SNO")) >= 5) hazards.add(Hazard.HEAVY_SNOW);
        if (number(forecast.get("WSD")) >= 9) hazards.add(Hazard.STRONG_WIND);
        if (forecast.get("TMP") != null) {
            int temperature = integer(forecast.get("TMP"));
            if (temperature >= 33) hazards.add(Hazard.HEAT);
            if (temperature <= -5) hazards.add(Hazard.COLD);
        }
        return hazards;
    }

    private static RiskLevel levelOf(Map<String, String> forecast, List<Hazard> hazards) {
        boolean severe = hazards.contains(Hazard.HEAVY_RAIN)
                || hazards.contains(Hazard.HEAVY_SNOW)
                || number(forecast.get("WSD")) >= 14
                || (forecast.get("TMP") != null
                        && (integer(forecast.get("TMP")) >= 35 || integer(forecast.get("TMP")) <= -12));
        if (severe) return RiskLevel.WARNING;
        return hazards.isEmpty() ? RiskLevel.NORMAL : RiskLevel.CAUTION;
    }

    static List<String> warningsForYongin(String currentWarnings) {
        List<String> warnings = new ArrayList<>();
        for (String line : currentWarnings.split("\\R")) {
            Matcher warning = WARNING_LINE.matcher(line.trim());
            if (!warning.matches()) continue;
            Matcher gyeonggi = GYEONGGI.matcher(warning.group(2));
            while (gyeonggi.find()) {
                if (gyeonggi.group(1) == null || gyeonggi.group(1).contains("용인")) {
                    warnings.add(warning.group(1));
                    break;
                }
            }
        }
        return warnings;
    }

    // 용인시 경계를 감싼 사각형이라 바로 옆 시 일부도 포함됨. 정밀하게 하려면 주소 변환 API 필요
    private static boolean isYongin(double latitude, double longitude) {
        return latitude >= 37.09 && latitude <= 37.37 && longitude >= 127.03 && longitude <= 127.43;
    }

    // "강수없음", "1mm 미만", "30.0~50.0mm" 같은 값에서 첫 숫자만 사용
    private static Double number(String value) {
        if (value == null) return 0.0;
        Matcher matcher = FIRST_NUMBER.matcher(value);
        return matcher.find() ? Double.valueOf(matcher.group()) : 0.0;
    }

    private static Integer integer(String value) {
        return value == null ? null : (int) Math.round(Double.parseDouble(value));
    }
}
