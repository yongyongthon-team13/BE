package com.yongyong.backend.trip;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredLocationCleanup {
    private final TripService trips;
    public ExpiredLocationCleanup(TripService trips) { this.trips = trips; }

    @Scheduled(fixedDelayString = "${app.trips.location-cleanup-ms:60000}")
    public void cleanup() { trips.cleanupExpiredLocations(); }
}
