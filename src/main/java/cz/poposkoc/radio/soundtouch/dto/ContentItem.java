package cz.poposkoc.radio.soundtouch.dto;

import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonRootName("ContentItem")
public record ContentItem(
        @JacksonXmlProperty(isAttribute = true, localName = "source") String source,
        @JacksonXmlProperty(isAttribute = true, localName = "location") String location,
        @JacksonXmlProperty(isAttribute = true, localName = "sourceAccount") String sourceAccount,
        @JacksonXmlProperty(isAttribute = true, localName = "isPresetable") Boolean isPresetable,
        @JacksonXmlProperty(localName = "itemName") String itemName
) {
    public static final String SOURCE_LOCAL_INTERNET_RADIO = "LOCAL_INTERNET_RADIO";
    public static final String SOURCE_TUNEIN = "TUNEIN";

    public static ContentItem localInternetRadio(String streamUrl, String displayName) {
        return new ContentItem(SOURCE_LOCAL_INTERNET_RADIO, streamUrl, "", true, displayName);
    }

    public static ContentItem tuneIn(String stationId, String displayName) {
        return new ContentItem(SOURCE_TUNEIN, stationId, "", true, displayName);
    }
}
