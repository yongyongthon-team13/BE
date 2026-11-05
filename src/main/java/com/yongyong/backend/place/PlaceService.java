package com.yongyong.backend.place;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.account.AccountService.User;
import com.yongyong.backend.place.PlaceDtos.SearchResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class PlaceService {
    private final AccountService accountService;
    private final TmapPlaceClient tmapPlaceClient;

    public PlaceService(AccountService accountService, TmapPlaceClient tmapPlaceClient) {
        this.accountService = accountService;
        this.tmapPlaceClient = tmapPlaceClient;
    }

    public SearchResult search(Jwt jwt, String query, int limit) {
        User user = accountService.current(jwt);
        if (!"ELDER".equals(user.role()) && !"GUARDIAN".equals(user.role())) {
            throw new PlaceApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "장소 검색 권한이 없습니다.");
        }

        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2 || normalizedQuery.length() > 100) {
            throw new PlaceApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "검색어는 2자 이상 100자 이하로 입력해 주세요.");
        }
        if (limit < 1 || limit > 20) {
            throw new PlaceApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "limit은 1 이상 20 이하로 입력해 주세요.");
        }
        return tmapPlaceClient.search(normalizedQuery, limit);
    }
}
