package cz.poposkoc.radio.soundtouch;

import cz.poposkoc.radio.soundtouch.dto.ErrorResponse;

public class SoundTouchException extends RuntimeException {

    private final ErrorResponse response;

    public SoundTouchException(String message, ErrorResponse response) {
        super(message);
        this.response = response;
    }

    public SoundTouchException(String message, Throwable cause) {
        super(message, cause);
        this.response = null;
    }

    public ErrorResponse response() {
        return response;
    }
}
