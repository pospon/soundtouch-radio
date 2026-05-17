package cz.poposkoc.radio.soundtouch;

import cz.poposkoc.radio.config.SoundTouchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits the real speaker on the LAN. Disabled unless SOUNDTOUCH_LIVE=true so it does not break CI
 * or developer machines that are off-network.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SOUNDTOUCH_LIVE", matches = "true")
class SoundTouchClientLiveTest {

    @Autowired
    private SoundTouchClient client;

    @Autowired
    private SoundTouchProperties props;

    @Test
    void info_returnsLiveDeviceData() {
        var info = client.info();
        assertThat(info.deviceId()).isNotBlank();
        assertThat(info.type()).contains("SoundTouch");
        assertThat(info.networkInfo()).isNotEmpty();
    }

    @Test
    void sources_includesLocalInternetRadioAsReady() {
        var sources = client.sources();
        assertThat(sources.items())
                .filteredOn(item -> item.isReady())
                .extracting("source")
                .as("LOCAL_INTERNET_RADIO must be a READY source on %s — see docs/gotchas.md", props.host())
                .contains("LOCAL_INTERNET_RADIO");
    }
}
