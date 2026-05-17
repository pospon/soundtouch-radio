package cz.poposkoc.radio.buttons;

import com.diozero.api.GpioEventTrigger;
import com.diozero.api.GpioPullUpDown;
import com.diozero.devices.Button;
import cz.poposkoc.radio.config.ButtonsProperties;
import cz.poposkoc.radio.soundtouch.SoundTouchClient;
import cz.poposkoc.radio.stations.StationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ButtonHandler {

    private static final Logger log = LoggerFactory.getLogger(ButtonHandler.class);

    private final ButtonsProperties props;
    private final StationService stationService;
    private final SoundTouchClient client;
    private final List<Button> buttons = new ArrayList<>();

    public ButtonHandler(ButtonsProperties props, StationService stationService, SoundTouchClient client) {
        this.props = props;
        this.stationService = stationService;
        this.client = client;
    }

    @PostConstruct
    void init() {
        if (!props.enabled()) {
            log.info("Buttons disabled (radio.buttons.enabled=false); skipping GPIO setup");
            return;
        }
        if (props.bindings() == null || props.bindings().isEmpty()) {
            log.info("No button bindings configured; skipping GPIO setup");
            return;
        }
        for (ButtonBinding binding : props.bindings()) {
            try {
                Button button = Button.Builder.builder(binding.gpio())
                        .setPullUpDown(GpioPullUpDown.PULL_UP)
                        .setActiveHigh(false)
                        .setTrigger(GpioEventTrigger.FALLING)
                        .build();
                button.whenPressed(timestamp -> dispatch(binding));
                buttons.add(button);
                log.info("Bound GPIO {} to {}", binding.gpio(), binding.action());
            } catch (Throwable t) {
                log.warn("Failed to bind GPIO {} ({}): {} — buttons may not work on this host (no Pi GPIO?)",
                        binding.gpio(), binding.action(), t.getMessage());
            }
        }
    }

    @PreDestroy
    void shutdown() {
        for (Button button : buttons) {
            try {
                button.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
        buttons.clear();
    }

    void dispatch(ButtonBinding binding) {
        try {
            ButtonAction action = binding.action();
            switch (action.type()) {
                case play_station -> stationService.play(action.station());
                case key -> client.pressKey(action.key());
            }
        } catch (Throwable t) {
            log.warn("Button GPIO {} handler failed: {}", binding.gpio(), t.getMessage());
        }
    }
}
