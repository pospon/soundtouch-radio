package cz.poposkoc.radio.soundtouch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class Fixtures {

    private Fixtures() {}

    static String load(String name) {
        String path = "fixtures/" + name;
        try (var in = Objects.requireNonNull(
                Fixtures.class.getResourceAsStream(path),
                () -> "Missing test fixture: " + path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
