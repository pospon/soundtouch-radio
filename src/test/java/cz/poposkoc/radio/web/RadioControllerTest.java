package cz.poposkoc.radio.web;

import cz.poposkoc.radio.config.StationsProperties;
import cz.poposkoc.radio.soundtouch.SoundTouchClient;
import cz.poposkoc.radio.soundtouch.SoundTouchException;
import cz.poposkoc.radio.soundtouch.dto.VolumeStatus;
import cz.poposkoc.radio.stations.Station;
import cz.poposkoc.radio.stations.StationNotFoundException;
import cz.poposkoc.radio.stations.StationRegistry;
import cz.poposkoc.radio.stations.StationService;
import cz.poposkoc.radio.web.dto.VolumeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@org.springframework.context.annotation.Import({
        RadioController.class,
        HealthController.class,
        RadioExceptionHandler.class,
        RadioControllerTest.TestStations.class
})
class RadioControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean StationService stationService;
    @MockitoBean SoundTouchClient client;

    @Test
    void listsStationsWithButtonField() throws Exception {
        mvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("vltava"))
                .andExpect(jsonPath("$[0].name").value("Vltava"))
                .andExpect(jsonPath("$[0].button").value(1))
                .andExpect(jsonPath("$[1].id").value("fip"))
                .andExpect(jsonPath("$[1].button").value(2));
    }

    @Test
    void playStationReturns204AndCallsService() throws Exception {
        mvc.perform(post("/api/play/vltava"))
                .andExpect(status().isNoContent());
        verify(stationService).play("vltava");
    }

    @Test
    void playUnknownStationReturns404() throws Exception {
        doThrow(new StationNotFoundException("nope"))
                .when(stationService).play("nope");
        mvc.perform(post("/api/play/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown station: nope"));
    }

    @Test
    void keyForwardsToClientAndIsCaseInsensitive() throws Exception {
        mvc.perform(post("/api/key/play_pause"))
                .andExpect(status().isNoContent());
        verify(client).pressKey("PLAY_PAUSE");
    }

    @Test
    void keyOutsideAllowlistReturns400AndDoesNotCallClient() throws Exception {
        mvc.perform(post("/api/key/DROP_TABLES"))
                .andExpect(status().isBadRequest());
        verify(client, never()).pressKey(any());
    }

    @Test
    void getVolumeReturnsActualAndMute() throws Exception {
        when(client.volume()).thenReturn(new VolumeStatus(42, 39, true));
        mvc.perform(get("/api/volume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volume").value(39))
                .andExpect(jsonPath("$.muted").value(true));
    }

    @Test
    void putVolumePassesValueThrough() throws Exception {
        mvc.perform(put("/api/volume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VolumeRequest(30))))
                .andExpect(status().isNoContent());
        verify(client).setVolume(30);
    }

    @Test
    void healthReturnsUpWhenInfoSucceeds() throws Exception {
        when(client.info()).thenReturn(new cz.poposkoc.radio.soundtouch.dto.Info(
                "7C3866495B48", "SoundTouch oobýval30", "SoundTouch 30", List.of()));
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.deviceName").value("SoundTouch oobýval30"))
                .andExpect(jsonPath("$.deviceId").value("7C3866495B48"));
    }

    @Test
    void healthReturnsDownWhenClientThrows() throws Exception {
        doThrow(new SoundTouchException("offline", new RuntimeException("connect timeout")))
                .when(client).info();
        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("oobýval"))));
    }

    @Configuration
    static class TestStations {
        @Bean
        StationRegistry stationRegistry() {
            return new StationRegistry(new StationsProperties(List.of(
                    new Station("vltava", "Vltava", "http://example.test/vltava", null, 1),
                    new Station("fip", "FIP", null, "s15200", 2)
            )));
        }
    }
}
