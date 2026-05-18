package cz.poposkoc.radio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("soundtouch")
public record SoundTouchProperties(
        String host,
        @DefaultValue("8090") int httpPort,
        @DefaultValue("8080") int wsPort,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        // Base URL the SPEAKER uses to reach this app's JSON catalog endpoint.
        // Must be HTTP (not HTTPS) — speaker firmware doesn't follow redirects.
        // Example: "http://10.0.0.221:8080"
        String catalogBaseUrl,
        // Preset slot the app uses as a scratch slot to play stations from the PWA / API.
        // Slots 1..5 are reserved for the user's "real" remote-control presets.
        @DefaultValue("6") int scratchPresetSlot
) {
    public String baseUrl() {
        return "http://" + host + ":" + httpPort;
    }
}
