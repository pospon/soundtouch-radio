package cz.poposkoc.radio.soundtouch.dto;

import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

@JsonRootName("info")
public record Info(
        @JacksonXmlProperty(isAttribute = true, localName = "deviceID") String deviceId,
        @JacksonXmlProperty(localName = "name") String name,
        @JacksonXmlProperty(localName = "type") String type,
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "networkInfo") List<NetworkInfo> networkInfo
) {
    public record NetworkInfo(
            @JacksonXmlProperty(isAttribute = true, localName = "type") String type,
            @JacksonXmlProperty(localName = "macAddress") String macAddress,
            @JacksonXmlProperty(localName = "ipAddress") String ipAddress
    ) {
    }
}
