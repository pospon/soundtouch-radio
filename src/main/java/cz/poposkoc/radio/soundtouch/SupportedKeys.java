package cz.poposkoc.radio.soundtouch;

import java.util.Set;

public final class SupportedKeys {

    public static final Set<String> ALL = Set.of(
            "PLAY",
            "PAUSE",
            "PLAY_PAUSE",
            "STOP",
            "POWER",
            "VOLUME_UP",
            "VOLUME_DOWN",
            "MUTE",
            "NEXT_TRACK",
            "PREV_TRACK"
    );

    private SupportedKeys() {
    }
}
