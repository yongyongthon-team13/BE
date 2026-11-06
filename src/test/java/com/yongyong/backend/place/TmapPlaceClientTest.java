package com.yongyong.backend.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yongyong.backend.place.PlaceDtos.Place;
import com.yongyong.backend.place.PlaceDtos.SearchResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TmapPlaceClientTest {
    private MockRestServiceServer server;
    private TmapPlaceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://apis.openapi.sk.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TmapPlaceClient("test-key", builder.build());
    }

    @Test
    void mapsTmapPoiToNormalizedPlace() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/tmap/pois?")))
                .andExpect(header("appKey", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "searchPoiInfo": {
                            "totalCount": "1",
                            "pois": {"poi": {
                              "id": "poi-1",
                              "name": "용인세브란스병원",
                              "upperAddrName": "경기도",
                              "middleAddrName": "용인시 기흥구",
                              "roadName": "동백죽전대로",
                              "firstBuildNo": "23",
                              "upperBizName": "의료",
                              "frontLat": "37.27204695",
                              "frontLon": "127.14815280"
                            }}
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        SearchResult result = client.search("용인세브란스병원", 10);

        assertEquals(1, result.total());
        assertEquals(List.of(new Place("poi-1", "용인세브란스병원",
                "경기도 용인시 기흥구 동백죽전대로 23", "의료",
                new PlaceDtos.Location(37.27204695, 127.1481528))), result.places());
        server.verify();
    }

    @Test
    void rejectsSearchWhenProviderKeyIsMissing() {
        TmapPlaceClient unconfiguredClient = new TmapPlaceClient(" ", RestClient.builder().build());

        PlaceApiException exception = assertThrows(PlaceApiException.class,
                () -> unconfiguredClient.search("병원", 10));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
        assertEquals("PROVIDER_NOT_CONFIGURED", exception.code());
    }
}
