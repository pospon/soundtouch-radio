package cz.poposkoc.radio.state;

import cz.poposkoc.radio.stations.Station;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PlayerStateTest {

    @Test
    void stationPlayingSetsStreamSourceAndPublishesEvent() {
        var publisher = mock(ApplicationEventPublisher.class);
        var state = new PlayerState(publisher);

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
        var state = new PlayerState(mock(ApplicationEventPublisher.class));
        state.stationPlaying(new Station("fip", "FIP", null, "s15200", 2));
        assertThat(state.snapshot().source()).isEqualTo("TUNEIN");
    }

    @Test
    void volumeChangedKeepsExistingStationAndPublishes() {
        var publisher = mock(ApplicationEventPublisher.class);
        var state = new PlayerState(publisher);
        state.stationPlaying(new Station("vltava", "Vltava", "http://example.test/x", null, 1));

        state.volumeChanged(42, false);

        var snap = state.snapshot();
        assertThat(snap.stationId()).isEqualTo("vltava");
        assertThat(snap.volume()).isEqualTo(42);
        assertThat(snap.muted()).isFalse();
        verify(publisher, times(2)).publishEvent(any(PlayerStateChanged.class));
    }

    private static <T> T any(Class<T> type) { return org.mockito.ArgumentMatchers.any(type); }
}
