package cz.poposkoc.radio.soundtouch;

import cz.poposkoc.radio.config.SoundTouchProperties;
import cz.poposkoc.radio.soundtouch.dto.NowPlaying;
import cz.poposkoc.radio.soundtouch.dto.VolumeStatus;
import cz.poposkoc.radio.state.PlayerState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SoundTouchEventListener {

    private static final Logger log = LoggerFactory.getLogger(SoundTouchEventListener.class);
    private static final String SUBPROTOCOL = "gabbo";
    private static final List<Long> BACKOFF_SECONDS = List.of(1L, 2L, 5L, 10L);

    private final SoundTouchProperties props;
    private final UpdatesDispatcher dispatcher;
    private final SoundTouchClient soundTouchClient;
    private final PlayerState playerState;
    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "soundtouch-ws-reconnect");
                t.setDaemon(true);
                return t;
            });
    private final AtomicReference<WebSocketSession> currentSession = new AtomicReference<>();
    private final AtomicInteger attempt = new AtomicInteger(0);
    private final AtomicReference<ScheduledFuture<?>> pendingReconnect = new AtomicReference<>();
    private volatile boolean stopping = false;

    public SoundTouchEventListener(SoundTouchProperties props,
                                   UpdatesDispatcher dispatcher,
                                   SoundTouchClient soundTouchClient,
                                   PlayerState playerState) {
        this.props = props;
        this.dispatcher = dispatcher;
        this.soundTouchClient = soundTouchClient;
        this.playerState = playerState;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        connect();
    }

    @PreDestroy
    public void stop() {
        stopping = true;
        ScheduledFuture<?> pending = pendingReconnect.getAndSet(null);
        if (pending != null) {
            pending.cancel(false);
        }
        WebSocketSession session = currentSession.getAndSet(null);
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.GOING_AWAY);
            } catch (Exception e) {
                log.debug("Error closing WS session on shutdown: {}", e.getMessage());
            }
        }
        scheduler.shutdownNow();
    }

    private void connect() {
        if (stopping) {
            return;
        }
        URI uri = URI.create("ws://" + props.host() + ":" + props.wsPort() + "/");
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setSecWebSocketProtocol(List.of(SUBPROTOCOL));
        log.info("Connecting to SoundTouch WS at {} (attempt {})", uri, attempt.get() + 1);
        client.execute(new Handler(), headers, uri).whenComplete((session, ex) -> {
            if (ex != null) {
                log.warn("WS connect failed: {}", ex.getMessage());
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (stopping) {
            return;
        }
        int i = Math.min(attempt.getAndIncrement(), BACKOFF_SECONDS.size() - 1);
        long delay = BACKOFF_SECONDS.get(i);
        log.info("Will retry WS connect in {}s", delay);
        ScheduledFuture<?> previous = pendingReconnect.getAndSet(
                scheduler.schedule(this::connect, delay, TimeUnit.SECONDS));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void onConnected(WebSocketSession session) {
        currentSession.set(session);
        attempt.set(0);
        log.info("WS connected: id={}, subprotocol={}", session.getId(), session.getAcceptedProtocol());
        resyncSnapshot();
    }

    private void resyncSnapshot() {
        try {
            VolumeStatus vol = soundTouchClient.volume();
            playerState.volumeChanged(vol.actualVolume(), vol.muteEnabled());
            NowPlaying np = soundTouchClient.nowPlaying();
            if (np.contentItem() != null && np.contentItem().location() != null) {
                playerState.contentItemFromSpeaker(np.contentItem());
            }
        } catch (RuntimeException e) {
            log.warn("Resync after WS connect failed: {}", e.getMessage());
        }
    }

    private void onClosed(WebSocketSession session, CloseStatus status) {
        currentSession.compareAndSet(session, null);
        if (stopping) {
            log.info("WS closed during shutdown: {}", status);
            return;
        }
        log.warn("WS closed: code={}, reason={}", status.getCode(), status.getReason());
        scheduleReconnect();
    }

    private void onTextFrame(WebSocketSession session, String payload) {
        log.debug("WS frame ({} bytes): {}", payload.length(), payload);
        dispatcher.dispatch(payload);
    }

    private class Handler extends TextWebSocketHandler {
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            onConnected(session);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            onClosed(session, status);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            onTextFrame(session, message.getPayload());
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.warn("WS transport error: {}", exception.getMessage());
        }
    }
}
