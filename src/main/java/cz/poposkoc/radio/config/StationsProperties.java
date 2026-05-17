package cz.poposkoc.radio.config;

import cz.poposkoc.radio.stations.Station;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("radio")
public record StationsProperties(
        List<Station> stations
) {
}
