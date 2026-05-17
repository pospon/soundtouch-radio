package cz.poposkoc.radio.soundtouch;

import cz.poposkoc.radio.soundtouch.dto.ContentItem;
import cz.poposkoc.radio.soundtouch.dto.Updates;
import cz.poposkoc.radio.state.PlayerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.dataformat.xml.XmlMapper;

@Component
public class UpdatesDispatcher {

    private static final Logger log = LoggerFactory.getLogger(UpdatesDispatcher.class);

    private final XmlMapper xmlMapper;
    private final PlayerState playerState;

    public UpdatesDispatcher(XmlMapper soundTouchXmlMapper, PlayerState playerState) {
        this.xmlMapper = soundTouchXmlMapper;
        this.playerState = playerState;
    }

    public void dispatch(String xml) {
        if (xml == null || xml.isBlank()) {
            return;
        }
        if (!xml.contains("<updates")) {
            log.debug("Ignoring non-updates frame: {}", trim(xml));
            return;
        }
        try {
            Updates updates = xmlMapper.readValue(xml, Updates.class);
            applyVolume(updates);
            applyNowSelection(updates);
            applyNowPlaying(updates);
        } catch (Exception e) {
            log.warn("Failed to parse <updates> frame: {} (payload: {})", e.getMessage(), trim(xml));
        }
    }

    private void applyVolume(Updates updates) {
        Updates.VolumeUpdated vu = updates.volumeUpdated();
        if (vu == null || vu.volume() == null) {
            return;
        }
        playerState.volumeChanged(vu.volume().actualVolume(), vu.volume().muteEnabled());
    }

    private void applyNowSelection(Updates updates) {
        Updates.NowSelectionUpdated nsu = updates.nowSelectionUpdated();
        if (nsu == null || nsu.preset() == null) {
            return;
        }
        ContentItem item = nsu.preset().contentItem();
        if (item == null) {
            return;
        }
        playerState.contentItemFromSpeaker(item);
    }

    private void applyNowPlaying(Updates updates) {
        Updates.NowPlayingUpdated npu = updates.nowPlayingUpdated();
        if (npu == null || npu.nowPlaying() == null) {
            return;
        }
        ContentItem item = npu.nowPlaying().contentItem();
        if (item == null) {
            return;
        }
        playerState.contentItemFromSpeaker(item);
    }

    private static String trim(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
