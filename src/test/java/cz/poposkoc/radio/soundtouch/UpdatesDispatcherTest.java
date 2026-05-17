package cz.poposkoc.radio.soundtouch;

import cz.poposkoc.radio.soundtouch.dto.ContentItem;
import cz.poposkoc.radio.state.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.dataformat.xml.XmlMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UpdatesDispatcherTest {

    private PlayerState playerState;
    private UpdatesDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        playerState = mock(PlayerState.class);
        dispatcher = new UpdatesDispatcher(XmlMapper.builder().build(), playerState);
    }

    @Test
    void volumeUpdatedPushesActualVolumeAndMute() {
        dispatcher.dispatch(Fixtures.load("ws/volume_updated.xml"));

        verify(playerState).volumeChanged(26, false);
    }

    @Test
    void nowSelectionUpdatedDispatchesContentItem() {
        dispatcher.dispatch(Fixtures.load("ws/now_selection_updated.xml"));

        ArgumentCaptor<ContentItem> captor = ArgumentCaptor.forClass(ContentItem.class);
        verify(playerState).contentItemFromSpeaker(captor.capture());
        ContentItem item = captor.getValue();
        assertThat(item.source()).isEqualTo("LOCAL_INTERNET_RADIO");
        assertThat(item.location()).isEqualTo("http://icecast2.rozhlas.cz/vltava-mp3-128");
        assertThat(item.itemName()).isEqualTo("ČRo Vltava");
    }

    @Test
    void sdkInfoFrameIsIgnored() {
        dispatcher.dispatch(Fixtures.load("ws/sdk_info.xml"));
        verifyNoInteractions(playerState);
    }

    @Test
    void userActivityFrameIsIgnored() {
        dispatcher.dispatch(Fixtures.load("ws/user_activity_update.xml"));
        verifyNoInteractions(playerState);
    }

    @Test
    void errorUpdateFrameIsIgnored() {
        dispatcher.dispatch(Fixtures.load("ws/error_update_4505.xml"));
        verifyNoInteractions(playerState);
    }

    @Test
    void malformedFrameDoesNotThrow() {
        dispatcher.dispatch("<updates><not><well-formed");
        verify(playerState, never()).contentItemFromSpeaker(any());
        verify(playerState, never()).volumeChanged(anyInt(), anyBoolean());
    }

    @Test
    void blankFrameIsIgnored() {
        dispatcher.dispatch("");
        dispatcher.dispatch(null);
        verifyNoInteractions(playerState);
    }

    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
    private static int anyInt() { return org.mockito.ArgumentMatchers.anyInt(); }
    private static boolean anyBoolean() { return org.mockito.ArgumentMatchers.anyBoolean(); }
}
