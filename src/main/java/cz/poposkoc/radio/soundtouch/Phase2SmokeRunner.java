package cz.poposkoc.radio.soundtouch;

import cz.poposkoc.radio.soundtouch.dto.ContentItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * One-shot Phase-2 smoke runner. Activated by --spring.profiles.active=phase2-smoke.
 * Confirms the wiring all the way through: app → client → live speaker → audible audio.
 * Will be deleted (or repurposed) in later phases.
 */
@Component
@Profile("phase2-smoke")
class Phase2SmokeRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Phase2SmokeRunner.class);

    private final SoundTouchClient client;

    Phase2SmokeRunner(SoundTouchClient client) {
        this.client = client;
    }

    @Override
    public void run(String... args) throws InterruptedException {
        log.info("Phase 2 smoke: info() -> {}", client.info().name());

        if ("STANDBY".equalsIgnoreCase(client.nowPlaying().source())) {
            log.info("Phase 2 smoke: speaker is in STANDBY, sending POWER to wake");
            client.pressKey("POWER");
            Thread.sleep(1500);
        }

        log.info("Phase 2 smoke: selecting Vltava…");
        client.select(ContentItem.localInternetRadio(
                "http://icecast2.rozhlas.cz/vltava-mp3-128", "Vltava"));
        Thread.sleep(500);
        log.info("Phase 2 smoke: select OK. now_playing.source = {}",
                client.nowPlaying().source());
    }
}
