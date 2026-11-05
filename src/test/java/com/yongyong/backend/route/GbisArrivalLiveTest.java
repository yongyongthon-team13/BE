package com.yongyong.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.backend.route.RouteDtos.Point;
import com.yongyong.backend.route.RouteDtos.RoutePlan;
import com.yongyong.backend.route.RouteDtos.Summary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfSystemProperty(named = "runLiveTmapTests", matches = "true")
@EnabledIfEnvironmentVariable(named = "TMAP_APP_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GBIS_SERVICE_KEY", matches = ".+")
class GbisArrivalLiveTest {
    @Autowired TmapTransitClient transit;
    @Autowired GbisArrivalClient arrivals;

    @Test
    void realTmapTransitBusLegCanBeRefreshedAgainstGyeonggiBus() {
        Point origin = new Point("동백역", 37.269075, 127.152708);
        Point destination = new Point("용인시청", 37.2409, 127.1773);
        var alternatives = transit.search(origin, destination, 3);
        assertThat(alternatives).isNotEmpty();
        var route = alternatives.get(0);
        var liveBusLegs = alternatives.stream().flatMap(alternative -> alternative.legs().stream())
                .filter(leg -> "BUS".equals(leg.mode()) && leg.route() != null).toList();
        assertThat(liveBusLegs).isNotEmpty();
        var livePlan = new RoutePlan("live-tmap-segments", "TMAP_TRANSIT", "TRANSIT", Instant.now(), null,
                new Summary(route.durationSeconds(), route.distanceMeters(), route.walkingSeconds(),
                        route.transferCount(), route.fareWon()), liveBusLegs, List.of());
        var results = arrivals.refresh(livePlan);
        assertThat(results).isNotEmpty();
        assertThat(results).as("live TMAP bus segments should match a live GBIS arrival")
                .extracting(RouteDtos.StopArrival::status).contains("AVAILABLE");
        assertThat(results).allSatisfy(result -> {
            assertThat(result.status()).isIn("AVAILABLE", "NOT_FOUND");
            assertThat(result.fetchedAt()).isNotNull();
            if ("AVAILABLE".equals(result.status())) assertThat(result.arrivals()).isNotEmpty();
        });
    }
}
