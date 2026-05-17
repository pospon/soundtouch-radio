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
        @DefaultValue("5s") Duration readTimeout
) {
    public String baseUrl() {
        return "http://" + host + ":" + httpPort;
    }
}
