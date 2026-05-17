package cz.poposkoc.radio.soundtouch.dto;

import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

import java.util.List;
import java.util.Objects;

@JsonRootName("errors")
public record ErrorResponse(
        @JacksonXmlProperty(isAttribute = true, localName = "deviceID") String deviceId,
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "error") List<ErrorEntry> errors
) {
    public static final class ErrorEntry {

        @JacksonXmlProperty(isAttribute = true, localName = "value")
        private int value;

        @JacksonXmlProperty(isAttribute = true, localName = "name")
        private String name;

        @JacksonXmlProperty(isAttribute = true, localName = "severity")
        private String severity;

        @JacksonXmlText
        private String message;

        public ErrorEntry() {
        }

        public int value() { return value; }
        public String name() { return name; }
        public String severity() { return severity; }
        public String message() { return message; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ErrorEntry that)) return false;
            return value == that.value
                    && Objects.equals(name, that.name)
                    && Objects.equals(severity, that.severity)
                    && Objects.equals(message, that.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, name, severity, message);
        }
    }
}
