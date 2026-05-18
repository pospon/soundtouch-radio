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

    private static final java.util.regex.Pattern CATALOG_URL =
            java.util.regex.Pattern.compile("/(?:s|api/stations)/([^/]+?)(?:/station)?\\.json");

    private Optional<Station> matchToStation(ContentItem item) {
        String location = item.location();
        if (location == null || location.isBlank()) {
            return Optional.empty();
        }
        var m = CATALOG_URL.matcher(location);
        if (m.find()) {
            Optional<Station> byId = registry.findById(m.group(1));
            if (byId.isPresent()) {
                return byId;
            }
        }
        // Raw-stream form (legacy; old presets, external app activity)
        return registry.all().stream()
                .filter(s -> location.equals(s.stream()) || location.equals(s.tunein()))
                .findFirst();
    }

    private void update(java.util.function.UnaryOperator<PlayerStateSnapshot> mutator) {
        PlayerStateSnapshot updated = snapshot.updateAndGet(mutator);
        publisher.publishEvent(new PlayerStateChanged(updated));
    }
}
