package cz.poposkoc.radio.buttons;

import cz.poposkoc.radio.config.ButtonsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ButtonBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void bindsPlayStationAndKeyActionsFromYamlStyleProperties() {
        runner
                .withPropertyValues(
                        "radio.buttons.enabled=true",
                        "radio.buttons.debounce=75ms",
                        "radio.buttons.bindings[0].gpio=17",
                        "radio.buttons.bindings[0].action.type=play_station",
                        "radio.buttons.bindings[0].action.station=vltava",
                        "radio.buttons.bindings[1].gpio=19",
                        "radio.buttons.bindings[1].action.type=key",
                        "radio.buttons.bindings[1].action.key=PLAY_PAUSE"
                )
                .run(ctx -> {
                    ButtonsProperties props = ctx.getBean(ButtonsProperties.class);

                    assertThat(props.enabled()).isTrue();
                    assertThat(props.debounce()).isEqualTo(Duration.ofMillis(75));
                    assertThat(props.bindings()).hasSize(2);

                    ButtonBinding play = props.bindings().get(0);
                    assertThat(play.gpio()).isEqualTo(17);
                    assertThat(play.action().type()).isEqualTo(ButtonAction.Type.play_station);
                    assertThat(play.action().station()).isEqualTo("vltava");

                    ButtonBinding key = props.bindings().get(1);
                    assertThat(key.gpio()).isEqualTo(19);
                    assertThat(key.action().type()).isEqualTo(ButtonAction.Type.key);
                    assertThat(key.action().key()).isEqualTo("PLAY_PAUSE");
                });
    }

    @Test
    void defaultsAreDisabledWithEmptyBindings() {
        runner.run(ctx -> {
            ButtonsProperties props = ctx.getBean(ButtonsProperties.class);
            assertThat(props.enabled()).isFalse();
            assertThat(props.bindings()).isEmpty();
        });
    }

    @EnableConfigurationProperties(ButtonsProperties.class)
    static class Config {}
}
