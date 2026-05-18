package cz.poposkoc.radio.web;

import cz.poposkoc.radio.stations.Station;
import cz.poposkoc.radio.stations.StationNotFoundException;
import cz.poposkoc.radio.stations.StationRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Root-path short URLs for the SoundTouch firmware's LOCAL_INTERNET_RADIO loader.
 * Kept outside `/api/` because empirical observation (2026-05-18, firmware 27.0.6)
 * suggests the speaker's HTTP fetcher silently rejects deep paths — it accepted
 * a preset whose location was `http://10.0.0.221:8080/api/stations/vltava/station.json`
 * but never opened a connection to fetch it.
 *
 * The request emitted here mirrors what python's `http.server` returns — minimal
 * headers, no charset suffix, no XSS/cache extras.
 */
@RestController
class StationCatalogController {

    private final StationRegistry registry;

    StationCatalogController(StationRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/s/{stationId}.json")
    ResponseEntity<byte[]> stationCatalog(@PathVariable String stationId) {
        Station station = registry.findById(stationId)
                .orElseThrow(() -> new StationNotFoundException(stationId));
        String streamUrl = station.stream() != null && !station.stream().isBlank()
                ? station.stream()
                : station.tunein();
        byte[] body = buildJson(station.name(), streamUrl);
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(body);
    }

    private static byte[] buildJson(String name, String streamUrl) {
        String escapedName = name.replace("\\", "\\\\").replace("\"", "\\\"");
        String escapedUrl = streamUrl.replace("\\", "\\\\").replace("\"", "\\\"");
        String json = "{\"audio\":{\"hasPlaylist\":true,\"isRealtime\":true,\"streamUrl\":\""
                + escapedUrl + "\"},\"name\":\""
                + escapedName + "\",\"streamType\":\"liveRadio\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
