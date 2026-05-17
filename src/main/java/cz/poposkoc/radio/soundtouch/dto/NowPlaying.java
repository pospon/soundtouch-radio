package cz.poposkoc.radio.soundtouch.dto;

import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonRootName("nowPlaying")
public record NowPlaying(
        @JacksonXmlProperty(isAttribute = true, localName = "deviceID") String deviceId,
        @JacksonXmlProperty(isAttribute = true, localName = "source") String source,
        @JacksonXmlProperty(isAttribute = true, localName = "sourceAccount") String sourceAccount,
        @JacksonXmlProperty(localName = "ContentItem") ContentItem contentItem
) {
}
