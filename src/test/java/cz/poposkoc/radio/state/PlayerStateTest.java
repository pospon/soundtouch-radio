package cz.poposkoc.radio.state;

import cz.poposkoc.radio.config.StationsProperties;
import cz.poposkoc.radio.soundtouch.dto.ContentItem;
import cz.poposkoc.radio.stations.Station;
import cz.poposkoc.radio.stations.StationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PlayerStateTest {

    @Test
    void stationPlayingSetsStreamSourceAndPublishesEvent() {
        var publisher = mock(ApplicationEventPublisher.class);
        var state = newPlayerState(publisher);

        state.stationPlaying(new Station("vltava", "Vltava", "http://example.test/x", null, 1));

        PlayerStateSnapshot snap = state.snapshot();
        assertThat(snap.stationId()).isEqualTo("vltava");
        assertThat(snap.stationName()).isEqualTo("Vltava");
        assertThat(snap.source()).isEqualTo("LOCAL_INTERNET_RADIO");

        var captor = ArgumentCaptor.forClass(PlayerStateChanged.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().snapshot()).isEqualTo(snap);
    }

    @Test
    void stationPlayingSetsTuneInSourceForTuneInStation() {
        var state = newPlayerState(mock(ApplicationEventPublisher.class));
        state.stationPlaying(new Station("fip", "FIP", null, "s15200", 2));
        assertThat(state.snapshot().source()).isEqualTo("TUNEIN");
    }

    @Test
    void volumeChangedKeepsExistingStationAndPublishes() {
        var publisher = mock(ApplicationEventPublisher.class);
        var state = newPlayerState(publisher);
        state.stationPlaying(new Station("vltava", "Vltava", "http://example.test/x", null, 1));

        state.volumeChanged(42, false);

        var snap = state.snapshot();
        assertThat(snap.stationId()).isEqualTo("vltava");
        assertThat(snap.volume()).isEqualTo(42);
        assertThat(snap.muted()).isFalse();
        verify(publisher, times(2)).publishEvent(any(PlayerStateChanged.class));
    }

    @Test
    void contentItemFromSpeakerMatchesKnownStationByStream() {
        var state = newPlayerState(mock(ApplicationEventPublisher.class));

        state.contentItemFromSpeaker(new ContentItem(
                "LOCAL_INTERNET_RADIO", "http://example.test/x", "", true, "ignored speaker label"));

        assertThat(state.snapshot().stationId()).isEqualTo("vltava");
        assertThat(state.snapshot().stationName()).isEqualTo("Vltava");
        assertThat(state.snapshot().source()).isEqualTo("LOCAL_INTERNET_RADIO");
    }

    @Test
    void contentItemFromSpeakerFallsBackToSpeakerItemNameWhenNoMatch() {
        var state = newPlayerState(mock(ApplicationEventPublisher.class));

        state.contentItemFromSpeaker(new ContentItem(
                "LOCAL_INTERNET_RADIO", "http://example.test/unknown", "", true, "Some Radio"));

        assertThat(state.snapshot().stationId()).isNull();
        assertThat(state.snapshot().stationName()).isEqualTo("Some Radio");
    }

    private PlayerState newPlayerState(ApplicationEventPublisher publisher) {
        StationRegistry registry = new StationRegistry(new StationsProperties(List.of(
                new Station("vltava", "Vltava", "http://example.test/x", null, 1),
                new Station("fip", "FIP", null, "s15200", 2)
        )));
        return new PlayerState(publisher, registry);
    }

    private static <T> T any(Class<T> type) { return org.mockito.ArgumentMatchers.any(type); }
}
