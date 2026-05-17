package cz.poposkoc.radio.soundtouch;

import cz.poposkoc.radio.soundtouch.dto.ContentItem;
import cz.poposkoc.radio.soundtouch.dto.ErrorResponse;
import cz.poposkoc.radio.soundtouch.dto.Info;
import cz.poposkoc.radio.soundtouch.dto.KeyCommand;
import cz.poposkoc.radio.soundtouch.dto.NowPlaying;
import cz.poposkoc.radio.soundtouch.dto.Sources;
import cz.poposkoc.radio.soundtouch.dto.VolumeCommand;
import cz.poposkoc.radio.soundtouch.dto.VolumeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.dataformat.xml.XmlMapper;

@Component
public class SoundTouchClient {

    private static final Logger log = LoggerFactory.getLogger(SoundTouchClient.class);
    private static final long KEY_PRESS_RELEASE_GAP_MS = 50L;
    private static final int MIN_VOLUME = 0;
    private static final int MAX_VOLUME = 100;

    private final RestClient http;
    private final XmlMapper xmlMapper;

    public SoundTouchClient(RestClient soundTouchRestClient, XmlMapper soundTouchXmlMapper) {
        this.http = soundTouchRestClient;
        this.xmlMapper = soundTouchXmlMapper;
    }

    public Info info() {
        return get("/info", Info.class);
    }

    public Sources sources() {
        return get("/sources", Sources.class);
    }

    public NowPlaying nowPlaying() {
        return get("/now_playing", NowPlaying.class);
    }

    public VolumeStatus volume() {
        return get("/volume", VolumeStatus.class);
    }

    public void select(ContentItem item) {
        postXml("/select", item);
    }

    public int setVolume(int level) {
        int clamped = Math.clamp(level, MIN_VOLUME, MAX_VOLUME);
        postXml("/volume", new VolumeCommand(clamped));
        return clamped;
    }

    public void pressKey(String key) {
        postXml("/key", KeyCommand.press(key));
        try {
            Thread.sleep(KEY_PRESS_RELEASE_GAP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SoundTouchException("interrupted between key press and release", e);
        }
        postXml("/key", KeyCommand.release(key));
    }

    private <T> T get(String path, Class<T> type) {
        byte[] body = http.get()
                .uri(path)
                .accept(MediaType.APPLICATION_XML)
                .retrieve()
                .body(byte[].class);
        return parse(body, type, "GET " + path);
    }

    private void postXml(String path, Object payload) {
        byte[] body = http.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_XML)
                .accept(MediaType.APPLICATION_XML)
                .body(payload)
                .retrieve()
                .body(byte[].class);
        verifyNotError(body, "POST " + path);
    }

    private <T> T parse(byte[] body, Class<T> type, String context) {
        verifyNotError(body, context);
        try {
            return xmlMapper.readValue(body, type);
        } catch (StreamReadException | MismatchedInputException e) {
            log.error("Failed to parse SoundTouch response for {}; body: {}", context, safeString(body));
            throw new SoundTouchException("Cannot parse response for " + context, e);
        }
    }

    private void verifyNotError(byte[] body, String context) {
        if (body == null || body.length == 0) {
            return;
        }
        String snippet = safeString(body);
        if (!snippet.contains("<errors")) {
            return;
        }
        try {
            ErrorResponse err = xmlMapper.readValue(body, ErrorResponse.class);
            throw new SoundTouchException("SoundTouch error on " + context + ": " + err, err);
        } catch (StreamReadException | MismatchedInputException e) {
            throw new SoundTouchException(
                    "SoundTouch error-shaped body on " + context + ": " + snippet, e);
        }
    }

    private static String safeString(byte[] body) {
        return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }
}
