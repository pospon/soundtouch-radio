package cz.poposkoc.radio.soundtouch.dto;

import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

@JsonRootName("key")
public record KeyCommand(
        @JacksonXmlProperty(isAttribute = true, localName = "state") State state,
        @JacksonXmlProperty(isAttribute = true, localName = "sender") String sender,
        @JacksonXmlText String key
) {
    public enum State { press, release }

    public static KeyCommand press(String key) {
        return new KeyCommand(State.press, "Gabbo", key);
    }

    public static KeyCommand release(String key) {
        return new KeyCommand(State.release, "Gabbo", key);
    }
}
