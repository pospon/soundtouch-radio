package cz.poposkoc.radio.stations;

public record Station(
        String id,
        String name,
        String stream,
        String tunein,
        Integer button
) {
    public boolean hasButton() {
        return button != null;
    }
}
