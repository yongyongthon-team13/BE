package com.yongyong.backend.account;

import com.yongyong.backend.account.HomeLocationDtos.Candidate;
import com.yongyong.backend.account.HomeLocationDtos.HomeLocation;
import com.yongyong.backend.account.HomeLocationDtos.SaveRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeLocationService {
    private final AccountService accounts;
    private final DataCipher cipher;
    private final JdbcTemplate db;
    private final TmapGeocodingClient geocoding;

    public List<Candidate> search(Jwt jwt, String address) {
        accounts.ownElderId(jwt);
        return geocoding.search(address.trim());
    }

    @Transactional(readOnly = true)
    public HomeLocation get(Jwt jwt) {
        String elderId = accounts.ownElderId(jwt);
        return db.queryForObject("""
                SELECT home_address_enc, home_lat_enc, home_lon_enc, home_location_verified_at
                FROM elder_profiles WHERE elder_id = ?
                """, (rs, row) -> new HomeLocation(cipher.decrypt(rs.getString(1)),
                decryptCoordinate(rs.getString(2)), decryptCoordinate(rs.getString(3)),
                instant(rs.getTimestamp(4))), elderId);
    }

    @Transactional
    public HomeLocation save(Jwt jwt, SaveRequest request) {
        String elderId = accounts.ownElderId(jwt);
        String address = request.address().trim();
        String detail = request.addressDetail() == null ? "" : request.addressDetail().trim();
        String fullAddress = detail.isEmpty() ? address : address + " " + detail;
        db.update("""
                UPDATE elder_profiles SET home_address_enc = ?, home_lat_enc = ?, home_lon_enc = ?,
                  home_location_verified_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6)
                WHERE elder_id = ?
                """, cipher.encrypt(fullAddress), cipher.encrypt(Double.toString(request.latitude())),
                cipher.encrypt(Double.toString(request.longitude())), elderId);
        return get(jwt);
    }

    private Double decryptCoordinate(String value) {
        String decrypted = cipher.decrypt(value);
        return decrypted == null ? null : Double.valueOf(decrypted);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
