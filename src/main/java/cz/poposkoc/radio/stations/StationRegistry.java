package cz.poposkoc.radio.stations;

import cz.poposkoc.radio.config.StationsProperties;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class StationRegistry {

    private final Map<String, Station> byId;

    public StationRegistry(StationsProperties props) {
        List<Station> stations = props.stations() == null ? List.of() : props.stations();
        this.byId = java.util.Collections.unmodifiableMap(validate(stations));
    }

    public Optional<Station> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<Station> all() {
        return byId.values();
    }

    private static Map<String, Station> validate(List<Station> stations) {
        Map<String, Station> result = new LinkedHashMap<>();
        Map<Integer, String> buttons = new HashMap<>();
        Set<String> sources = new HashSet<>();

        for (Station station : stations) {
            requireNonBlank("id", station.id());
            requireNonBlank("name", station.name());
            requireExactlyOneSource(station);

            if (result.putIfAbsent(station.id(), station) != null) {
                throw new IllegalStateException(
                        "Duplicate station id: " + station.id());
            }
            sources.add(station.id());

            if (station.button() != null) {
                String previous = buttons.putIfAbsent(station.button(), station.id());
                if (previous != null) {
                    throw new IllegalStateException(
                            "Button %d is assigned to both '%s' and '%s'"
                                    .formatted(station.button(), previous, station.id()));
                }
            }
        }
        return result;
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Station " + field + " must not be blank");
        }
    }

    private static void requireExactlyOneSource(Station s) {
        boolean hasStream = s.stream() != null && !s.stream().isBlank();
        boolean hasTunein = s.tunein() != null && !s.tunein().isBlank();
        if (hasStream == hasTunein) {
            throw new IllegalStateException(
                    "Station '%s' must define exactly one of `stream` or `tunein`"
                            .formatted(s.id()));
        }
    }
}
