package cz.poposkoc.radio.stations;

import cz.poposkoc.radio.config.SoundTouchProperties;
import cz.poposkoc.radio.config.StationsProperties;
import cz.poposkoc.radio.soundtouch.SoundTouchClient;
import cz.poposkoc.radio.soundtouch.dto.ContentItem;
import cz.poposkoc.radio.soundtouch.dto.NowPlaying;
import cz.poposkoc.radio.state.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationServiceTest {

    private static final int SCRATCH_SLOT = 6;
    private static final String CATALOG_BASE = "http://catalog.test:8080";

    private SoundTouchClient client;
    private StationService service;

    @BeforeEach
    void setUp() {
        client = mock(SoundTouchClient.class);
        StationRegistry registry = new StationRegistry(new StationsProperties(List.of(
                new Station("vltava", "Vltava", "http://example.test/vltava", null, 1),
                new Station("fip", "FIP", null, "s15200", 2)
        )));
        var props = new SoundTouchProperties(
                "10.0.0.148", 8090, 8080,
                Duration.ofSeconds(2), Duration.ofSeconds(5),
                CATALOG_BASE, SCRATCH_SLOT);
        service = new StationService(registry, client, mock(PlayerState.class), props);
    }

    @Test
    void playStoresStationInScratchPresetThenTriggersPlayPreset() {
        when(client.nowPlaying()).thenReturn(awake("LOCAL_INTERNET_RADIO"));

        service.play("vltava");

        ArgumentCaptor<ContentItem> captor = ArgumentCaptor.forClass(ContentItem.class);
        verify(client).storePreset(eq(SCRATCH_SLOT), captor.capture());
        verify(client).playPreset(SCRATCH_SLOT);

        ContentItem item = captor.getValue();
        assertThat(item.source()).isEqualTo("LOCAL_INTERNET_RADIO");
        assertThat(item.type()).isEqualTo("stationurl");
        assertThat(item.location())
                .as("location must point at our catalog JSON, not the raw stream")
                .isEqualTo(CATALOG_BASE + "/s/vltava.json");
        assertThat(item.itemName()).isEqualTo("Vltava");
    }

    @Test
    void playWorksForTuneInStationsToo() {
        when(client.nowPlaying()).thenReturn(awake("LOCAL_INTERNET_RADIO"));

        service.play("fip");

        ArgumentCaptor<ContentItem> captor = ArgumentCaptor.forClass(ContentItem.class);
        verify(client).storePreset(eq(SCRATCH_SLOT), captor.capture());
        assertThat(captor.getValue().location())
                .isEqualTo(CATALOG_BASE + "/s/fip.json");
    }

    @Test
    void wakesSpeakerViaPowerKeyWhenStandbyBeforePlay() {
        when(client.nowPlaying()).thenReturn(awake("STANDBY"));

        service.play("vltava");

        var order = inOrder(client);
        order.verify(client).nowPlaying();
        order.verify(client).pressKey("POWER");
        order.verify(client).storePreset(eq(SCRATCH_SLOT), any(ContentItem.class));
        order.verify(client).playPreset(SCRATCH_SLOT);
    }

    @Test
    void unknownStationThrowsStationNotFoundException() {
        assertThatThrownBy(() -> service.play("nope"))
                .isInstanceOf(StationNotFoundException.class)
                .hasMessageContaining("nope");
        verify(client, never()).storePreset(anyInt(), any());
        verify(client, never()).playPreset(anyInt());
    }

    @Test
    void pinStoresStationAtRequestedSlotWithoutPressingPreset() {
        service.pin("vltava", 1);
        ArgumentCaptor<ContentItem> captor = ArgumentCaptor.forClass(ContentItem.class);
        verify(client).storePreset(eq(1), captor.capture());
        verify(client, never()).playPreset(anyInt());
        assertThat(captor.getValue().location())
                .isEqualTo(CATALOG_BASE + "/s/vltava.json");
    }

    private static NowPlaying awake(String source) {
        return new NowPlaying("deviceId", source, "", null);
    }

    private static int eq(int value) { return org.mockito.ArgumentMatchers.eq(value); }
}
