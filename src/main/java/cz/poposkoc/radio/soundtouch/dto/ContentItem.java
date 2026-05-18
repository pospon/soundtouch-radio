package cz.poposkoc.radio.soundtouch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Post-cloud-sunset shape: {@code type="stationurl"} is required when
 * {@code source="LOCAL_INTERNET_RADIO"} and {@code location} is a JSON catalog URL.
 * Pre-sunset firmware accepted raw stream URLs in {@code location} without
 * {@code type}; firmware 27.0.6 rejects that with error 1005.
 */
@JsonRootName("ContentItem")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentItem(
        @JacksonXmlProperty(isAttribute = true, localName = "source") String source,
        @JacksonXmlProperty(isAttribute = true, localName = "type") String type,
        @JacksonXmlProperty(isAttribute = true, localName = "location") String location,
        @JacksonXmlProperty(isAttribute = true, localName = "sourceAccount") String sourceAccount,
        @JacksonXmlProperty(isAttribute = true, localName = "isPresetable") Boolean isPresetable,
        @JacksonXmlProperty(localName = "itemName") String itemName
) {
    public static final String SOURCE_LOCAL_INTERNET_RADIO = "LOCAL_INTERNET_RADIO";
    public static final String SOURCE_TUNEIN = "TUNEIN";
    public static final String TYPE_STATION_URL = "stationurl";

    /** Convenience: store a preset that resolves a station via our JSON catalog. */
    public static ContentItem catalog(String catalogJsonUrl, String displayName) {
        return new ContentItem(SOURCE_LOCAL_INTERNET_RADIO, TYPE_STATION_URL,
                catalogJsonUrl, "", true, displayName);
    }
}
