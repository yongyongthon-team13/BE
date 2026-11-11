package com.yongyong.backend.route;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.api.ApiEnvelope;
import com.yongyong.backend.route.RouteDtos.Point;
import com.yongyong.backend.route.RouteDtos.RoutePlan;
import com.yongyong.backend.route.RouteDtos.SearchRequest;
import com.yongyong.backend.route.RouteDtos.SearchResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class RoutePlanService {
    private final AccountService accounts;
    private final JdbcTemplate db;
    private final TmapWalkingClient walkingClient;
    private final TmapTransitClient transitClient;
    private final GbisArrivalClient arrivalClient;
    private final ObjectMapper mapper;

    public RoutePlanService(AccountService accounts, JdbcTemplate db,
                            TmapWalkingClient walkingClient, TmapTransitClient transitClient,
                            GbisArrivalClient arrivalClient, ObjectMapper mapper) {
        this.accounts = accounts;
        this.db = db;
        this.walkingClient = walkingClient;
        this.transitClient = transitClient;
        this.arrivalClient = arrivalClient;
        this.mapper = mapper;
    }

    @Transactional
    public SearchResult search(Jwt jwt, SearchRequest request) {
        var user = accounts.current(jwt);
        if (!List.of("ELDER", "GUARDIAN").contains(user.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!List.of("WALK", "TRANSIT").contains(request.mode()))
            throw new RouteApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "mode은 WALK 또는 TRANSIT이어야 합니다.");
        Point origin = request.origin();
        Point destination = request.destination();
        if ("TRANSIT".equals(request.mode())) {
            List<TmapTransitClient.TransitAlternative> alternatives = transitClient.search(origin, destination,
                    request.count() == null ? 3 : request.count());
            Instant now = Instant.now();
            List<RoutePlan> plans = new java.util.ArrayList<>();
            for (TmapTransitClient.TransitAlternative alternative : alternatives) {
                RoutePlan plan = new RoutePlan(UUID.randomUUID().toString(), "TMAP_TRANSIT", "TRANSIT", now, null,
                        new RouteDtos.Summary(alternative.durationSeconds(), alternative.distanceMeters(),
                                alternative.walkingSeconds(), alternative.transferCount(), alternative.fareWon()),
                        alternative.legs(), List.of());
                persist(user.userId(), origin, destination, plan, now);
                plans.add(withArrivals(plan, arrivalClient.refresh(plan)));
            }
            return new SearchResult(now, null, List.copyOf(plans));
        }
        var result = walkingClient.search(origin, destination);
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        RoutePlan plan = new RoutePlan(id, "TMAP_WALK", "WALK", now, null,
                new RouteDtos.Summary(result.durationSeconds(), result.distanceMeters(), result.durationSeconds(), 0, 0), result.legs(), List.of());
        persist(user.userId(), origin, destination, plan, now);
        return new SearchResult(now, null, List.of(plan));
    }

    private void persist(String userId, Point origin, Point destination, RoutePlan plan, Instant now) {
        String json;
        try { json = mapper.writeValueAsString(plan); }
        catch (JacksonException exception) { throw new IllegalStateException("Could not serialize normalized route", exception); }
        db.update("""
                INSERT INTO route_plans(route_plan_id, owner_user_id, mode, provider,
                  origin_name, origin_lat, origin_lon, destination_name, destination_lat, destination_lon,
                  duration_seconds, distance_meters, fetched_at, details_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, plan.routePlanId(), userId, plan.mode(), plan.provider(), origin.name(), origin.latitude(), origin.longitude(),
                destination.name(), destination.latitude(), destination.longitude(),
                plan.summary().durationSeconds(), plan.summary().distanceMeters(), java.sql.Timestamp.from(now), json);
    }

    public RoutePlan get(Jwt jwt, String routePlanId, boolean refreshArrivals) {
        var user = accounts.current(jwt);
        List<String> rows = db.query("SELECT details_json FROM route_plans WHERE route_plan_id = ? AND owner_user_id = ?",
                (rs, n) -> rs.getString(1), routePlanId, user.userId());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Route plan not found");
        try {
            RoutePlan plan = mapper.readValue(rows.get(0), RoutePlan.class);
            if (refreshArrivals && "TRANSIT".equals(plan.mode())) return withArrivals(plan, arrivalClient.refresh(plan));
            if ("TRANSIT".equals(plan.mode())) return withArrivals(plan, arrivalClient.unavailable(plan));
            return plan;
        }
        catch (JacksonException exception) { throw new IllegalStateException("Stored route plan is invalid", exception); }
    }

    public void requireOwned(Jwt jwt, String routePlanId) {
        String userId = accounts.current(jwt).userId();
        if (db.query("SELECT route_plan_id FROM route_plans WHERE route_plan_id = ? AND owner_user_id = ?",
                (rs, n) -> rs.getString(1), routePlanId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Route plan not found");
        }
    }

    private static RoutePlan withArrivals(RoutePlan plan, List<RouteDtos.StopArrival> arrivals) {
        return new RoutePlan(plan.routePlanId(), plan.provider(), plan.mode(), plan.fetchedAt(), plan.expiresAt(),
                plan.summary(), plan.legs(), arrivals);
    }
}
