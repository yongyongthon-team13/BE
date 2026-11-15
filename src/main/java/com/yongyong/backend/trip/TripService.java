package com.yongyong.backend.trip;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.account.AccountService.User;
import com.yongyong.backend.account.DataCipher;
import com.yongyong.backend.trip.TripDtos.*;
import com.yongyong.backend.push.PushOutboxService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TripService {
    private static final Duration LOCATION_TTL = Duration.ofMinutes(2);
    private static final double NO_RESPONSE_DISTANCE_METERS = 1_200;
    private final Duration delayThreshold;
    private final Duration delayDwell;
    private final Duration noResponseGrace;
    private final JdbcTemplate db;
    private final AccountService accounts;
    private final DataCipher cipher;
    private final ObjectMapper mapper;
    private final PushOutboxService pushOutbox;

    public TripService(JdbcTemplate db, AccountService accounts, DataCipher cipher, ObjectMapper mapper,
                       PushOutboxService pushOutbox,
                       @Value("${app.trips.delay-threshold-minutes:10}") long delayThresholdMinutes,
                       @Value("${app.trips.delay-dwell-minutes:5}") long delayDwellMinutes,
                       @Value("${app.trips.no-response-grace-minutes:5}") long noResponseGraceMinutes) {
        this.db = db; this.accounts = accounts; this.cipher = cipher; this.mapper = mapper;
        this.pushOutbox = pushOutbox;
        this.delayThreshold = Duration.ofMinutes(Math.max(1, delayThresholdMinutes));
        this.delayDwell = Duration.ofMinutes(Math.max(1, delayDwellMinutes));
        this.noResponseGrace = Duration.ofMinutes(Math.max(1, noResponseGraceMinutes));
    }

    @Transactional
    public Started start(Jwt jwt, StartRequest request) {
        User user = accounts.current(jwt);
        if (!"ELDER".equals(user.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        List<String> elders = db.query("SELECT elder_id FROM elder_profiles WHERE user_id = ?",
                (rs, n) -> rs.getString(1), user.userId());
        if (elders.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Elder profile required");
        String elderId = elders.get(0);
        if (!consented(elderId, "LOCATION")) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Location consent required");
        List<RouteInfo> routes = db.query("SELECT duration_seconds, owner_user_id FROM route_plans WHERE route_plan_id = ?",
                (rs, n) -> new RouteInfo(rs.getInt(1), rs.getString(2)), request.routePlanId());
        if (routes.isEmpty() || !routes.get(0).ownerId().equals(user.userId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Route plan not found");
        if (!db.query("SELECT trip_id FROM trips WHERE elder_id = ? AND status IN ('ACTIVE', 'HELP_REQUESTED')",
                (rs, n) -> rs.getString(1), elderId).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Active trip exists");
        Instant now = Instant.now();
        Instant departure = request.plannedDepartureAt() == null ? now : request.plannedDepartureAt();
        Instant plannedArrival = departure.plusSeconds(Math.max(0, routes.get(0).durationSeconds()));
        String tripId = UUID.randomUUID().toString();
        db.update("""
                INSERT INTO trips(trip_id, route_plan_id, elder_id, status, planned_departure_at,
                  planned_arrival_at, estimated_arrival_at, started_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                """, tripId, request.routePlanId(), elderId, ts(departure), ts(plannedArrival),
                ts(plannedArrival), ts(now));
        db.update("INSERT INTO trip_events(event_id, trip_id, event_type, occurred_at) VALUES (?, ?, 'DEPARTED', ?)",
                id(), tripId, ts(now));
        enqueueGuardianTripEvent(tripId, elderId, "DEPARTED");
        return new Started(tripId, request.routePlanId(), "ACTIVE", departure, now, plannedArrival);
    }

    public TripStatus get(Jwt jwt, String tripId) {
        User user = accounts.current(jwt);
        List<TripStatus> rows = db.query("""
                SELECT t.trip_id, t.route_plan_id, t.status, t.planned_departure_at, t.planned_arrival_at,
                  t.estimated_arrival_at, t.started_at, t.ended_at, l.recorded_at,
                  CASE WHEN l.expires_at > CURRENT_TIMESTAMP(6) THEN l.latitude END,
                  CASE WHEN l.expires_at > CURRENT_TIMESTAMP(6) THEN l.longitude END,
                  (SELECT ev.event_type FROM trip_events ev WHERE ev.trip_id = t.trip_id
                   ORDER BY ev.occurred_at DESC, ev.event_id DESC LIMIT 1)
                FROM trips t JOIN elder_profiles e ON e.elder_id = t.elder_id
                LEFT JOIN trip_current_locations l ON l.trip_id = t.trip_id
                WHERE t.trip_id = ? AND (e.user_id = ? OR EXISTS (
                  SELECT 1 FROM guardian_links gl WHERE gl.elder_id = t.elder_id
                    AND gl.guardian_user_id = ? AND gl.status = 'ACTIVE'))
                """, (rs, n) -> new TripStatus(rs.getString(1), rs.getString(2), rs.getString(3),
                instant(rs.getTimestamp(4)), instant(rs.getTimestamp(5)), instant(rs.getTimestamp(6)),
                instant(rs.getTimestamp(7)), instant(rs.getTimestamp(8)), instant(rs.getTimestamp(9)),
                (Double) rs.getObject(10), (Double) rs.getObject(11), rs.getString(12)),
                tripId, user.userId(), user.userId());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        if ("GUARDIAN".equals(user.role()) && (!consented(elderIdForTrip(tripId), "CAREGIVER_SHARING")
                || !consented(elderIdForTrip(tripId), "LOCATION")))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        return rows.get(0);
    }

    public TripStatus activeTripForGuardian(Jwt jwt, String elderId) {
        User guardian = accounts.current(jwt);
        if (!"GUARDIAN".equals(guardian.role()) || !activeGuardian(guardian.userId(), elderId)
                || !consented(elderId, "LOCATION"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        List<String> tripIds = db.query("SELECT trip_id FROM trips WHERE elder_id = ? AND status IN ('ACTIVE', 'HELP_REQUESTED') ORDER BY started_at DESC LIMIT 1",
                (rs, n) -> rs.getString(1), elderId);
        if (tripIds.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active trip not found");
        return get(jwt, tripIds.get(0));
    }

    @Transactional
    public LocationResult updateLocation(Jwt jwt, String tripId, LocationRequest request) {
        User user = accounts.current(jwt);
        TripContext trip = ownedActiveTrip(user, tripId);
        Instant now = Instant.now();
        if (request.recordedAt().isBefore(now.minusSeconds(120)) || request.recordedAt().isAfter(now.plusSeconds(30)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stale location");
        if (request.accuracyMeters() > 100)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Location accuracy too low");
        Instant expiresAt = now.plus(LOCATION_TTL);
        List<Instant> latestRows = db.query("SELECT recorded_at FROM trip_current_locations WHERE trip_id = ?",
                (rs, n) -> instant(rs.getTimestamp(1)), tripId);
        if (!latestRows.isEmpty() && latestRows.get(0).isAfter(request.recordedAt()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Older location update");
        int updated = db.update("UPDATE trip_current_locations SET latitude = ?, longitude = ?, accuracy_meters = ?, recorded_at = ?, expires_at = ? WHERE trip_id = ?",
                request.latitude(), request.longitude(), request.accuracyMeters(), ts(request.recordedAt()), ts(expiresAt), tripId);
        if (updated == 0) db.update("INSERT INTO trip_current_locations(trip_id, latitude, longitude, accuracy_meters, recorded_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                tripId, request.latitude(), request.longitude(), request.accuracyMeters(), ts(request.recordedAt()), ts(expiresAt));

        double destinationLat = trip.destinationLat();
        double destinationLon = trip.destinationLon();
        long remainingSeconds = Math.round(haversineMeters(request.latitude(), request.longitude(),
                destinationLat, destinationLon) * 1.3 / 0.8);
        Instant estimated = request.recordedAt().plusSeconds(Math.max(0, remainingSeconds));
        db.update("UPDATE trips SET estimated_arrival_at = ? WHERE trip_id = ?", ts(estimated), tripId);
        boolean late = estimated.isAfter(trip.plannedArrival().plus(delayThreshold));
        if (late) {
            db.update("UPDATE trips SET delay_since = COALESCE(delay_since, ?) WHERE trip_id = ?",
                    ts(request.recordedAt()), tripId);
            List<Instant> sinceRows = db.query("SELECT delay_since FROM trips WHERE trip_id = ?",
                    (rs, n) -> instant(rs.getTimestamp(1)), tripId);
            if (!sinceRows.isEmpty() && sinceRows.get(0) != null &&
                    !now.isBefore(sinceRows.get(0).plus(delayDwell))) createGuardianDelayNotifications(tripId, trip.elderId());
        } else {
            db.update("UPDATE trips SET delay_since = NULL WHERE trip_id = ?", tripId);
        }
        return new LocationResult("RECENT", request.recordedAt(), expiresAt, estimated, late);
    }

    @Transactional
    public EventResult event(Jwt jwt, String tripId, EventRequest request) {
        User user = accounts.current(jwt);
        TripContext trip = ownedActiveTrip(user, tripId);
        String event = request.eventType();
        if (!List.of("BOARDED", "ALIGHTED", "ARRIVED", "HELP_REQUESTED", "CANCELLED", "ABORTED").contains(event))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event type");
        Instant now = Instant.now();
        String status = switch (event) {
            case "ARRIVED" -> "ARRIVED";
            case "CANCELLED", "ABORTED" -> "CLOSED";
            case "HELP_REQUESTED" -> "HELP_REQUESTED";
            default -> "ACTIVE";
        };
        String eventId = id();
        db.update("INSERT INTO trip_events(event_id, trip_id, event_type, detail_code, occurred_at) VALUES (?, ?, ?, ?, ?)",
                eventId, tripId, event, request.detailCode(), ts(now));
        if (!"ACTIVE".equals(status)) {
            db.update("UPDATE trips SET status = ?, ended_at = ? WHERE trip_id = ?", status, ts(now), tripId);
            db.update("DELETE FROM trip_current_locations WHERE trip_id = ?", tripId);
        }
        if ("ARRIVED".equals(event) || "HELP_REQUESTED".equals(event))
            enqueueGuardianTripEvent(tripId, trip.elderId(), event);
        return new EventResult(eventId, event, now, status);
    }

    @Transactional
    public EventResult checkIn(Jwt jwt, String tripId, CheckInRequest request) {
        String response = request.response();
        if (!List.of("OK", "NEED_HELP").contains(response))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid check-in response");
        User user = accounts.current(jwt);
        ownedActiveTrip(user, tripId);
        Instant now = Instant.now();
        String eventId = id();
        db.update("INSERT INTO trip_events(event_id, trip_id, event_type, detail_code, occurred_at) VALUES (?, ?, 'CHECK_IN_RESPONDED', ?, ?)",
                eventId, tripId, response, ts(now));
        String status = "OK".equals(response) ? "ACTIVE" : "HELP_REQUESTED";
        db.update("UPDATE trips SET status = ?, delay_since = NULL WHERE trip_id = ?", status, tripId);
        return new EventResult(eventId, "CHECK_IN_RESPONDED", now, status);
    }

    @Transactional
    public ContactActionResult contactAction(Jwt jwt, String elderId, String tripId, ContactActionRequest request) {
        if (!List.of("CALL", "MESSAGE", "EMERGENCY_CALL").contains(request.actionType())
                || (request.outcome() != null && !List.of("CONNECTED", "NO_ANSWER", "FAILED", "OTHER")
                .contains(request.outcome())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid contact action value");
        User guardian = accounts.current(jwt);
        if (!"GUARDIAN".equals(guardian.role()) || !activeGuardian(guardian.userId(), elderId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        List<String> tripIds = db.query("SELECT trip_id FROM trips WHERE trip_id = ? AND elder_id = ?",
                (rs, n) -> rs.getString(1), tripId, elderId);
        if (tripIds.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        Instant now = Instant.now();
        String actionId = id();
        String actionType = request.actionType();
        String outcome = request.outcome();
        db.update("INSERT INTO guardian_contact_actions(contact_action_id, trip_id, guardian_user_id, action_type, outcome, occurred_at) VALUES (?, ?, ?, ?, ?, ?)",
                actionId, tripId, guardian.userId(), actionType, outcome, ts(now));
        return new ContactActionResult(actionId, tripId, actionType, outcome, now);
    }

    @Transactional
    public int cleanupExpiredLocations() {
        return db.update("DELETE FROM trip_current_locations WHERE expires_at <= CURRENT_TIMESTAMP(6)");
    }

    @Transactional
    public int checkNoResponseAlerts() {
        Instant now = Instant.now();
        Instant graceCutoff = now.minus(noResponseGrace);
        List<NoResponseCandidate> candidates = db.query("""
                SELECT t.trip_id, t.elder_id, t.planned_arrival_at, l.latitude, l.longitude,
                  r.destination_lat, r.destination_lon
                FROM trips t
                JOIN route_plans r ON r.route_plan_id = t.route_plan_id
                JOIN trip_current_locations l ON l.trip_id = t.trip_id
                WHERE t.status = 'ACTIVE' AND t.no_response_notified_at IS NULL
                  AND t.planned_arrival_at <= ? AND l.expires_at > ?
                  AND NOT EXISTS (
                    SELECT 1 FROM trip_events ev WHERE ev.trip_id = t.trip_id
                      AND ev.event_type = 'CHECK_IN_RESPONDED'
                      AND ev.occurred_at >= t.planned_arrival_at
                  )
                ORDER BY t.planned_arrival_at
                """, (rs, n) -> new NoResponseCandidate(rs.getString(1), rs.getString(2),
                rs.getDouble(4), rs.getDouble(5), rs.getDouble(6), rs.getDouble(7)),
                ts(graceCutoff), ts(now));

        int notified = 0;
        for (NoResponseCandidate candidate : candidates) {
            double distance = haversineMeters(candidate.latitude(), candidate.longitude(),
                    candidate.destinationLat(), candidate.destinationLon());
            if (distance <= NO_RESPONSE_DISTANCE_METERS || !consented(candidate.elderId(), "CAREGIVER_SHARING"))
                continue;
            List<String> guardians = db.query("SELECT guardian_user_id FROM guardian_links WHERE elder_id = ? AND status = 'ACTIVE'",
                    (rs, n) -> rs.getString(1), candidate.elderId());
            if (guardians.isEmpty()) continue;

            int claimed = db.update("UPDATE trips SET no_response_notified_at = ? WHERE trip_id = ? AND no_response_notified_at IS NULL",
                    ts(now), candidate.tripId());
            if (claimed == 0) continue;
            for (String guardianId : guardians) {
                db.update("""
                        INSERT INTO notifications(notification_id, trip_id, recipient_user_id, channel, type, status, sent_at)
                        VALUES (?, ?, ?, 'APP', 'NO_RESPONSE', 'QUEUED', NULL)
                        """, id(), candidate.tripId(), guardianId);
                pushOutbox.enqueue(guardianId, "TRIP_NO_RESPONSE", "어르신 안부 확인이 필요합니다",
                        "예정 도착 시간이 지났고 병원에서 1.2km 이상 떨어져 있습니다.",
                        "{\"type\":\"TRIP_NO_RESPONSE\",\"tripId\":\"" + candidate.tripId() + "\"}");
            }
            notified++;
        }
        return notified;
    }

    private void createGuardianDelayNotifications(String tripId, String elderId) {
        if (!consented(elderId, "CAREGIVER_SHARING")) return;
        List<String> already = db.query("SELECT notification_id FROM notifications WHERE trip_id = ? AND type = 'DELAY'",
                (rs, n) -> rs.getString(1), tripId);
        if (!already.isEmpty()) return;
        List<String> guardians = db.query("SELECT guardian_user_id FROM guardian_links WHERE elder_id = ? AND status = 'ACTIVE'",
                (rs, n) -> rs.getString(1), elderId);
        for (String guardianId : guardians) {
            db.update("""
                INSERT INTO notifications(notification_id, trip_id, recipient_user_id, channel, type, status, sent_at)
                VALUES (?, ?, ?, 'APP', 'DELAY', 'QUEUED', NULL)
                """, id(), tripId, guardianId);
            pushOutbox.enqueue(guardianId, "TRIP_DELAY", "이동 지연 안내", "어르신의 도착이 예정 시간보다 늦어지고 있습니다.",
                    "{\"type\":\"TRIP_DELAY\",\"tripId\":\"" + tripId + "\"}");
        }
    }

    private void enqueueGuardianTripEvent(String tripId, String elderId, String eventType) {
        if (!consented(elderId, "CAREGIVER_SHARING")) return;

        String bodyTemplate = switch (eventType) {
            case "DEPARTED" -> "%s님이 병원으로 출발했어요";
            case "ARRIVED" -> "%s님이 병원에 도착했어요";
            case "HELP_REQUESTED" -> "%s님이 도움을 요청했어요";
            default -> null;
        };
        if (bodyTemplate == null) return;

        List<String> names = db.query("SELECT display_name_enc FROM elder_profiles WHERE elder_id = ?",
                (rs, n) -> cipher.decrypt(rs.getString(1)), elderId);
        String elderName = names.isEmpty() || names.get(0) == null || names.get(0).isBlank()
                ? "어르신" : names.get(0);
        String body = bodyTemplate.formatted(elderName);
        List<String> guardians = db.query(
                "SELECT guardian_user_id FROM guardian_links WHERE elder_id = ? AND status = 'ACTIVE'",
                (rs, n) -> rs.getString(1), elderId);
        for (String guardianId : guardians) {
            db.update("""
                    INSERT INTO notifications(notification_id, trip_id, recipient_user_id, channel, type, status, sent_at)
                    VALUES (?, ?, ?, 'APP', ?, 'QUEUED', NULL)
                    """, id(), tripId, guardianId, eventType);
            pushOutbox.enqueue(guardianId, eventType, "이동 안내", body,
                    "{\"type\":\"" + eventType + "\",\"tripId\":\"" + tripId + "\"}");
        }
    }

    private TripContext ownedActiveTrip(User user, String tripId) {
        if (!"ELDER".equals(user.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        List<TripContext> rows = db.query("""
                SELECT t.elder_id, t.planned_arrival_at, r.destination_lat, r.destination_lon
                FROM trips t JOIN elder_profiles e ON e.elder_id = t.elder_id
                JOIN route_plans r ON r.route_plan_id = t.route_plan_id
                WHERE t.trip_id = ? AND e.user_id = ? AND t.status IN ('ACTIVE', 'HELP_REQUESTED')
                """, (rs, n) -> new TripContext(rs.getString(1), instant(rs.getTimestamp(2)),
                rs.getDouble(3), rs.getDouble(4)), tripId, user.userId());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip is not active");
        if (!consented(rows.get(0).elderId(), "LOCATION")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return rows.get(0);
    }

    private boolean activeGuardian(String userId, String elderId) {
        return !db.query("SELECT link_id FROM guardian_links WHERE guardian_user_id = ? AND elder_id = ? AND status = 'ACTIVE'",
                (rs, n) -> rs.getString(1), userId, elderId).isEmpty() && consented(elderId, "CAREGIVER_SHARING");
    }

    private String elderIdForTrip(String tripId) {
        List<String> rows = db.query("SELECT elder_id FROM trips WHERE trip_id = ?", (rs, n) -> rs.getString(1), tripId);
        return rows.isEmpty() ? "" : rows.get(0);
    }

    private boolean consented(String elderId, String scope) {
        List<Boolean> rows = db.query("SELECT granted FROM consent_records WHERE elder_id = ? AND scope = ? ORDER BY recorded_at DESC, consent_id DESC LIMIT 1",
                (rs, n) -> rs.getBoolean(1), elderId, scope);
        return !rows.isEmpty() && rows.get(0);
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
    private static java.sql.Timestamp ts(Instant value) { return java.sql.Timestamp.from(value); }
    private static String id() { return UUID.randomUUID().toString(); }
    private record RouteInfo(int durationSeconds, String ownerId) {}
    private record TripContext(String elderId, Instant plannedArrival, double destinationLat, double destinationLon) {}
    private record NoResponseCandidate(String tripId, String elderId, double latitude, double longitude,
                                       double destinationLat, double destinationLon) {}
}
