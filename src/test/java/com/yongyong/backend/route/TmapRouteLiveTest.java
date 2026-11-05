package com.yongyong.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.backend.route.RouteDtos.Point;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfSystemProperty(named = "runLiveTmapTests", matches = "true")
class TmapRouteLiveTest {
    @Autowired TmapWalkingClient walking;
    @Autowired TmapTransitClient transit;

    private final Point origin = new Point("동백역", 37.269075, 127.152708);
    private final Point destination = new Point("용인세브란스병원", 37.272047, 127.148153);

    @Test
    void walkingRouteCallsRealTmapAndReturnsNormalizedGeometry() {
        var route = walking.search(origin, destination);
        assertThat(route.distanceMeters()).isPositive();
        assertThat(route.durationSeconds()).isPositive();
        assertThat(route.legs()).isNotEmpty();
        assertThat(route.legs().get(0).geometry()).isNotEmpty();
    }

    @Test
    void transitRouteCallsRealTmapAndReturnsAlternatives() {
        var routes = transit.search(origin, destination, 3);
        assertThat(routes).isNotEmpty();
        assertThat(routes.get(0).durationSeconds()).isPositive();
        assertThat(routes.get(0).legs()).isNotEmpty();
    }
}
