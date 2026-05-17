package cz.poposkoc.radio.soundtouch.dto;

import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonRootName("volume")
public record VolumeStatus(
        @JacksonXmlProperty(localName = "targetvolume") int targetVolume,
        @JacksonXmlProperty(localName = "actualvolume") int actualVolume,
        @JacksonXmlProperty(localName = "muteenabled") boolean muteEnabled
) {
}
