package cz.poposkoc.radio.stations;

public class StationNotFoundException extends RuntimeException {
    public StationNotFoundException(String stationId) {
        super("Unknown station: " + stationId);
    }
}
