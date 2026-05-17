package cz.poposkoc.radio.web;

import cz.poposkoc.radio.soundtouch.SoundTouchClient;
import cz.poposkoc.radio.soundtouch.dto.Info;
import cz.poposkoc.radio.web.dto.HealthView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RestController
@RequestMapping("/api")
class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final SoundTouchClient client;

    HealthController(SoundTouchClient client) {
        this.client = client;
    }

    @GetMapping("/health")
    ResponseEntity<HealthView> health() {
        try {
            Info info = client.info();
            return ResponseEntity.ok(HealthView.up(info.name(), info.deviceId()));
        } catch (RuntimeException e) {
            log.warn("Health probe failed: {}", e.getMessage());
            return ResponseEntity.status(SERVICE_UNAVAILABLE).body(HealthView.down());
        }
    }
}
