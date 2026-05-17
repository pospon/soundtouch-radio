package cz.poposkoc.radio.soundtouch.dto;

import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

@JsonRootName("volume")
public record VolumeCommand(
        @JacksonXmlText int value
) {
}
