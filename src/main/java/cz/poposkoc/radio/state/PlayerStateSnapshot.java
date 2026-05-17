package cz.poposkoc.radio.state;

/**
 * Immutable view of "what the speaker is doing right now" as the app understands it.
 * For LOCAL_INTERNET_RADIO the speaker does not report playStatus, so {@code playState}
 * stays null until Phase 5 wires up the WS listener for sources that do report it.
 */
public record PlayerStateSnapshot(
        String source,
        String stationId,
        String stationName,
        Integer volume,
        Boolean muted,
        String playState
) {
    public static PlayerStateSnapshot empty() {
        return new PlayerStateSnapshot(null, null, null, null, null, null);
    }
}
