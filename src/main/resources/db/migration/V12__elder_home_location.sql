ALTER TABLE elder_profiles
  ADD COLUMN home_lat_enc TEXT NULL;
ALTER TABLE elder_profiles
  ADD COLUMN home_lon_enc TEXT NULL;
ALTER TABLE elder_profiles
  ADD COLUMN home_location_verified_at TIMESTAMP(6) NULL;
