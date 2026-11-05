package com.yongyong.backend.place;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yongyong.backend.account.PhoneAuthService;
import com.yongyong.backend.account.SmsSender;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:account-test.properties")
@Transactional
class TmapPlaceClientLiveTest {
    @Autowired MockMvc mvc;
    @Autowired PhoneAuthService phoneAuth;
    @MockitoBean SmsSender sms;
    private final AtomicReference<String> sentMessage = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        when(sms.available()).thenReturn(true);
        doAnswer(invocation -> {
            sentMessage.set(invocation.getArgument(1));
            return null;
        }).when(sms).send(anyString(), anyString());
    }

    @Test
    @EnabledIfSystemProperty(named = "runLiveTmapTests", matches = "true")
    @EnabledIfEnvironmentVariable(named = "TMAP_APP_KEY", matches = ".+")
    void authenticatedPlacesEndpointSearchesRealTmapPoiService() throws Exception {
        phoneAuth.requestCode("01012345678");
        String code = sentMessage.get().replaceAll(".*인증번호 ([0-9]{6}).*", "$1");
        PhoneAuthService.LoginResult login = phoneAuth.login("01012345678", code, "ELDER");

        mvc.perform(get("/api/v1/places")
                        .queryParam("q", "용인세브란스병원")
                        .queryParam("limit", "3")
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places").isArray())
                .andExpect(jsonPath("$.data.places[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data.places.length()")
                        .value(org.hamcrest.Matchers.lessThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.places[0].location.latitude").isNumber())
                .andExpect(jsonPath("$.data.places[0].location.longitude").isNumber());
    }
}
