package cz.poposkoc.radio.config;

import com.diozero.devices.oled.MonochromeSsdOled;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("radio.display")
public record DisplayProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("1") int i2cController,
        @DefaultValue("60") int i2cAddress,
        @DefaultValue("TALL") MonochromeSsdOled.Height height,
        @DefaultValue("5m") Duration idleBlankAfter
) {
    public int widthPx() {
        return 128;
    }

    public int heightPx() {
        return height.lines * 8;
    }
}
