# REST API (Phase 3)

Base URL: `http://<pi>:8080/api`. All responses are JSON. The speaker's own XML API is not exposed here — this is a thin façade in front of `SoundTouchClient`.

## Endpoints

### `GET /stations`

List configured stations. Returns 200 with a JSON array.

```json
[
  {"id": "vltava", "name": "ČRo Vltava", "button": 1},
  {"id": "radiowave", "name": "ČRo Radio Wave", "button": 2}
]
```

`button` is `null` when the station has no physical-button binding.

### `POST /play/{stationId}`

Plays the named station. Wakes the speaker via `POWER` if it was in STANDBY, then sends `/select` with the appropriate source (`LOCAL_INTERNET_RADIO` for direct streams, `TUNEIN` for TuneIn IDs).

- `204 No Content` on success.
- `404 Not Found` with `{"error": "Unknown station: <id>"}` if the id isn't configured.

### `POST /key/{key}`

Sends a `/key` press+release pair to the speaker. The key name is case-insensitive but must be in the allowlist:

```
PLAY, PAUSE, PLAY_PAUSE, STOP, POWER,
VOLUME_UP, VOLUME_DOWN, MUTE,
NEXT_TRACK, PREV_TRACK
```

- `204 No Content` on success.
- `400 Bad Request` if the key isn't supported (Spring's default error envelope).

### `GET /volume`

Returns the speaker's current actual volume and mute state.

```json
{"volume": 25, "muted": false}
```

(`volume` here is the speaker's `actualvolume`, not the `targetvolume` — see `docs/api.md` for the distinction.)

### `PUT /volume`

Sets volume. Body is JSON `{"volume": int}`. Values outside 0..100 are clamped client-side by `SoundTouchClient`.

- `204 No Content` on success.

### `GET /now-playing`

Returns the app's current `PlayerState` snapshot.

```json
{
  "source": "LOCAL_INTERNET_RADIO",
  "stationId": "vltava",
  "stationName": "ČRo Vltava",
  "volume": 25,
  "muted": false,
  "playState": "PLAY_STATE"
}
```

Any field may be `null` if not yet known. Until Phase 5 wires the WS listener, this reflects only state changes triggered through this API.

### `GET /events`

Server-Sent Events stream. The client receives a `state` event whenever `PlayerState` changes; the body is the same JSON shape as `/now-playing`. A `state` event is emitted immediately on subscribe so a freshly reconnecting client gets the current snapshot without polling. Heartbeat comments (`:hb`) fire every 25 seconds to keep proxies happy.

```
event:state
data:{"source":"LOCAL_INTERNET_RADIO","stationId":"vltava","stationName":"ČRo Vltava","volume":25,"muted":false,"playState":"PLAY_STATE"}

:hb
```

Use from JS via `new EventSource('/api/events')`.

### `GET /health`

Probes the speaker via `GET /info`.

- `200 OK` with `{"status": "UP", "deviceName": "...", "deviceId": "..."}` when reachable.
- `503 Service Unavailable` with `{"status": "DOWN", "deviceName": null, "deviceId": null}` when the speaker is unreachable or the client throws.

## Verified curl session (2026-05-17, app running on Mac at 10.0.0.213 → speaker at 10.0.0.148)

```bash
$ curl http://localhost:8080/api/health
{"status":"UP","deviceName":"SoundTouch oobýval30","deviceId":"7C3866495B48"}

$ curl -X POST http://localhost:8080/api/play/vltava  # → 204, audible

$ curl http://localhost:8080/api/volume
{"volume":39,"muted":false}

$ curl -X PUT -H 'Content-Type: application/json' \
       -d '{"volume":25}' http://localhost:8080/api/volume  # → 204

$ curl -X POST http://localhost:8080/api/key/pause  # → 204
```
