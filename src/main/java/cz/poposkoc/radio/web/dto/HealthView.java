package cz.poposkoc.radio.web.dto;

public record HealthView(
        String status,
        String deviceName,
        String deviceId
) {
    public static HealthView up(String deviceName, String deviceId) {
        return new HealthView("UP", deviceName, deviceId);
    }

    public static HealthView down() {
        return new HealthView("DOWN", null, null);
    }
}
