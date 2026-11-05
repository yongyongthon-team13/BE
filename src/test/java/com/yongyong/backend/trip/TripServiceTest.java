package com.yongyong.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.trip.TripDtos.EventRequest;
import com.yongyong.backend.trip.TripDtos.LocationRequest;
import com.yongyong.backend.trip.TripDtos.StartRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@TestPropertySource(locations = "classpath:account-test.properties")
@Transactional
class TripServiceTest {
    @Autowired TripService trips;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate db;

    @Test
    void startRequiresConsentAndEndingTripDeletesPreciseLocation() {
        Jwt elder = token("trip-owner");
        String userId = accounts.login(elder, "ELDER").userId();
        String elderId = accounts.createProfile(elder, "이동 테스트", 1950, null, null).elderId();
        String routeId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        db.update("""
                INSERT INTO route_plans(route_plan_id, owner_user_id, mode, provider, origin_name, origin_lat, origin_lon,
                  destination_name, destination_lat, destination_lon, duration_seconds, distance_meters, fetched_at, details_json)
                VALUES (?, ?, 'WALK', 'TMAP_WALK', '출발', 37.26, 127.15, '병원', 37.27, 127.14, 600, 500, ?, '{}')
                """, routeId, userId, java.sql.Timestamp.from(now));

        assertThatThrownBy(() -> trips.start(elder, new StartRequest(routeId, now)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("422");
        accounts.recordConsent(elder, elderId, "LOCATION", "v1", true);
        TripDtos.Started started = trips.start(elder, new StartRequest(routeId, now));
        assertThat(started.status()).isEqualTo("ACTIVE");

        trips.updateLocation(elder, started.tripId(), new LocationRequest(37.265, 127.145, 12, Instant.now()));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM trip_current_locations WHERE trip_id = ?", Integer.class,
                started.tripId())).isEqualTo(1);
        trips.event(elder, started.tripId(), new EventRequest("ARRIVED", null));

        assertThat(db.queryForObject("SELECT COUNT(*) FROM trip_current_locations WHERE trip_id = ?", Integer.class,
                started.tripId())).isZero();
        assertThat(trips.get(elder, started.tripId()).status()).isEqualTo("ARRIVED");
    }

    @Test
    void sustainedDelayQueuesOnlyOneCaregiverNotification() {
        Jwt elder = token("trip-delay-owner");
        Jwt guardian = token("trip-delay-guardian");
        String userId = accounts.login(elder, "ELDER").userId();
        accounts.login(guardian, "GUARDIAN");
        String elderId = accounts.createProfile(elder, "이동 테스트", 1950, null, null).elderId();
        accounts.recordConsent(elder, elderId, "LOCATION", "v1", true);
        accounts.recordConsent(elder, elderId, "CAREGIVER_SHARING", "v1", true);
        accounts.connectGuardian(guardian, accounts.issueLinkCode(elder, elderId).code(), "자녀");
        String routeId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        db.update("""
                INSERT INTO route_plans(route_plan_id, owner_user_id, mode, provider, origin_name, origin_lat, origin_lon,
                  destination_name, destination_lat, destination_lon, duration_seconds, distance_meters, fetched_at, details_json)
                VALUES (?, ?, 'WALK', 'TMAP_WALK', '출발', 37.26, 127.15, '병원', 37.27, 127.14, 600, 500, ?, '{}')
                """, routeId, userId, java.sql.Timestamp.from(now));
        var started = trips.start(elder, new StartRequest(routeId, now));
        db.update("UPDATE trips SET delay_since = ? WHERE trip_id = ?",
                java.sql.Timestamp.from(now.minusSeconds(360)), started.tripId());

        var firstLocation = trips.updateLocation(elder, started.tripId(),
                new LocationRequest(37.25, 127.16, 10, Instant.now()));
        assertThat(firstLocation.delayed()).isTrue();
        trips.updateLocation(elder, started.tripId(), new LocationRequest(37.2501, 127.1601, 10, Instant.now().plusMillis(1)));

        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE trip_id = ? AND type = 'DELAY'",
                Integer.class, started.tripId())).isEqualTo(1);
        assertThat(db.queryForObject("SELECT status FROM notifications WHERE trip_id = ? AND type = 'DELAY'",
                String.class, started.tripId())).isEqualTo("QUEUED");
    }

    private static Jwt token(String subject) {
        return Jwt.withTokenValue(subject).header("alg", "RS256")
                .issuer("https://id.example.test").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }
}
