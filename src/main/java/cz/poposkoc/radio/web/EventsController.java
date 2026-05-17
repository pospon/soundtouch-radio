package cz.poposkoc.radio.web;

import cz.poposkoc.radio.state.PlayerState;
import cz.poposkoc.radio.state.PlayerStateChanged;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api")
class EventsController {

    private static final Logger log = LoggerFactory.getLogger(EventsController.class);
    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final PlayerState playerState;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    EventsController(PlayerState playerState) {
        this.playerState = playerState;
    }

    @GetMapping(path = "/events", produces = "text/event-stream")
    SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(t -> emitters.remove(emitter));
        emitters.add(emitter);
        sendOrDrop(emitter, "state", playerState.snapshot());
        return emitter;
    }

    @EventListener
    void onPlayerStateChanged(PlayerStateChanged event) {
        for (SseEmitter emitter : emitters) {
            sendOrDrop(emitter, "state", event.snapshot());
        }
    }

    @PreDestroy
    void closeAllOnShutdown() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // emitter already in a closed state — fine
            }
        }
        emitters.clear();
    }

    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("hb"));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    private void sendOrDrop(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping SSE client: {}", e.getMessage());
            emitters.remove(emitter);
        }
    }
}
