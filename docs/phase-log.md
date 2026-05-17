# Phase log

One row per phase. Status, when it cleared exit criteria, and notable deviations from `PLAN.md`.

| Phase | Status | Cleared on | Notes |
|---|---|---|---|
| 1 — Smoke test | ✅ done (1 open follow-up) | 2026-05-17 | Speaker found at `10.0.0.148`. `INTERNET_RADIO` source rejected (1005) — using `LOCAL_INTERNET_RADIO` instead. PAUSE/PLAY verified. **Open:** DHCP reservation on router. |
| 2 — Spring Boot skeleton + `SoundTouchClient` | ⏳ not started | — | Will default `ContentItem.source` to `LOCAL_INTERNET_RADIO`; tests must cover both sources. |
| 3 — `StationRegistry` + REST API | ⏳ | — | |
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
