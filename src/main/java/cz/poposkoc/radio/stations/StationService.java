package cz.poposkoc.radio.stations;

import cz.poposkoc.radio.config.SoundTouchProperties;
import cz.poposkoc.radio.soundtouch.SoundTouchClient;
import cz.poposkoc.radio.soundtouch.dto.ContentItem;
import cz.poposkoc.radio.state.PlayerState;
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
    private final PlayerState playerState;
    private final SoundTouchProperties props;

    public StationService(StationRegistry registry,
                          SoundTouchClient client,
                          PlayerState playerState,
                          SoundTouchProperties props) {
        this.registry = registry;
        this.client = client;
        this.playerState = playerState;
        this.props = props;
    }

    /**
     * Play a station. Firmware 27.0.6 of the SoundTouch 30 removed direct-URL
     * {@code source="LOCAL_INTERNET_RADIO"} support for {@code /select}, but kept
     * it for stored presets that resolve via a JSON URL. We store the requested
     * station into our scratch preset slot (default: 6) and trigger PRESET_N.
     */
    public Station play(String stationId) {
        Station station = registry.findById(stationId)
                .orElseThrow(() -> new StationNotFoundException(stationId));

        ContentItem item = toCatalogContentItem(station);
        wakeIfStandby();
        client.storePreset(props.scratchPresetSlot(), item);
        client.playPreset(props.scratchPresetSlot());
        playerState.stationPlaying(station);
        log.info("Playing station {} ({}) via preset slot {}",
                station.id(), station.name(), props.scratchPresetSlot());
        return station;
    }

    /**
     * Pin a station into one of the speaker's 6 preset slots so the physical
     * remote / front-panel buttons play it. The preset's {@code location} points
     * at our station-catalog JSON endpoint.
     */
    public Station pin(String stationId, int slot) {
        Station station = registry.findById(stationId)
                .orElseThrow(() -> new StationNotFoundException(stationId));
        client.storePreset(slot, toCatalogContentItem(station));
        log.info("Pinned station {} ({}) to preset slot {}",
                station.id(), station.name(), slot);
        return station;
    }

    private ContentItem toCatalogContentItem(Station station) {
        // Short path (root-mapped) — observed: firmware 27.0.6 silently rejects
        // /api/stations/{id}/station.json (never fetches), but a short root-level
        // URL matches the documented working examples.
        String catalogUrl = props.catalogBaseUrl()
                + "/s/" + station.id() + ".json";
        return ContentItem.catalog(catalogUrl, station.name());
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
}
