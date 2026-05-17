package cz.poposkoc.radio.web;

import cz.poposkoc.radio.stations.StationNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
class RadioExceptionHandler {

    @ExceptionHandler(StationNotFoundException.class)
    ResponseEntity<Map<String, String>> stationNotFound(StationNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }
}
