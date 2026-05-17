package cz.poposkoc.radio.state;

import cz.poposkoc.radio.soundtouch.dto.ContentItem;
import cz.poposkoc.radio.stations.Station;
import cz.poposkoc.radio.stations.StationRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PlayerState {

    private final ApplicationEventPublisher publisher;
    private final StationRegistry registry;
    private final AtomicReference<PlayerStateSnapshot> snapshot =
            new AtomicReference<>(PlayerStateSnapshot.empty());

    public PlayerState(ApplicationEventPublisher publisher, StationRegistry registry) {
        this.publisher = publisher;
        this.registry = registry;
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

    public void contentItemFromSpeaker(ContentItem item) {
        Optional<Station> matched = matchToStation(item);
        update(prev -> new PlayerStateSnapshot(
                item.source(),
                matched.map(Station::id).orElse(null),
                matched.map(Station::name).orElseGet(item::itemName),
                prev.volume(),
                prev.muted(),
                "PLAY_STATE"
        ));
    }

    private Optional<Station> matchToStation(ContentItem item) {
        if (item.location() == null || item.location().isBlank()) {
            return Optional.empty();
        }
        return registry.all().stream()
                .filter(s -> item.location().equals(s.stream()) || item.location().equals(s.tunein()))
                .findFirst();
    }

    private void update(java.util.function.UnaryOperator<PlayerStateSnapshot> mutator) {
        PlayerStateSnapshot updated = snapshot.updateAndGet(mutator);
        publisher.publishEvent(new PlayerStateChanged(updated));
    }
}
