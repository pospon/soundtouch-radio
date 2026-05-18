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
import org.springframework.web.client.HttpStatusCodeException;
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

    /**
     * Store a ContentItem as a preset on slots 1..6. New-style flow on firmware 27.0.6:
     * speaker stores the item locally and resolves `location` (a JSON URL we host) when
     * the preset is played via {@link #pressKey "PRESET_N"} or the physical remote.
     */
    public void storePreset(int slot, ContentItem item) {
        if (slot < 1 || slot > 6) {
            throw new IllegalArgumentException("Preset slot must be 1..6, got " + slot);
        }
        String inner = serialize(item);
        String body = "<preset id=\"" + slot + "\">" + inner + "</preset>";
        postRawXml("/storePreset", body);
    }

    public void playPreset(int slot) {
        if (slot < 1 || slot > 6) {
            throw new IllegalArgumentException("Preset slot must be 1..6, got " + slot);
        }
        pressKey("PRESET_" + slot);
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
        byte[] body = exchange(() -> http.get()
                .uri(path)
                .accept(MediaType.APPLICATION_XML)
                .retrieve()
                .body(byte[].class), "GET " + path);
        return parse(body, type, "GET " + path);
    }

    private static final MediaType XML_UTF8 =
            new MediaType("application", "xml", java.nio.charset.StandardCharsets.UTF_8);

    private void postXml(String path, Object payload) {
        // Serialize to UTF-8 bytes ourselves so non-ASCII (e.g. Č in itemName) survives
        // the trip — Spring's default XML converter would emit ISO-8859-1.
        byte[] xml = serialize(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        postRawXmlBytes(path, xml);
    }

    private void postRawXml(String path, String rawXml) {
        postRawXmlBytes(path, rawXml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void postRawXmlBytes(String path, byte[] xmlUtf8) {
        byte[] body = exchange(() -> http.post()
                .uri(path)
                .contentType(XML_UTF8)
                .accept(MediaType.APPLICATION_XML)
                .body(xmlUtf8)
                .retrieve()
                .body(byte[].class), "POST " + path);
        verifyNotError(body, "POST " + path);
    }

    private byte[] exchange(java.util.function.Supplier<byte[]> call, String context) {
        try {
            return call.get();
        } catch (HttpStatusCodeException e) {
            // Speaker sometimes returns HTTP 500 with an <errors> body. Translate.
            byte[] errBody = e.getResponseBodyAsByteArray();
            if (errBody != null && errBody.length > 0) {
                verifyNotError(errBody, context);
            }
            throw new SoundTouchException(
                    "SoundTouch " + context + " failed with HTTP " + e.getStatusCode(), e);
        }
    }

    private String serialize(Object payload) {
        try {
            return xmlMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new SoundTouchException("Cannot serialize " + payload.getClass().getSimpleName(), e);
        }
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
