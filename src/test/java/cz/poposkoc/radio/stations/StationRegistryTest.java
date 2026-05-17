package cz.poposkoc.radio.stations;

import cz.poposkoc.radio.config.StationsProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StationRegistryTest {

    @Test
    void loadsStationsAndLooksUpById() {
        var props = props(List.of(
                streamStation("vltava", "Vltava", "http://example.test/vltava", 1),
                tuneInStation("fip", "FIP", "s15200", 2)
        ));

        var registry = new StationRegistry(props);

        assertThat(registry.all()).hasSize(2);
        assertThat(registry.findById("vltava"))
                .get()
                .extracting(Station::name)
                .isEqualTo("Vltava");
        assertThat(registry.findById("missing")).isEmpty();
    }

    @Test
    void duplicateIdFailsStartup() {
        var props = props(List.of(
                streamStation("vltava", "Vltava", "http://example.test/vltava", 1),
                streamStation("vltava", "Vltava 2", "http://example.test/vltava2", 2)
        ));

        assertThatThrownBy(() -> new StationRegistry(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate")
                .hasMessageContaining("vltava");
    }

    @Test
    void stationWithBothStreamAndTuneInIsRejected() {
        var props = props(List.of(
                new Station("bad", "Bad", "http://example.test/x", "s1", 1)
        ));

        assertThatThrownBy(() -> new StationRegistry(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void stationWithNeitherStreamNorTuneInIsRejected() {
        var props = props(List.of(
                new Station("empty", "Empty", null, null, 1)
        ));

        assertThatThrownBy(() -> new StationRegistry(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void duplicateButtonNumberFailsStartup() {
        var props = props(List.of(
                streamStation("a", "A", "http://example.test/a", 1),
                streamStation("b", "B", "http://example.test/b", 1)
        ));

        assertThatThrownBy(() -> new StationRegistry(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Button 1");
    }

    @Test
    void noStationsConfiguredIsAllowed() {
        var registry = new StationRegistry(new StationsProperties(null));
        assertThat(registry.all()).isEmpty();
    }

    private static StationsProperties props(List<Station> stations) {
        return new StationsProperties(stations);
    }

    private static Station streamStation(String id, String name, String stream, Integer button) {
        return new Station(id, name, stream, null, button);
    }

    private static Station tuneInStation(String id, String name, String tunein, Integer button) {
        return new Station(id, name, null, tunein, button);
    }
}
