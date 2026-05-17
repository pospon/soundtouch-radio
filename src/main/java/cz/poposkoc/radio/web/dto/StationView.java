package cz.poposkoc.radio.web.dto;

import cz.poposkoc.radio.stations.Station;

public record StationView(
        String id,
        String name,
        Integer button
) {
    public static StationView from(Station station) {
        return new StationView(station.id(), station.name(), station.button());
    }
}
