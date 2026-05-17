# Phase log

One row per phase. Status, when it cleared exit criteria, and notable deviations from `PLAN.md`.

| Phase | Status | Cleared on | Notes |
|---|---|---|---|
| 1 — Smoke test | ✅ done (1 open follow-up) | 2026-05-17 | Speaker found at `10.0.0.148`. `INTERNET_RADIO` source rejected (1005) — using `LOCAL_INTERNET_RADIO` instead. PAUSE/PLAY verified. **Open:** DHCP reservation on router. |
| 2 — Spring Boot skeleton + `SoundTouchClient` | ✅ done | 2026-05-17 | Java (not Kotlin as plan), Boot 4.0.6, Spring 7, JDK 21 toolchain, Jackson 3 (`tools.jackson.*`). `SoundTouchClient` with `info / sources / nowPlaying / volume / select / setVolume / pressKey`. 10 MockWebServer tests + 2 live tests (gated via `SOUNDTOUCH_LIVE=true`) green. Smoke runner profile `phase2-smoke` selected Vltava end-to-end. Three new gotchas captured. |
| 3 — `StationRegistry` + REST API | ✅ done | 2026-05-17 | Station configured in `application.yml` under `radio.stations` (separated to own file in Phase 8). `StationRegistry` validates ids/sources/buttons at startup. `StationService.play(id)` wakes on STANDBY then selects. `RadioController` exposes `GET /api/stations`, `POST /api/play/{id}`, `POST /api/key/{key}` (allowlisted), `GET/PUT /api/volume`. `HealthController` does `GET /api/health` (200 UP / 503 DOWN). 9 `@WebMvcTest` + 10 service/registry unit tests green; live curl against speaker confirmed `POST /api/play/vltava` plays. `Phase2SmokeRunner` deleted. One new gotcha (`@WebMvcTest(controllers=)` doesn't register beans in Boot 4). |
| 4 — PWA frontend | ⏳ | — | |
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
