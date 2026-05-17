package cz.poposkoc.radio.config;

import cz.poposkoc.radio.buttons.ButtonBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("radio.buttons")
public record ButtonsProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("50ms") Duration debounce,
        @DefaultValue List<ButtonBinding> bindings
) {
}
