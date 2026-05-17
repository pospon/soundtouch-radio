package cz.poposkoc.radio.soundtouch.dto;

import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

import java.util.List;
import java.util.Objects;

@JsonRootName("sources")
public record Sources(
        @JacksonXmlProperty(isAttribute = true, localName = "deviceID") String deviceId,
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "sourceItem") List<SourceItem> items
) {
    public static final class SourceItem {

        @JacksonXmlProperty(isAttribute = true, localName = "source")
        private String source;

        @JacksonXmlProperty(isAttribute = true, localName = "sourceAccount")
        private String sourceAccount;

        @JacksonXmlProperty(isAttribute = true, localName = "status")
        private String status;

        @JacksonXmlText
        private String label;

        public SourceItem() {
        }

        public String source() { return source; }
        public String sourceAccount() { return sourceAccount; }
        public String status() { return status; }
        public String label() { return label; }

        public boolean isReady() {
            return "READY".equalsIgnoreCase(status);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SourceItem that)) return false;
            return Objects.equals(source, that.source)
                    && Objects.equals(sourceAccount, that.sourceAccount)
                    && Objects.equals(status, that.status)
                    && Objects.equals(label, that.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, sourceAccount, status, label);
        }
    }
}
