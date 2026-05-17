package cz.poposkoc.radio.stations;

import cz.poposkoc.radio.soundtouch.SoundTouchClient;
import cz.poposkoc.radio.soundtouch.dto.ContentItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StationService {

    private static final Logger log = LoggerFactory.getLogger(StationService.class);
    private static final String STANDBY = "STANDBY";
    private static final long WAKE_WAIT_MS = 1500L;

    private final StationRegistry registry;
    private final SoundTouchClient client;

    public StationService(StationRegistry registry, SoundTouchClient client) {
        this.registry = registry;
        this.client = client;
    }

    public Station play(String stationId) {
        Station station = registry.findById(stationId)
                .orElseThrow(() -> new StationNotFoundException(stationId));

        wakeIfStandby();
        client.select(toContentItem(station));
        log.info("Playing station {} ({})", station.id(), station.name());
        return station;
    }

    private void wakeIfStandby() {
        String source = client.nowPlaying().source();
        if (!STANDBY.equalsIgnoreCase(source)) {
            return;
        }
        log.info("Speaker is in STANDBY; sending POWER to wake");
        client.pressKey("POWER");
        try {
            Thread.sleep(WAKE_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for speaker to wake", e);
        }
    }

    private static ContentItem toContentItem(Station station) {
        if (station.stream() != null && !station.stream().isBlank()) {
            return ContentItem.localInternetRadio(station.stream(), station.name());
        }
        return ContentItem.tuneIn(station.tunein(), station.name());
    }
}
