package cz.poposkoc.radio.buttons;

import cz.poposkoc.radio.config.ButtonsProperties;
import cz.poposkoc.radio.soundtouch.SoundTouchClient;
import cz.poposkoc.radio.stations.StationNotFoundException;
import cz.poposkoc.radio.stations.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ButtonHandlerTest {

    private StationService stationService;
    private SoundTouchClient client;
    private ButtonHandler handler;

    @BeforeEach
    void setUp() {
        stationService = mock(StationService.class);
        client = mock(SoundTouchClient.class);
        var props = new ButtonsProperties(false, Duration.ofMillis(50), List.of());
        handler = new ButtonHandler(props, stationService, client);
    }

    @Test
    void playStationActionInvokesStationService() {
        handler.dispatch(new ButtonBinding(17, ButtonAction.playStation("vltava")));
        verify(stationService).play("vltava");
        verifyNoInteractions(client);
    }

    @Test
    void keyActionInvokesSoundTouchClient() {
        handler.dispatch(new ButtonBinding(19, ButtonAction.key("PLAY_PAUSE")));
        verify(client).pressKey("PLAY_PAUSE");
        verifyNoInteractions(stationService);
    }

    @Test
    void handlerExceptionsAreSwallowedSoButtonsCannotCrashApp() {
        doThrow(new StationNotFoundException("ghost"))
                .when(stationService).play("ghost");

        handler.dispatch(new ButtonBinding(17, ButtonAction.playStation("ghost")));

        verify(stationService).play("ghost");
    }

    @Test
    void runtimeFromClientIsSwallowed() {
        doThrow(new RuntimeException("speaker offline"))
                .when(client).pressKey("PAUSE");

        handler.dispatch(new ButtonBinding(19, ButtonAction.key("PAUSE")));

        verify(client).pressKey("PAUSE");
    }
}
