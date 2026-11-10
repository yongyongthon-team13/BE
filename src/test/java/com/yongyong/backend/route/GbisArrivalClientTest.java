package com.yongyong.backend.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.yongyong.backend.route.RouteDtos.Arrival;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GbisArrivalClientTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void mapsArrivalTimeAndLowFloorVehicleType() throws Exception {
        var node = mapper.readTree("""
                {"predictTime1":3,"lowPlate1":1,"predictTime2":7,"lowPlate2":0}
                """);

        assertEquals(List.of(new Arrival(180, "LOW_FLOOR"), new Arrival(420, "STANDARD")),
                GbisArrivalClient.arrivals(node));
    }

    @Test
    void unknownVehicleCodeIsNotTreatedAsLowFloor() throws Exception {
        var node = mapper.readTree("""
                {"predictTime1":2,"lowPlate1":99,"predictTime2":-1,"lowPlate2":1}
                """);

        assertEquals(List.of(new Arrival(120, "UNKNOWN")), GbisArrivalClient.arrivals(node));
    }
}
