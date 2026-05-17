# SoundTouch Radio — Implementation Plan

A self-hosted replacement for Bose SoundTouch presets, running on a Raspberry Pi, controlling a **Bose SoundTouch 30 Series III** via its local HTTP/WebSocket API.

## Goals

- Play internet radio stations on the SoundTouch 30 III without any cloud dependency.
- Replace lost presets with: **physical buttons** on a Pi + **phone web UI (PWA)**.
- Show now-playing info on a small OLED.
- Survive reboots, network blips, and the speaker being power-cycled.

## Non-goals (explicit, for scope discipline)

- Multi-room grouping (the speaker supports it; out of scope for v1).
- Spotify/Apple Music control (handled by their native apps via Spotify Connect / AirPlay).
- TTS / announcements.
- Cloud backup of stations (the YAML is in git, that's enough).

---

## Tech stack

| Layer | Choice | Reason |
|---|---|---|
| Hardware | Raspberry Pi 4 (2 GB ok) or Pi Zero 2 W | GPIO + Java; Pi 4 has Ethernet built in |
| OS | Raspberry Pi OS Lite 64-bit (Bookworm) | Minimal, headless |
| JDK | Eclipse Temurin 21 | Latest LTS, native ARM64 |
| Framework | Spring Boot 3.3.x (Kotlin) | Familiar, batteries included |
| HTTP client | Spring `RestClient` | Modern, synchronous, fine for low volume |
| WebSocket client | Spring `StandardWebSocketClient` | For SoundTouch events on :8080 |
| XML | `jackson-dataformat-xml` | DTOs ↔ SoundTouch XML |
| GPIO | `diozero` (`com.diozero:diozero-core`) | Modern, Pi 4/5 compatible, clean Kotlin interop |
| OLED | `diozero` SSD1306 driver | Same library, I²C |
| Build | Gradle (Kotlin DSL) | |
| Packaging | Spring Boot fat JAR + systemd unit | Simple, reliable |
| Config | YAML files (`stations.yaml`, `buttons.yaml`) | Hand-edited, git-tracked |

---

## Architecture

```
┌─────────────────────── Raspberry Pi ──────────────────────┐
│                                                            │
│  ┌──────────────────────────────────────────────────┐     │
│  │              Spring Boot Application              │     │
│  │                                                   │     │
│  │  ┌─────────────┐  ┌──────────────┐  ┌─────────┐  │     │
│  │  │ Web (REST + │  │ Button       │  │ OLED    │  │     │
│  │  │ SSE + PWA)  │  │ Handler      │  │ Display │  │     │
│  │  └──────┬──────┘  └──────┬───────┘  └────┬────┘  │     │
│  │         │                │                │       │     │
│  │  ┌──────┴────────────────┴────────────────┴────┐  │     │
│  │  │            StationService                    │  │     │
│  │  │  (loads YAML, exposes play(stationId))      │  │     │
│  │  └────────────────┬────────────────────────────┘  │     │
│  │                   │                                │     │
│  │  ┌────────────────┴────────────────┐  ┌─────────┐ │     │
│  │  │      SoundTouchClient            │  │ Event   │ │     │
│  │  │  (POST /select, /key, /volume)   │  │ Listener│ │     │
│  │  └────────────────┬────────────────┘  └────┬────┘ │     │
│  └─────────────────┬─┴────────────────────────┬──────┘     │
│                    │                          │            │
│      ┌─────────┐   │       ┌──────────┐       │            │
│      │ GPIO    │◀──┘       │ I²C bus  │       │            │
│      │ buttons │            │ OLED     │       │            │
│      └─────────┘            └──────────┘       │            │
└────────────────────────────────────────────────┼───────────┘
                                                 │
                          HTTP :8090 ────────────┤
                          WS   :8080 ◀───────────┘
                                  │
                          ┌───────▼────────┐
                          │ SoundTouch 30  │
                          │   Series III   │
                          └────────────────┘
```

**Key principle:** the Pi never proxies audio. It only sends control messages. The speaker fetches the stream directly.

---

## SoundTouch API reference (what we actually use)

Base URL: `http://<speaker-ip>:8090`

### `GET /info`
Returns device info (name, MAC, type, components). Used for health check and discovery.

### `POST /select`
**This replaces presets.** Body for a direct stream URL:
```xml
<ContentItem source="LOCAL_INTERNET_RADIO" location="STREAM_URL" sourceAccount="" isPresetable="true">
  <itemName>Display Name</itemName>
</ContentItem>
```
Body for a TuneIn station ID:
```xml
<ContentItem source="TUNEIN" location="s24861" sourceAccount="" isPresetable="true">
  <itemName>Display Name</itemName>
</ContentItem>
```
- `source` **must match the device's `/sources` list**. On firmware 27.x (post-Bose-cloud sunset, our device) the value `INTERNET_RADIO` is rejected with `1005 UNKNOWN_SOURCE_ERROR` — use `LOCAL_INTERNET_RADIO` for direct stream URLs. See `docs/gotchas.md`.
- Returns `<status>/select</status>` on success.

### `POST /key`
Body:
```xml
<key state="press" sender="Gabbo">VOLUME_UP</key>
```
Then `state="release"`. Send press+release as a pair.
Useful keys: `PLAY`, `PAUSE`, `PLAY_PAUSE`, `STOP`, `POWER`, `VOLUME_UP`, `VOLUME_DOWN`, `MUTE`, `NEXT_TRACK`, `PREV_TRACK`.

### `GET /volume`, `POST /volume`
```xml
<volume>30</volume>
```

### `GET /now_playing`
Returns current source, ContentItem, track metadata. Used for initial state on app start.

### WebSocket `:8080`, subprotocol `gabbo`
Connect with `Sec-WebSocket-Protocol: gabbo`. Server pushes `<updates>` frames whenever state changes:
- `<nowPlayingUpdated>` — track/station changed
- `<volumeUpdated>` — volume changed
- `<sourceUpdated>` — source changed (BT, AUX, etc.)

No client keepalive required; library should reconnect on drop.

---

## Speaker discovery

Three options, in order of robustness:

1. **Static IP via DHCP reservation** on the router. *Recommended.* Add the speaker's MAC to the router's DHCP static lease table. Configure `soundtouch.host` in `application.yml`. Done.
2. **mDNS** — the speaker advertises `_soundtouch._tcp` on the LAN. `diozero` doesn't help here; use `jmdns` if you want auto-discovery.
3. **Manual** — set `soundtouch.host: 192.168.1.42` in config.

For v1: **manual config + DHCP reservation**. Add mDNS later if it bugs you.

---

## Project layout

```
soundtouch-radio/
├── README.md
├── PLAN.md                                 # this file
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── config/
│   ├── application.yml                     # base config
│   ├── stations.yaml                       # station catalog
│   └── buttons.yaml                        # GPIO pin → action mapping
├── src/main/kotlin/cz/ondrej/radio/
│   ├── RadioApplication.kt
│   ├── config/
│   │   ├── SoundTouchProperties.kt
│   │   ├── StationProperties.kt
│   │   └── ButtonProperties.kt
│   ├── soundtouch/
│   │   ├── SoundTouchClient.kt             # REST: select/key/volume/info
│   │   ├── SoundTouchEventListener.kt      # WebSocket :8080
│   │   ├── dto/
│   │   │   ├── ContentItem.kt
│   │   │   ├── KeyCommand.kt
│   │   │   ├── Volume.kt
│   │   │   ├── NowPlaying.kt
│   │   │   └── Updates.kt                  # WS event envelope
│   │   └── XmlMapperConfig.kt
│   ├── stations/
│   │   ├── Station.kt
│   │   ├── StationRegistry.kt              # parses stations.yaml
│   │   └── StationService.kt               # play(stationId) façade
│   ├── buttons/
│   │   ├── ButtonAction.kt                 # sealed: PlayStation, Key, Volume
│   │   └── ButtonHandler.kt                # diozero GPIO setup
│   ├── display/
│   │   └── OledDisplay.kt                  # SSD1306 now-playing
│   ├── state/
│   │   └── PlayerState.kt                  # in-memory, updated by WS
│   └── web/
│       ├── RadioController.kt              # REST: /api/stations, /api/play/{id}
│       ├── EventsController.kt             # SSE: /api/events for PWA
│       └── HealthController.kt             # /api/health
├── src/main/resources/
│   ├── application.yml                     # defaults; overridden by /etc/soundtouch-radio/
│   └── static/                             # PWA
│       ├── index.html
│       ├── style.css
│       ├── app.js
│       ├── manifest.json
│       └── icon-192.png / icon-512.png
├── src/test/kotlin/cz/ondrej/radio/
│   ├── soundtouch/
│   │   ├── SoundTouchClientTest.kt         # MockWebServer
│   │   └── XmlMappingTest.kt
│   └── stations/
│       └── StationRegistryTest.kt
└── deploy/
    ├── soundtouch-radio.service            # systemd unit
    └── install.sh                          # copy JAR + config, enable service
```

---

## Configuration

### `application.yml`
```yaml
soundtouch:
  host: 192.168.1.42          # DHCP-reserved IP
  http-port: 8090
  ws-port: 8080
  connect-timeout: 2s
  read-timeout: 5s

stations:
  config-file: file:./config/stations.yaml

buttons:
  enabled: true
  config-file: file:./config/buttons.yaml
  debounce-ms: 50

display:
  enabled: true
  i2c-bus: 1
  i2c-address: 0x3C

server:
  port: 8080                  # PWA + API (different from speaker WS)
  # NOTE: speaker WS is OUTBOUND from Pi; no port conflict
```

### `stations.yaml`
```yaml
stations:
  - id: vltava
    name: ČRo Vltava
    stream: http://icecast2.rozhlas.cz/vltava-mp3-128
    button: 1
  - id: radiowave
    name: ČRo Radio Wave
    stream: http://icecast2.rozhlas.cz/radiowave-mp3-128
    button: 2
  - id: bbc-r1
    name: BBC Radio 1
    stream: http://stream.live.vc.bbcmedia.co.uk/bbc_radio_one
    button: 3
  - id: fip
    name: FIP
    tunein: s15200
    button: 4
  - id: somafm-groove
    name: SomaFM Groove Salad
    stream: http://ice1.somafm.com/groovesalad-128-mp3
    button: 5
  - id: somafm-drone
    name: SomaFM Drone Zone
    stream: http://ice1.somafm.com/dronezone-128-mp3
    button: 6
  # phone-only stations (no `button` field) go below
  - id: nts1
    name: NTS Radio 1
    stream: http://stream-relay-geo.ntslive.net/stream
```

Verify each stream URL with `curl -I <url>` before adding — many radio stations republish to new URLs over time.

### `buttons.yaml`
```yaml
buttons:
  # station preset buttons
  - gpio: 17
    action:
      type: play_station
      station: 1          # references stations.yaml `button` field
  - gpio: 27
    action: { type: play_station, station: 2 }
  - gpio: 22
    action: { type: play_station, station: 3 }
  - gpio: 5
    action: { type: play_station, station: 4 }
  - gpio: 6
    action: { type: play_station, station: 5 }
  - gpio: 13
    action: { type: play_station, station: 6 }
  # transport
  - gpio: 19
    action: { type: key, key: PLAY_PAUSE }
  - gpio: 26
    action: { type: key, key: VOLUME_DOWN }
  - gpio: 16
    action: { type: key, key: VOLUME_UP }
```

(Avoid GPIO 0/1 (I²C ID EEPROM), 2/3 (I²C — used by OLED), 14/15 (UART). The pins above are safe.)

---

## Implementation phases

Each phase is independently testable and leaves the system in a working state. **Do not start phase N+1 until phase N is verified end-to-end.**

### Phase 1 — Smoke test (manual, no code)

**Goal:** confirm the speaker responds and a stream actually plays before writing a single line of code. **This phase is done by you, not Claude Code.** Do not start Phase 2 until this passes.

1. Find speaker IP from router DHCP table or:
   ```bash
   sudo arp-scan --localnet | grep -i bose
   # or
   avahi-browse -rt _soundtouch._tcp
   ```
2. Reserve that IP in the router DHCP settings.
3. Verify HTTP API:
   ```bash
   curl http://<speaker-ip>:8090/info
   ```
   Should return XML with `<info>...</info>`.
4. Check which sources are READY on the device — this dictates the `source=` value in step 5:
   ```bash
   curl -sS http://<speaker-ip>:8090/sources | xmllint --format -
   ```
   Look for `<sourceItem source="LOCAL_INTERNET_RADIO" status="READY" .../>`. On older firmware you'd see `INTERNET_RADIO` instead — pick whichever is READY on **your** device.
5. Play a known-good stream manually (use the source value from step 4):
   ```bash
   curl -X POST http://<speaker-ip>:8090/select \
     -H "Content-Type: application/xml" \
     -d '<ContentItem source="LOCAL_INTERNET_RADIO" location="http://icecast2.rozhlas.cz/vltava-mp3-128" sourceAccount="" isPresetable="true"><itemName>Vltava</itemName></ContentItem>'
   ```
   **You should hear it within ~3 seconds.** If you get `<error value="1005" name="UNKNOWN_SOURCE_ERROR">`, the `source` value doesn't exist on this firmware — re-check step 4. If still no audio, the whole project is blocked; debug stream URL or speaker state first.
6. Try a `/key` command (press + release pair):
   ```bash
   curl -X POST http://<speaker-ip>:8090/key -d '<key state="press" sender="Gabbo">PAUSE</key>'
   curl -X POST http://<speaker-ip>:8090/key -d '<key state="release" sender="Gabbo">PAUSE</key>'
   ```

**Exit criteria:** you can play and pause a radio station from `curl`.

**Status: completed 2026-05-17 against `10.0.0.148` (Bose-SM2-3ca3080e4d3a.local, firmware 27.0.3). Key finding moved to `docs/gotchas.md`. DHCP reservation (MAC `7C:38:66:49:5B:48` → `10.0.0.148`) still pending — user action on router.**

### Phase 2 — Spring Boot skeleton + `SoundTouchClient`

**Goal:** Kotlin code that does what phase 1 did.

- Gradle project, Spring Boot 3.3.x, Kotlin 1.9+, Java 21 toolchain.
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-websocket`, `jackson-module-kotlin`, `jackson-dataformat-xml`, `spring-boot-configuration-processor`.
- `SoundTouchProperties` (`@ConfigurationProperties("soundtouch")`) — host/ports/timeouts.
- `XmlMapperConfig` — a `XmlMapper` bean configured with `KotlinModule` and `JacksonXmlModule`.
- DTOs: `ContentItem`, `KeyCommand`, `Volume`, `Info`, `NowPlaying`. Annotate with `@JacksonXmlRootElement`, `@JacksonXmlProperty`. Keep them small — only fields we use.
- `SoundTouchClient`:
  - `RestClient` bean with base URL + timeouts.
  - `fun info(): Info`
  - `fun select(contentItem: ContentItem)`
  - `fun pressKey(key: String)` — emits press then release, with ~50 ms gap.
  - `fun setVolume(v: Int)` — clamp 0..100.
  - `fun nowPlaying(): NowPlaying`
- Unit tests with **MockWebServer** (`com.squareup.okhttp3:mockwebserver`) — verify request bodies & response parsing for each method.
- Integration check: one Spring Boot test that calls `info()` against the real speaker (gated behind a `@EnabledIfEnvironmentVariable` so CI doesn't break).

**Exit criteria:** `./gradlew test` green; running the app and calling `client.select(...)` from a `CommandLineRunner` plays a station.

### Phase 3 — StationRegistry + REST API

**Goal:** a usable phone-friendly HTTP API.

- `Station` data class. `StationProperties` loaded from `stations.yaml` via `@ConfigurationProperties` (or a small `YamlPropertySourceFactory` if loading from external file).
- `StationRegistry` — in-memory map, validated at startup (no duplicate IDs, all `stream` or `tunein` URLs non-blank).
- `StationService.play(stationId)` — looks up station, builds `ContentItem`, calls `SoundTouchClient.select`.
- `RadioController`:
  - `GET /api/stations` → JSON list of stations (id, name, has-button).
  - `POST /api/play/{stationId}` → 204 on success, 404 if unknown.
  - `POST /api/key/{keyName}` → forward to client. Allowlist of keys.
  - `GET /api/volume`, `PUT /api/volume` (JSON `{"volume": 30}`).
- `HealthController` `GET /api/health` — calls `info()`, returns `UP`/`DOWN`.
- Tests with `@WebMvcTest`.

**Exit criteria:** `curl -X POST http://pi:8080/api/play/vltava` plays Vltava on the speaker.

### Phase 4 — PWA frontend

**Goal:** tap a tile on your phone, station plays.

- Single `index.html` with a grid of station tiles, transport buttons (play/pause, vol−, vol+), volume readout.
- Vanilla JS — no framework, no build step. `fetch` for actions, EventSource for state.
- `EventsController` `GET /api/events` — Spring `SseEmitter`, pushes JSON snapshots of `PlayerState` (current station id, volume, playing/paused) whenever it changes.
- `PlayerState` — `@Component` holding current state, updated by event listener (phase 5) and `select()` calls.
- `manifest.json` + `<link rel="manifest">` + theme color → "Add to Home Screen" works on iOS/Android.
- Icons: generate 192px and 512px PNGs (any quick tool; ImageMagick from a single high-res source is fine).
- Style: large tap targets (min 64×64 px), high contrast, dark by default. No bells, no whistles.

**Exit criteria:** open `http://<pi>:8080` on your phone, tap a station, it plays. Volume slider updates live when someone hits the volume button on the speaker remote.

### Phase 5 — WebSocket event listener

**Goal:** live state. App knows what's playing even when changed externally.

- `SoundTouchEventListener` — `@Component`, lifecycle-managed bean.
  - On `ApplicationReadyEvent`: open WS to `ws://<host>:8080/` with `Sec-WebSocket-Protocol: gabbo`.
  - Use Spring's `StandardWebSocketClient` + a `TextWebSocketHandler`.
  - Parse incoming `<updates>` XML, dispatch to `PlayerState`.
  - **Reconnect logic:** exponential backoff (1s → 2s → 5s → 10s, capped). Log warn on disconnect, info on reconnect.
- On reconnect, immediately call `GET /now_playing` to resync — WS gives deltas, we need a snapshot.

**Exit criteria:** change source on the speaker via its remote / another AirPlay sender — PWA UI reflects it within ~1 second. Pull the speaker's power, app logs disconnect; restore power, app reconnects without manual intervention.

### Phase 6 — Physical buttons

**Goal:** the actual point of the project.

- `diozero` dependency: `com.diozero:diozero-core:1.4.x` (check current).
- Wire one button first (just GPIO 17 → button → GND), verify with a tiny test runner before going further.
- `ButtonAction` sealed class: `PlayStation(stationId)`, `Key(name)`, `VolumeUp`, `VolumeDown` (the last two could just be `Key`s).
- `ButtonHandler` — `@Component`:
  - On startup, read `buttons.yaml`, for each entry:
    - Create `DigitalInputDevice(gpio, pullUp = true, activeHigh = false, debounceMs = 50)`.
    - `whenActivated { stationService.handle(action) }`.
  - Hold references so they aren't GC'd. Close on `@PreDestroy`.
- **Crash-resilience:** wrap each handler invocation in try/catch + log. A button press must never kill the app.

**Hardware wiring (one button):**

```
GPIO 17  ─────┐
              │
              ├──[ momentary button ]──┐
              │                        │
              └────────────────────────┴──── GND
```

Internal pull-up enabled in software; no external resistor needed.

**Exit criteria:** pressing physical button 1 plays Vltava. Pressing while it's already playing does nothing weird (idempotent select is fine).

### Phase 7 — OLED display

**Goal:** "what's playing right now" without picking up your phone.

- `diozero` SSD1306 driver (or `OLEDFontInterface` if it lives in a sibling artifact — verify at impl time).
- `OledDisplay` — `@Component`, listens to `PlayerState` changes.
- Layout (128×64):
  ```
  ┌────────────────────────────┐
  │ ▶ ČRo Vltava               │   ← line 1: station name (14 px)
  │ Smetana - Vltava           │   ← line 2: track title (scrolling if long)
  │                            │
  │ Vol 28               WiFi  │   ← line 4: volume + connection
  └────────────────────────────┘
  ```
- Idle timeout: dim/blank after 5 min of no changes (OLED burn-in).

**Wiring:**

```
OLED VCC ── 3V3
OLED GND ── GND
OLED SDA ── GPIO 2 (SDA1)
OLED SCL ── GPIO 3 (SCL1)
```

Enable I²C with `sudo raspi-config nonint do_i2c 0`. Verify with `i2cdetect -y 1` — OLED should show at `0x3C`.

**Exit criteria:** screen shows current station; updates when station changes; doesn't crash on disconnect.

### Phase 8 — Packaging & deployment

**Goal:** survives reboots, runs as a service.

- Gradle `bootJar` → fat JAR at `build/libs/soundtouch-radio.jar`.
- `deploy/soundtouch-radio.service`:
  ```ini
  [Unit]
  Description=SoundTouch Radio
  After=network-online.target
  Wants=network-online.target

  [Service]
  Type=simple
  User=pi
  WorkingDirectory=/opt/soundtouch-radio
  ExecStart=/usr/bin/java -jar /opt/soundtouch-radio/soundtouch-radio.jar \
    --spring.config.additional-location=file:/etc/soundtouch-radio/
  Restart=on-failure
  RestartSec=5s

  [Install]
  WantedBy=multi-user.target
  ```
- `deploy/install.sh`:
  ```bash
  #!/usr/bin/env bash
  set -euo pipefail
  sudo mkdir -p /opt/soundtouch-radio /etc/soundtouch-radio
  sudo cp build/libs/soundtouch-radio.jar /opt/soundtouch-radio/
  sudo cp config/*.yaml /etc/soundtouch-radio/
  sudo cp deploy/soundtouch-radio.service /etc/systemd/system/
  sudo systemctl daemon-reload
  sudo systemctl enable --now soundtouch-radio
  sudo journalctl -u soundtouch-radio -f
  ```
- Add `pi` user to `gpio` and `i2c` groups (`sudo usermod -aG gpio,i2c pi`).
- Optional: deploy from dev machine over SSH via `./gradlew bootJar && scp ... && ssh pi sudo systemctl restart soundtouch-radio`.

**Exit criteria:** reboot the Pi, system comes back up, station plays via physical button without intervention.

---

## Edge cases & gotchas (worth knowing up front)

> Living gotchas list is in `docs/gotchas.md`. The bullets below are seeded from the original plan; the docs file is canonical going forward.

- **`source="INTERNET_RADIO"` is rejected on firmware 27.x** with `1005 UNKNOWN_SOURCE_ERROR`. The post-Bose-cloud sunset firmware on our device exposes `LOCAL_INTERNET_RADIO` for direct stream URLs and `TUNEIN` for TuneIn IDs. Always pick the source value from `/sources` (status=READY), never hardcode `INTERNET_RADIO`. See `docs/gotchas.md`.
- **Stream URLs rot.** Build a `GET /api/stations/health` endpoint later that pings each stream URL and reports dead ones. Not in v1.
- **The speaker takes ~2–4 s** between receiving `/select` and audible audio. Don't let the UI hammer it with retries.
- **Sending `/select` while already playing the same stream** is harmless — it just restarts. Idempotent enough.
- **`POWER` key behavior** — SoundTouch enters standby on first press, returns on second. App should reflect standby state in PWA.
- **AirPlay/Bluetooth override** — if someone AirPlays from a phone, source changes to `AIRPLAY`. Hitting a station button does the right thing (forces back to `INTERNET_RADIO`).
- **Speaker reboots** — WS will drop. Reconnect logic in phase 5 handles it; verify it actually works by pulling the speaker's plug during testing.
- **Pi boot order** — systemd may start the app before the speaker is reachable on the network. The `info()` health probe will fail; `Restart=on-failure` retries. Optionally add a `pre-start` script that pings the speaker first.
- **Diozero on Pi 5** — Pi 5 changed the GPIO driver (RP1 chip). Diozero supports it but check version notes; Pi 4 is safer for v1.
- **i2c bus number** — on Pi 4 it's bus 1. Pi 5 may differ. `i2cdetect -l` shows it.

---

## Suggested commit history (for clean PRs / Claude Code sessions)

1. `chore: scaffold spring boot kotlin project`
2. `feat(soundtouch): add SoundTouchClient with select/key/volume`
3. `test(soundtouch): MockWebServer tests for client`
4. `feat(stations): load stations.yaml into StationRegistry`
5. `feat(web): REST API for play/key/volume + health`
6. `feat(web): PWA with station tiles and SSE state stream`
7. `feat(soundtouch): WebSocket event listener with reconnect`
8. `feat(buttons): GPIO button handler via diozero`
9. `feat(display): SSD1306 now-playing display`
10. `chore(deploy): systemd unit + install script`
11. `docs: README with hardware wiring and setup`

---

## What's deliberately punted to v2

- mDNS discovery
- Multi-room (group with other SoundTouch speakers)
- Station health-checking
- Web-based station editor (instead of YAML)
- Schedule-based playback ("morning alarm: BBC R4")
- Home Assistant MQTT bridge
- Replacing the speaker's own remote with a dedicated wireless remote

---

## Claude Code starting prompt

**Phase 1 is manual — do it yourself before invoking Claude Code.** When Phase 1 passes, open Claude Code in the empty repo and paste:

> Read `PLAN.md`. I've completed Phase 1 (manual smoke test — speaker responds, stream plays via curl). We're now implementing Phase 2: Spring Boot Kotlin skeleton and `SoundTouchClient`. Set up the Gradle project per the spec (Spring Boot 3.3, Kotlin, Java 21, dependencies listed). Then implement `SoundTouchProperties`, `XmlMapperConfig`, the DTOs (`ContentItem`, `KeyCommand`, `Volume`, `Info`, `NowPlaying`), and `SoundTouchClient` with methods `info()`, `select(ContentItem)`, `pressKey(String)`, `setVolume(Int)`, `nowPlaying()`. Write MockWebServer tests for each method verifying request body and response parsing. Stop at the end of Phase 2 — don't move past it without my go-ahead.

Then phase-by-phase, with the same "stop at the end" discipline. Each phase is a session.
