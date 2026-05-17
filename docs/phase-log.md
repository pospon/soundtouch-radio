# Phase log

One row per phase. Status, when it cleared exit criteria, and notable deviations from `PLAN.md`.

| Phase | Status | Cleared on | Notes |
|---|---|---|---|
| 1 — Smoke test | ✅ done (1 open follow-up) | 2026-05-17 | Speaker found at `10.0.0.148`. `INTERNET_RADIO` source rejected (1005) — using `LOCAL_INTERNET_RADIO` instead. PAUSE/PLAY verified. **Open:** DHCP reservation on router. |
| 2 — Spring Boot skeleton + `SoundTouchClient` | ✅ done | 2026-05-17 | Java (not Kotlin as plan), Boot 4.0.6, Spring 7, JDK 21 toolchain, Jackson 3 (`tools.jackson.*`). `SoundTouchClient` with `info / sources / nowPlaying / volume / select / setVolume / pressKey`. 10 MockWebServer tests + 2 live tests (gated via `SOUNDTOUCH_LIVE=true`) green. Smoke runner profile `phase2-smoke` selected Vltava end-to-end. Three new gotchas captured. |
| 3 — `StationRegistry` + REST API | ✅ done | 2026-05-17 | Station configured in `application.yml` under `radio.stations` (separated to own file in Phase 8). `StationRegistry` validates ids/sources/buttons at startup. `StationService.play(id)` wakes on STANDBY then selects. `RadioController` exposes `GET /api/stations`, `POST /api/play/{id}`, `POST /api/key/{key}` (allowlisted), `GET/PUT /api/volume`. `HealthController` does `GET /api/health` (200 UP / 503 DOWN). 9 `@WebMvcTest` + 10 service/registry unit tests green; live curl against speaker confirmed `POST /api/play/vltava` plays. `Phase2SmokeRunner` deleted. One new gotcha (`@WebMvcTest(controllers=)` doesn't register beans in Boot 4). |
| 4 — PWA frontend | ✅ done (partial exit-criterion until Phase 5) | 2026-05-17 | `PlayerState` (`AtomicReference<PlayerStateSnapshot>` + `ApplicationEventPublisher`). New endpoints: `GET /api/now-playing`, `GET /api/events` (SSE with 25-s heartbeat). PWA at `/`: vanilla JS single page, station tiles, transport, volume readout, refresh, dark theme, `manifest.json` + 192/512 icons (sips-rendered from `static/icon.svg`). Live smoke: SSE pushes verified end-to-end via curl (initial-state-on-connect + push-on-change). **Speaker-remote / AirPlay-originated changes won't appear in the PWA until Phase 5 wires the WS listener** — `PlayerState` has no third writer yet. New gotcha: SIGTERM doesn't gracefully stop the app with active SSE + `@EnableScheduling`; needs `kill -9` locally. |
| 5 — WebSocket listener | ⏳ | — | |
| 6 — Physical buttons | ⏳ | — | |
| 7 — OLED display | ⏳ | — | |
| 8 — Packaging & deployment | ⏳ | — | Will use the auto-sync-from-GitHub pattern from `pospon/tuya-horakova` rather than the systemd recipe in `PLAN.md` §8 (TBD when we get there). |

## Phase 1 detail

Verified from `piserver` (10.0.0.221) against speaker `10.0.0.148`:

1. mDNS discovery: `dns-sd -Z _soundtouch._tcp local` → `Bose-SM2-3ca3080e4d3a.local:8090`, MAC `7C3866495B48`.
2. `GET /info` → 200, parses cleanly, `<type>SoundTouch 30</type>`.
3. `GET /sources` → `LOCAL_INTERNET_RADIO` and `TUNEIN` are `READY`. **No `INTERNET_RADIO`.**
4. `POST /select` with `source="INTERNET_RADIO"` → `1005 UNKNOWN_SOURCE_ERROR`.
5. `POST /select` with `source="LOCAL_INTERNET_RADIO"` and Vltava Icecast URL → `<status>/select</status>`, `/now_playing` reflects it. Audio confirmation pending user.
6. `POST /key PAUSE` (press + release) → accepted.
7. `POST /key PLAY` (press + release) → accepted.
8. `GET /volume` → 39 / 39 / muted=false.

Open follow-ups:

- [ ] User: DHCP reservation `7C:38:66:49:5B:48 → 10.0.0.148` on the router.
- [ ] User: audible confirmation that PAUSE actually stopped audio and PLAY resumed it (the API calls succeeded — but with `LOCAL_INTERNET_RADIO` there's no `playStatus` to verify programmatically).

## Phase 2 detail

Build: Gradle 9.4.1, Spring Boot 4.0.6, Spring 7.0.7, JDK 21 toolchain (host has 25; Boot 4 + Kotlin's lack of JDK 25 targeting required dropping to a JDK that the test plugin and Kotlin support — 21 is the LTS that matches the plan).

Why we deviated from PLAN.md:
- **Java instead of Kotlin.** User preference. Plan's references to `kotlin/`, `KotlinModule`, etc. are obsolete.
- **Boot 4.0.6 instead of 3.3.x.** The Spring Initializr no longer offers 3.3.x; Boot 4 with Jackson 3 was the only realistic choice. Jackson moved from `com.fasterxml.jackson.*` → `tools.jackson.*` for implementation classes (annotations still at `com.fasterxml.jackson.annotation.*`).
- **`LOCAL_INTERNET_RADIO`, not `INTERNET_RADIO`.** Per Phase 1.

Surfaced gotchas (all in `docs/gotchas.md`):
1. STANDBY silently swallows `/select` — must wake via POWER first.
2. Jackson 3 records + `@JacksonXmlText` don't deserialize — use POJOs with no-arg ctor for those types.
3. `RestClient.body(String.class)` decodes as ISO-8859-1 when speaker omits charset — switched to `byte[].class`.

Exit-criterion verification:
- `./gradlew build` — green (10 unit tests pass).
- `SOUNDTOUCH_LIVE=true ./gradlew test --tests '*LiveTest'` — green against `10.0.0.148`.
- `./gradlew bootRun --args='--spring.profiles.active=phase2-smoke'` — wakes speaker if STANDBY, selects Vltava, confirms `/now_playing.source == LOCAL_INTERNET_RADIO`. Verified via SSH `curl /now_playing` from `piserver`.

## Phase 3 detail

Added:
- `cz.poposkoc.radio.stations.Station` (record), `StationRegistry`, `StationService`, `StationNotFoundException`.
- `cz.poposkoc.radio.config.StationsProperties` bound to `radio.stations` in `application.yml`.
- `cz.poposkoc.radio.web.RadioController`, `HealthController`, `RadioExceptionHandler`.
- View DTOs: `StationView`, `VolumeView`, `VolumeRequest`, `HealthView`.
- Tests: `StationRegistryTest` (6), `StationServiceTest` (4), `RadioControllerTest` (9).

Deleted: `Phase2SmokeRunner` — `StationService` now owns the wake-and-select path.

Plan deviations:
- **Stations live in `application.yml`** rather than a separate `stations.yaml`. Reason: simplicity for Phase 3 — one file, no `YamlPropertySourceFactory`. Will be split to external `config/stations.yaml` in Phase 8 so it can be edited outside the JAR.
- **Initial stations: only Vltava + Radio Wave** rather than the plan's 7. Reason: don't seed URLs we haven't verified; we'll grow the list in Phase 4 prep (with `curl -I` checks per the plan's own gotcha about URL rot).
- **Property prefix `radio.stations`** rather than `stations`. Reason: avoid future-conflict with the `stations:` top-level key the plan mentions, and group app-level config under a project namespace.

Exit-criterion verification (curl from same LAN as speaker, Mac at 10.0.0.213 → speaker at 10.0.0.148):
- `curl -X POST http://localhost:8080/api/play/vltava` → 204, speaker `now_playing.source = LOCAL_INTERNET_RADIO` with Vltava ContentItem, audible. ✅
- `curl http://localhost:8080/api/health` → 200 UP, device name correctly UTF-8 encoded through JSON.
- `curl -X PUT … /api/volume {"volume":25}` → 204, follow-up GET shows 25 (round-trip on real speaker).
- `curl -X POST /api/play/nope` → 404 with JSON error envelope.
- `curl -X POST /api/key/DROP_TABLES` → 400 (allowlist works).
- `curl -X POST /api/key/pause` → 204 (case-insensitive).

## Phase 4 detail

Added:
- `cz.poposkoc.radio.state.PlayerState` (`@Component` over an `AtomicReference<PlayerStateSnapshot>`), publishes `PlayerStateChanged` via `ApplicationEventPublisher` on every mutation.
- `cz.poposkoc.radio.state.PlayerStateSnapshot` (immutable record, all-nullable fields), `PlayerStateChanged` (event payload).
- `EventsController` — `GET /api/events` SseEmitter stream; `@EventListener` fan-out; `@Scheduled(fixedRate = 25_000)` heartbeat (`:hb` comment) to keep proxies from idle-killing the connection.
- `RadioController.nowPlaying` — `GET /api/now-playing` returning the snapshot. `setVolume` also pushes the applied volume into `PlayerState` (and `SoundTouchClient.setVolume` now returns the clamped value).
- `StationService` writes to `PlayerState` on every successful select.
- `@EnableScheduling` on `RadioApplication`.
- PWA at `src/main/resources/static/`: `index.html`, `style.css`, `app.js` (ES module, vanilla, EventSource for state, fetch for actions), `manifest.json`, `icon-192.png`, `icon-512.png` (rendered from `icon.svg` via `sips`).
- Tests: `PlayerStateTest` (3), `RadioControllerTest` got 1 new `now-playing` case (total 10). All 33 unit tests pass.

Plan deviations:
- **Exit criterion is only partially met until Phase 5.** Plan says: "Volume slider updates live when someone hits the volume button on the speaker remote." That needs the WS listener to push external state into `PlayerState`; this phase only handles PWA-originated changes. Documented up-front via the AskUserQuestion at the start of Phase 4.
- **No `playState`/`UNKNOWN` enum.** `PlayerStateSnapshot.playState` is just a nullable `String`. Per Phase 2 finding, `LOCAL_INTERNET_RADIO` doesn't report it anyway; we'll model it properly when Phase 5 sees real values.
- **PWA polling minimised:** the only "polling" is `app.js` calling `/api/volume` after a volume key press, because that path mutates the speaker but `PlayerState` doesn't yet know the new value (no WS subscriber).

Exit-criterion verification:
- `curl /` → 200 HTML, `/style.css /app.js /manifest.json /icon-192.png` all 200.
- `curl /api/now-playing` → empty snapshot on cold start.
- `curl -X POST /api/play/vltava` → 204; subsequent `/api/now-playing` shows `stationId=vltava`, `source=LOCAL_INTERNET_RADIO`; speaker confirms.
- SSE push test: subscriber connected before `play` got the initial snapshot, then a second `state` event after the action, then `:hb` after 25 s.

Open follow-ups:
- Live in-browser smoke (tapping a tile on a phone) — user-driven. Backend was verified via curl SSE round-trip and against the real speaker.
