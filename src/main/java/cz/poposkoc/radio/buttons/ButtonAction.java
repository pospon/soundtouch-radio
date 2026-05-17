package cz.poposkoc.radio.buttons;

public record ButtonAction(
        Type type,
        String station,
        String key
) {

    public enum Type { play_station, key }

    public ButtonAction {
        if (type == null) {
            throw new IllegalArgumentException("ButtonAction.type is required");
        }
        switch (type) {
            case play_station -> {
                if (station == null || station.isBlank()) {
                    throw new IllegalArgumentException("play_station action requires `station`");
                }
            }
            case key -> {
                if (key == null || key.isBlank()) {
                    throw new IllegalArgumentException("key action requires `key`");
                }
            }
        }
    }

    public static ButtonAction playStation(String stationId) {
        return new ButtonAction(Type.play_station, stationId, null);
    }

    public static ButtonAction key(String key) {
        return new ButtonAction(Type.key, null, key);
    }
}
