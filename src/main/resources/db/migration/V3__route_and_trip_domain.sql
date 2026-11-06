CREATE TABLE route_plans (
  route_plan_id CHAR(36) PRIMARY KEY,
  owner_user_id CHAR(36) NOT NULL,
  mode VARCHAR(20) NOT NULL,
  provider VARCHAR(40) NOT NULL,
  origin_name VARCHAR(255) NOT NULL,
  origin_lat DOUBLE NOT NULL,
  origin_lon DOUBLE NOT NULL,
  destination_name VARCHAR(255) NOT NULL,
  destination_lat DOUBLE NOT NULL,
  destination_lon DOUBLE NOT NULL,
  duration_seconds INT NOT NULL,
  distance_meters INT NOT NULL,
  fetched_at TIMESTAMP(6) NOT NULL,
  details_json TEXT NOT NULL,
  CONSTRAINT fk_route_plan_owner FOREIGN KEY (owner_user_id) REFERENCES app_users(user_id),
  INDEX idx_route_plan_owner (owner_user_id, fetched_at)
);

CREATE TABLE trips (
  trip_id CHAR(36) PRIMARY KEY,
  route_plan_id CHAR(36) NOT NULL,
  elder_id CHAR(36) NOT NULL,
  status VARCHAR(30) NOT NULL,
  planned_departure_at TIMESTAMP(6) NOT NULL,
  planned_arrival_at TIMESTAMP(6) NOT NULL,
  estimated_arrival_at TIMESTAMP(6) NOT NULL,
  delay_since TIMESTAMP(6) NULL,
  started_at TIMESTAMP(6) NOT NULL,
  ended_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_trip_route_plan FOREIGN KEY (route_plan_id) REFERENCES route_plans(route_plan_id),
  CONSTRAINT fk_trip_elder FOREIGN KEY (elder_id) REFERENCES elder_profiles(elder_id),
  INDEX idx_trip_elder_status (elder_id, status),
  INDEX idx_trip_delay (status, delay_since)
);

CREATE TABLE trip_current_locations (
  trip_id CHAR(36) PRIMARY KEY,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  accuracy_meters INT NOT NULL,
  recorded_at TIMESTAMP(6) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_trip_location_trip FOREIGN KEY (trip_id) REFERENCES trips(trip_id),
  INDEX idx_trip_location_expiry (expires_at)
);

CREATE TABLE trip_events (
  event_id CHAR(36) PRIMARY KEY,
  trip_id CHAR(36) NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  detail_code VARCHAR(80) NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_trip_event_trip FOREIGN KEY (trip_id) REFERENCES trips(trip_id),
  INDEX idx_trip_event_trip (trip_id, occurred_at)
);

CREATE TABLE guardian_contact_actions (
  contact_action_id CHAR(36) PRIMARY KEY,
  trip_id CHAR(36) NOT NULL,
  guardian_user_id CHAR(36) NOT NULL,
  action_type VARCHAR(40) NOT NULL,
  outcome VARCHAR(40) NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_contact_trip FOREIGN KEY (trip_id) REFERENCES trips(trip_id),
  CONSTRAINT fk_contact_guardian FOREIGN KEY (guardian_user_id) REFERENCES app_users(user_id),
  INDEX idx_contact_trip (trip_id, occurred_at)
);
