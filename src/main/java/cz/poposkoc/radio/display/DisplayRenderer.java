package cz.poposkoc.radio.display;

import cz.poposkoc.radio.state.PlayerStateSnapshot;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class DisplayRenderer {

    private final int width;
    private final int height;

    public DisplayRenderer(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public BufferedImage renderBlank() {
        return new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
    }

    public BufferedImage render(PlayerStateSnapshot snap) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.WHITE);

            drawStationLine(g, snap);
            drawVolumeLine(g, snap);
        } finally {
            g.dispose();
        }
        return image;
    }

    private void drawStationLine(Graphics2D g, PlayerStateSnapshot snap) {
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 14);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        String text = snap.stationName() != null ? snap.stationName() : "—";
        String fitted = ellipsize(text, fm, width);
        int y = fm.getAscent() + 1;
        g.drawString(fitted, 0, y);
    }

    private void drawVolumeLine(Graphics2D g, PlayerStateSnapshot snap) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        String text;
        if (snap.muted() != null && snap.muted()) {
            text = "Vol MUTE";
        } else if (snap.volume() != null) {
            text = "Vol " + snap.volume();
        } else {
            text = "Vol --";
        }
        int y = height - fm.getDescent() - 1;
        g.drawString(text, 0, y);
    }

    private static String ellipsize(String text, FontMetrics fm, int maxWidthPx) {
        if (fm.stringWidth(text) <= maxWidthPx) {
            return text;
        }
        String ellipsis = "…";
        for (int len = text.length() - 1; len > 0; len--) {
            String candidate = text.substring(0, len) + ellipsis;
            if (fm.stringWidth(candidate) <= maxWidthPx) {
                return candidate;
            }
        }
        return ellipsis;
    }
}
