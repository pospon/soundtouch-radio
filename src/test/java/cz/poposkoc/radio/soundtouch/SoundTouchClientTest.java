package cz.poposkoc.radio.soundtouch;

import cz.poposkoc.radio.soundtouch.dto.ContentItem;
import cz.poposkoc.radio.soundtouch.dto.NowPlaying;
import cz.poposkoc.radio.soundtouch.dto.Sources;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.dataformat.xml.XmlMapper;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoundTouchClientTest {

    private MockWebServer server;
    private SoundTouchClient client;
    private XmlMapper xmlMapper;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        xmlMapper = XmlMapper.builder().build();
        RestClient http = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        client = new SoundTouchClient(http, xmlMapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private void enqueueXml(String body) {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_XML_VALUE)
                .setBody(body));
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        return server.takeRequest(2, TimeUnit.SECONDS);
    }

    @Test
    void info_parsesDeviceMetadataAndBothNetworkInterfaces() throws Exception {
        enqueueXml(Fixtures.load("info.xml"));

        var info = client.info();

        RecordedRequest req = takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/info");

        assertThat(info.deviceId()).isEqualTo("7C3866495B48");
        assertThat(info.name()).isEqualTo("SoundTouch oobýval30");
        assertThat(info.type()).isEqualTo("SoundTouch 30");
        assertThat(info.networkInfo())
                .hasSize(2)
                .extracting("type", "macAddress", "ipAddress")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("SCM", "7C3866495B48", "10.0.0.148"),
                        org.assertj.core.groups.Tuple.tuple("SMSC", "3CA3080E4D3A", "10.0.0.148"));
    }

    @Test
    void sources_recognisesReadyVsUnavailable() throws Exception {
        enqueueXml(Fixtures.load("sources.xml"));

        Sources sources = client.sources();

        RecordedRequest req = takeRequest();
        assertThat(req.getPath()).isEqualTo("/sources");

        assertThat(sources.items())
                .filteredOn(Sources.SourceItem::isReady)
                .extracting(Sources.SourceItem::source)
                .containsExactlyInAnyOrder("AUX", "ALEXA", "TUNEIN", "LOCAL_INTERNET_RADIO");

        assertThat(sources.items())
                .filteredOn(item -> !item.isReady())
                .extracting(Sources.SourceItem::source)
                .contains("NOTIFICATION", "AIRPLAY");
    }

    @Test
    void nowPlaying_parsesLocalInternetRadioContentItem() throws Exception {
        enqueueXml(Fixtures.load("now_playing.xml"));

        NowPlaying np = client.nowPlaying();

        assertThat(takeRequest().getPath()).isEqualTo("/now_playing");
        assertThat(np.source()).isEqualTo("LOCAL_INTERNET_RADIO");
        assertThat(np.contentItem()).isNotNull();
        assertThat(np.contentItem().source()).isEqualTo("LOCAL_INTERNET_RADIO");
        assertThat(np.contentItem().location()).isEqualTo("http://icecast2.rozhlas.cz/vltava-mp3-128");
        assertThat(np.contentItem().itemName()).isEqualTo("Vltava");
    }

    @Test
    void volume_parsesTargetActualAndMute() throws Exception {
        enqueueXml(Fixtures.load("volume.xml"));

        var vol = client.volume();

        assertThat(takeRequest().getPath()).isEqualTo("/volume");
        assertThat(vol.targetVolume()).isEqualTo(39);
        assertThat(vol.actualVolume()).isEqualTo(39);
        assertThat(vol.muteEnabled()).isFalse();
    }

    @Test
    void select_postsContentItemWithLocalInternetRadioSource() throws Exception {
        enqueueXml(Fixtures.load("select_ok.xml"));

        client.select(ContentItem.localInternetRadio(
                "http://icecast2.rozhlas.cz/vltava-mp3-128", "Vltava"));

        RecordedRequest req = takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/select");
        assertThat(req.getHeader("Content-Type")).contains(MediaType.APPLICATION_XML_VALUE);

        String body = req.getBody().readUtf8();
        assertThat(body)
                .contains("<ContentItem")
                .contains("source=\"LOCAL_INTERNET_RADIO\"")
                .contains("location=\"http://icecast2.rozhlas.cz/vltava-mp3-128\"")
                .contains("<itemName>Vltava</itemName>");
    }

    @Test
    void select_rejectsInternetRadioWithErrorResponse() throws Exception {
        enqueueXml(Fixtures.load("error_1005.xml"));

        assertThatThrownBy(() -> client.select(
                new ContentItem("INTERNET_RADIO",
                        "http://icecast2.rozhlas.cz/vltava-mp3-128", "", true, "Vltava")))
                .isInstanceOf(SoundTouchException.class)
                .satisfies(ex -> {
                    var err = ((SoundTouchException) ex).response();
                    assertThat(err).isNotNull();
                    assertThat(err.errors())
                            .hasSize(1)
                            .first()
                            .extracting("value", "name")
                            .containsExactly(1005, "UNKNOWN_SOURCE_ERROR");
                });
    }

    @Test
    void pressKey_sendsPressThenReleasePair() throws Exception {
        enqueueXml(Fixtures.load("key_ok.xml"));
        enqueueXml(Fixtures.load("key_ok.xml"));

        client.pressKey("PAUSE");

        RecordedRequest press = takeRequest();
        RecordedRequest release = takeRequest();

        assertThat(press.getMethod()).isEqualTo("POST");
        assertThat(press.getPath()).isEqualTo("/key");
        assertThat(press.getBody().readUtf8())
                .contains("state=\"press\"")
                .contains("sender=\"Gabbo\"")
                .contains(">PAUSE</key>");

        assertThat(release.getPath()).isEqualTo("/key");
        assertThat(release.getBody().readUtf8())
                .contains("state=\"release\"")
                .contains(">PAUSE</key>");
    }

    @Test
    void setVolume_clampsAboveHundred() throws Exception {
        enqueueXml(Fixtures.load("key_ok.xml"));

        client.setVolume(250);

        RecordedRequest req = takeRequest();
        assertThat(req.getPath()).isEqualTo("/volume");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("<volume>100</volume>");
    }

    @Test
    void setVolume_clampsBelowZero() throws Exception {
        enqueueXml(Fixtures.load("key_ok.xml"));

        client.setVolume(-7);

        String body = takeRequest().getBody().readUtf8();
        assertThat(body).contains("<volume>0</volume>");
    }
}
