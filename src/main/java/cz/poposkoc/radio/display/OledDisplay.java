package cz.poposkoc.radio.display;

import com.diozero.api.I2CDevice;
import com.diozero.devices.oled.SSD1306;
import com.diozero.devices.oled.SsdOledCommunicationChannel;
import cz.poposkoc.radio.config.DisplayProperties;
import cz.poposkoc.radio.state.PlayerState;
import cz.poposkoc.radio.state.PlayerStateChanged;
import cz.poposkoc.radio.state.PlayerStateSnapshot;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class OledDisplay {

    private static final Logger log = LoggerFactory.getLogger(OledDisplay.class);

    private final DisplayProperties props;
    private final PlayerState playerState;
    private final DisplayRenderer renderer;
    private final ScheduledExecutorService idleScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "oled-idle");
                t.setDaemon(true);
                return t;
            });

    private volatile SSD1306 device;
    private volatile boolean blanked = false;
    private volatile ScheduledFuture<?> idleTask;

    public OledDisplay(DisplayProperties props, PlayerState playerState) {
        this.props = props;
        this.playerState = playerState;
        this.renderer = new DisplayRenderer(props.widthPx(), props.heightPx());
    }

    @PostConstruct
    void init() {
        if (!props.enabled()) {
            log.info("Display disabled (radio.display.enabled=false); skipping OLED init");
            return;
        }
        try {
            I2CDevice i2c = I2CDevice.builder(props.i2cAddress())
                    .setController(props.i2cController())
                    .build();
            var channel = new SsdOledCommunicationChannel.I2cCommunicationChannel(i2c);
            this.device = new SSD1306(channel, props.height());
            renderCurrent();
            log.info("OLED ready on I²C controller={} address=0x{} resolution={}x{}",
                    props.i2cController(), Integer.toHexString(props.i2cAddress()),
                    props.widthPx(), props.heightPx());
        } catch (Throwable t) {
            log.warn("Failed to initialise OLED on I²C controller={} address=0x{}: {} — display will be inactive",
                    props.i2cController(), Integer.toHexString(props.i2cAddress()), t.getMessage());
            this.device = null;
        }
    }

    @PreDestroy
    void shutdown() {
        idleScheduler.shutdownNow();
        SSD1306 d = device;
        device = null;
        if (d == null) {
            return;
        }
        try {
            d.clear();
            d.setDisplay(false);
            d.close();
        } catch (Exception e) {
            log.debug("Error closing OLED: {}", e.getMessage());
        }
    }

    @EventListener
    void onPlayerStateChanged(PlayerStateChanged event) {
        if (device == null) {
            return;
        }
        renderAndRescheduleIdle(event.snapshot());
    }

    private void renderCurrent() {
        renderAndRescheduleIdle(playerState.snapshot());
    }

    private synchronized void renderAndRescheduleIdle(PlayerStateSnapshot snap) {
        try {
            BufferedImage img = renderer.render(snap);
            device.display(img);
            if (blanked) {
                device.setDisplay(true);
                blanked = false;
            }
        } catch (Throwable t) {
            log.warn("OLED render failed: {}", t.getMessage());
            return;
        }
        ScheduledFuture<?> previous = idleTask;
        if (previous != null) {
            previous.cancel(false);
        }
        long delayMs = Math.max(props.idleBlankAfter().toMillis(), 1_000L);
        idleTask = idleScheduler.schedule(this::blank, delayMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void blank() {
        SSD1306 d = device;
        if (d == null || blanked) {
            return;
        }
        try {
            d.clear();
            d.setDisplay(false);
            blanked = true;
            log.debug("OLED blanked after idle timeout");
        } catch (Throwable t) {
            log.warn("OLED blank failed: {}", t.getMessage());
        }
    }
}
