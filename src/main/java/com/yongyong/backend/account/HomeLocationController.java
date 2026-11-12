package com.yongyong.backend.account;

import com.yongyong.backend.account.HomeLocationDtos.HomeLocation;
import com.yongyong.backend.account.HomeLocationDtos.SaveRequest;
import com.yongyong.backend.account.HomeLocationDtos.SearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/elder-profiles/me/home-location")
@SecurityRequirement(name = "bearerAuth")
public class HomeLocationController {
    public record AddressSearchRequest(@NotBlank @Size(max = 500) String address) {}

    private final HomeLocationService homeLocations;

    public HomeLocationController(HomeLocationService homeLocations) {
        this.homeLocations = homeLocations;
    }

    @PostMapping("/candidates")
    @Operation(summary = "집 주소를 TMAP으로 검색해 좌표 후보를 조회합니다.",
            description = "상세 주소(동·호수)는 보내지 말고, 후보 위치를 앱에서 확인한 뒤 저장하세요.")
    public SearchResponse search(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressSearchRequest request) {
        return new SearchResponse(homeLocations.search(jwt, request.address()));
    }

    @GetMapping
    @Operation(summary = "로그인한 어르신의 확인된 집 위치를 조회합니다.")
    public HomeLocation get(@AuthenticationPrincipal Jwt jwt) {
        return homeLocations.get(jwt);
    }

    @PutMapping
    @Operation(summary = "확인한 집 주소와 좌표를 저장합니다.",
            description = "TMAP 좌표 후보를 사용자가 확인한 뒤 저장합니다. 상세 주소는 TMAP에 전달하지 않습니다.")
    public HomeLocation save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveRequest request) {
        return homeLocations.save(jwt, request);
    }
}
