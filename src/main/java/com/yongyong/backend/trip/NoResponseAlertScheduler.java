package com.yongyong.backend.trip;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NoResponseAlertScheduler {
    private final TripService trips;

    public NoResponseAlertScheduler(TripService trips) {
        this.trips = trips;
    }

    @Scheduled(fixedDelayString = "${app.trips.no-response-check-ms:60000}")
    public void check() {
        trips.checkNoResponseAlerts();
    }
}
