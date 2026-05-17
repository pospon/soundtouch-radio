package cz.poposkoc.radio.web;

import cz.poposkoc.radio.soundtouch.SoundTouchClient;
import cz.poposkoc.radio.soundtouch.SupportedKeys;
import cz.poposkoc.radio.soundtouch.dto.VolumeStatus;
import cz.poposkoc.radio.state.PlayerState;
import cz.poposkoc.radio.state.PlayerStateSnapshot;
import cz.poposkoc.radio.stations.StationRegistry;
import cz.poposkoc.radio.stations.StationService;
import cz.poposkoc.radio.web.dto.StationView;
import cz.poposkoc.radio.web.dto.VolumeRequest;
import cz.poposkoc.radio.web.dto.VolumeView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api")
class RadioController {

    private final StationRegistry registry;
    private final StationService stationService;
    private final SoundTouchClient client;
    private final PlayerState playerState;

    RadioController(StationRegistry registry, StationService stationService, SoundTouchClient client, PlayerState playerState) {
        this.registry = registry;
        this.stationService = stationService;
        this.client = client;
        this.playerState = playerState;
    }

    @GetMapping("/stations")
    List<StationView> stations() {
        return registry.all().stream().map(StationView::from).toList();
    }

    @GetMapping("/now-playing")
    PlayerStateSnapshot nowPlaying() {
        return playerState.snapshot();
    }

    @PostMapping("/play/{stationId}")
    ResponseEntity<Void> play(@PathVariable String stationId) {
        stationService.play(stationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/key/{key}")
    ResponseEntity<Void> key(@PathVariable String key) {
        String normalized = key.toUpperCase();
        if (!SupportedKeys.ALL.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported key: " + key);
        }
        client.pressKey(normalized);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/volume")
    VolumeView getVolume() {
        VolumeStatus status = client.volume();
        playerState.volumeChanged(status.actualVolume(), status.muteEnabled());
        return new VolumeView(status.actualVolume(), status.muteEnabled());
    }

    @PutMapping("/volume")
    ResponseEntity<Void> setVolume(@RequestBody VolumeRequest request) {
        int applied = client.setVolume(request.volume());
        playerState.volumeChanged(applied, false);
        return ResponseEntity.noContent().build();
    }
}
