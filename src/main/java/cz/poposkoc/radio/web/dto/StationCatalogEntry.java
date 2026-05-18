package cz.poposkoc.radio.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * JSON descriptor served at /api/stations/{id}/station.json. The SoundTouch firmware
 * (post-cloud sunset) fetches this URL via LOCAL_INTERNET_RADIO/type="stationurl"
 * and dereferences `audio.streamUrl` to get the actual Icecast/Shoutcast stream.
 *
 * Must be served over plain HTTP — the firmware does not follow HTTPS redirects.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"audio", "imageUrl", "name", "streamType"})
public record StationCatalogEntry(
        Audio audio,
        String imageUrl,
        String name,
        String streamType
) {
    public record Audio(
            boolean hasPlaylist,
            boolean isRealtime,
            String streamUrl
    ) {
    }

    public static StationCatalogEntry forLiveRadio(String name, String streamUrl) {
        return new StationCatalogEntry(
                new Audio(true, true, streamUrl),
                null,
                name,
                "liveRadio"
        );
    }
}
