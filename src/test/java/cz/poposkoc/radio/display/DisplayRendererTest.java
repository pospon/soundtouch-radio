package cz.poposkoc.radio.display;

import cz.poposkoc.radio.state.PlayerStateSnapshot;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayRendererTest {

    private final DisplayRenderer renderer = new DisplayRenderer(128, 64);

    @Test
    void blankImageHasNoLitPixels() {
        BufferedImage img = renderer.renderBlank();
        assertThat(img.getWidth()).isEqualTo(128);
        assertThat(img.getHeight()).isEqualTo(64);
        assertThat(countLit(img)).isZero();
    }

    @Test
    void emptySnapshotShowsDashAndDoubleDash() {
        BufferedImage img = renderer.render(PlayerStateSnapshot.empty());
        assertThat(litInRow(img, 0, 28)).isPositive();
        assertThat(litInRow(img, 36, 64)).isPositive();
    }

    @Test
    void stationNameAppearsInTopHalf() {
        var snap = new PlayerStateSnapshot(
                "LOCAL_INTERNET_RADIO", "vltava", "Vltava", null, null, "PLAY_STATE");
        BufferedImage img = renderer.render(snap);

        assertThat(litInRow(img, 0, 28))
                .as("station-name band should have pixels")
                .isGreaterThan(40);
    }

    @Test
    void volumeAppearsInBottomHalf() {
        var snap = new PlayerStateSnapshot(null, null, null, 28, false, null);
        BufferedImage img = renderer.render(snap);

        assertThat(litInRow(img, 36, 64))
                .as("volume band should have pixels")
                .isGreaterThan(20);
    }

    @Test
    void longStationNameIsTruncatedToFitWidth() {
        var snap = new PlayerStateSnapshot(
                "LOCAL_INTERNET_RADIO",
                "super-long",
                "ČRo Hodně Dlouhé Jméno Stanice Které Se Nevejde",
                null, null, "PLAY_STATE");
        BufferedImage img = renderer.render(snap);

        for (int y = 0; y < 28; y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                assertThat(rgb)
                        .as("pixel (%d, %d) must be black or white, not gray", x, y)
                        .isIn(0x000000, 0xFFFFFF);
            }
        }
    }

    @Test
    void mutedSnapshotShowsMuteLabel() {
        var snap = new PlayerStateSnapshot(null, null, null, 50, true, null);
        BufferedImage img = renderer.render(snap);

        assertThat(litInRow(img, 36, 64)).isPositive();
    }

    private int countLit(BufferedImage img) {
        return litInRow(img, 0, img.getHeight());
    }

    private int litInRow(BufferedImage img, int yStart, int yEnd) {
        int count = 0;
        for (int y = yStart; y < yEnd; y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == (Color.WHITE.getRGB() & 0xFFFFFF)) {
                    count++;
                }
            }
        }
        return count;
    }
}
