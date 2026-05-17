package cz.poposkoc.radio.stations;

import cz.poposkoc.radio.config.StationsProperties;
import cz.poposkoc.radio.soundtouch.SoundTouchClient;
import cz.poposkoc.radio.soundtouch.dto.ContentItem;
import cz.poposkoc.radio.soundtouch.dto.NowPlaying;
import cz.poposkoc.radio.state.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationServiceTest {

    private SoundTouchClient client;
    private StationService service;

    @BeforeEach
    void setUp() {
        client = mock(SoundTouchClient.class);
        StationRegistry registry = new StationRegistry(new StationsProperties(List.of(
                new Station("vltava", "Vltava", "http://example.test/vltava", null, 1),
                new Station("fip", "FIP", null, "s15200", 2)
        )));
        service = new StationService(registry, client, mock(PlayerState.class));
    }

    @Test
    void playStreamStationSendsLocalInternetRadioContentItem() {
        when(client.nowPlaying()).thenReturn(awake("LOCAL_INTERNET_RADIO"));

        service.play("vltava");

        ArgumentCaptor<ContentItem> captor = ArgumentCaptor.forClass(ContentItem.class);
        verify(client).select(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo("LOCAL_INTERNET_RADIO");
        assertThat(captor.getValue().location()).isEqualTo("http://example.test/vltava");
        assertThat(captor.getValue().itemName()).isEqualTo("Vltava");
        verify(client, never()).pressKey(any());
    }

    @Test
    void playTuneInStationSendsTuneInContentItem() {
        when(client.nowPlaying()).thenReturn(awake("LOCAL_INTERNET_RADIO"));

        service.play("fip");

        ArgumentCaptor<ContentItem> captor = ArgumentCaptor.forClass(ContentItem.class);
        verify(client).select(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo("TUNEIN");
        assertThat(captor.getValue().location()).isEqualTo("s15200");
    }

    @Test
    void wakesSpeakerViaPowerKeyWhenStandbyBeforeSelecting() {
        when(client.nowPlaying()).thenReturn(awake("STANDBY"));

        service.play("vltava");

        var order = inOrder(client);
        order.verify(client).nowPlaying();
        order.verify(client).pressKey("POWER");
        order.verify(client).select(any(ContentItem.class));
    }

    @Test
    void unknownStationThrowsStationNotFoundException() {
        assertThatThrownBy(() -> service.play("nope"))
                .isInstanceOf(StationNotFoundException.class)
                .hasMessageContaining("nope");
        verify(client, never()).select(any());
    }

    private static NowPlaying awake(String source) {
        return new NowPlaying("deviceId", source, "", null);
    }
}
