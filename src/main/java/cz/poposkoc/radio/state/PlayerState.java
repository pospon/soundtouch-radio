package cz.poposkoc.radio.state;

import cz.poposkoc.radio.stations.Station;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class PlayerState {

    private final ApplicationEventPublisher publisher;
    private final AtomicReference<PlayerStateSnapshot> snapshot =
            new AtomicReference<>(PlayerStateSnapshot.empty());

    public PlayerState(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public PlayerStateSnapshot snapshot() {
        return snapshot.get();
    }

    public void stationPlaying(Station station) {
        update(prev -> new PlayerStateSnapshot(
                station.stream() != null && !station.stream().isBlank()
                        ? "LOCAL_INTERNET_RADIO" : "TUNEIN",
                station.id(),
                station.name(),
                prev.volume(),
                prev.muted(),
                "PLAY_STATE"
        ));
    }

    public void volumeChanged(int volume, boolean muted) {
        update(prev -> new PlayerStateSnapshot(
                prev.source(), prev.stationId(), prev.stationName(),
                volume, muted,
                prev.playState()
        ));
    }

    private void update(java.util.function.UnaryOperator<PlayerStateSnapshot> mutator) {
        PlayerStateSnapshot updated = snapshot.updateAndGet(mutator);
        publisher.publishEvent(new PlayerStateChanged(updated));
    }
}
