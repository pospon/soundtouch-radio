package cz.poposkoc.radio.soundtouch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.dataformat.xml.XmlMapper;

@Configuration
class XmlMapperConfig {

    @Bean
    XmlMapper soundTouchXmlMapper() {
        return XmlMapper.builder().build();
    }
}
