package cz.poposkoc.radio.soundtouch.dto;

import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonRootName("updates")
public record Updates(
        @JacksonXmlProperty(isAttribute = true, localName = "deviceID") String deviceId,
        @JacksonXmlProperty(localName = "nowSelectionUpdated") NowSelectionUpdated nowSelectionUpdated,
        @JacksonXmlProperty(localName = "volumeUpdated") VolumeUpdated volumeUpdated,
        @JacksonXmlProperty(localName = "nowPlayingUpdated") NowPlayingUpdated nowPlayingUpdated
) {
    public record NowSelectionUpdated(
            @JacksonXmlProperty(localName = "preset") Preset preset
    ) {
    }

    public record Preset(
            @JacksonXmlProperty(isAttribute = true, localName = "id") String id,
            @JacksonXmlProperty(localName = "ContentItem") ContentItem contentItem
    ) {
    }

    public record VolumeUpdated(
            @JacksonXmlProperty(localName = "volume") VolumeStatus volume
    ) {
    }

    public record NowPlayingUpdated(
            @JacksonXmlProperty(localName = "nowPlaying") NowPlaying nowPlaying
    ) {
    }
}
