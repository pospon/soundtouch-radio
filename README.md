# SoundTouch Radio

A self-hosted replacement for the now-defunct Bose SoundTouch cloud presets. Runs on a Raspberry Pi, controls a **Bose SoundTouch 30 III** over its local HTTP + WebSocket API, and exposes:

- A phone-friendly PWA at `/` with station tiles, transport, and live state.
- Physical GPIO buttons on the Pi for one-tap station selection.
- An I²C OLED that shows the current station and volume.

No cloud, no Bose account, no Spotify-Connect-as-a-substitute.

## How it works

```
┌──────────── Raspberry Pi ─────────────┐
│                                       │
│   ┌─────────────────────────────┐     │
│   │ Spring Boot app (this repo) │     │
│   │  - PWA at /                 │     │
│   │  - REST + SSE at /api/*     │─────┼──── HTTP :8090 ──┐
│   │  - WS listener for state    │─────┼──── WS   :8080 ──┤
│   │  - GPIO button handler      │     │                  │
│   │  - SSD1306 renderer         │     │           ┌──────▼──────┐
│   └─────────────────────────────┘     │           │ SoundTouch  │
│        ▲                ▲             │           │  30 III     │
│        │                │             │           └─────────────┘
│   GPIO buttons     I²C OLED           │
└───────────────────────────────────────┘
```

The Pi never touches audio — it only sends control messages. The speaker fetches the stream directly.

## Quick start (local dev on Mac)

```bash
./gradlew bootRun
open http://localhost:8080
```

`radio.buttons.enabled` and `radio.display.enabled` default to `false`, so GPIO/I²C are skipped on dev hosts.

To talk to a real speaker on the LAN, override `soundtouch.host`:

```bash
SOUNDTOUCH_HOST=10.0.0.148 ./gradlew bootRun
```

To run the live integration tests against a real speaker:

```bash
SOUNDTOUCH_LIVE=true ./gradlew test --tests '*LiveTest'
```

## Configuration

All settings live under `radio.*` and `soundtouch.*` in `application.yml`. Override any value via env var (`SOUNDTOUCH_HOST`, `RADIO_BUTTONS_ENABLED`, etc.) or with `--spring.config.additional-location=file:/path/to/your/dir/`.

Stations are defined inline:

```yaml
radio:
  stations:
    - id: vltava
      name: ČRo Vltava
      stream: http://icecast2.rozhlas.cz/vltava-mp3-128
      button: 1
```

Use `stream:` for direct Icecast/Shoutcast URLs and `tunein:` for TuneIn station IDs (`s15200` etc.).

## Deployment to the Pi

Mirrors the [pospon/tuya-horakova](https://github.com/pospon/tuya-horakova) pattern:

1. Push to `main` triggers `.github/workflows/deploy.yml`.
2. GitHub Actions builds a `linux/arm64` Docker image and pushes it to GHCR.
3. Watchtower on the Pi (configured in [pospon/homeserver](https://github.com/pospon/homeserver)) polls GHCR every 5 minutes and auto-updates the container.
4. Traefik handles HTTPS via Let's Encrypt; the `lan-only` middleware restricts access to the LAN.

To wire it up the first time, copy `deploy/docker-compose.snippet.yml` into your homeserver `docker-compose.yml`, set `SOUNDTOUCH_HOST` in `/opt/homeserver/.env`, and `docker compose up -d soundtouch-radio`. After that, every push to `main` deploys automatically.

GPIO and I²C are passed through explicitly:

```yaml
devices:
  - /dev/gpiochip0:/dev/gpiochip0
  - /dev/i2c-1:/dev/i2c-1
group_add: [gpio, i2c]
```

The `pi` Spring profile (auto-activated via `SPRING_PROFILES_ACTIVE=pi`) flips `radio.buttons.enabled` and `radio.display.enabled` on.

## Docs

- [`PLAN.md`](PLAN.md) — full design and phase-by-phase roadmap.
- [`docs/phase-log.md`](docs/phase-log.md) — what's done, with deviations from the plan.
- [`docs/gotchas.md`](docs/gotchas.md) — non-obvious behavior, firmware quirks, things that bit us.
- [`docs/api.md`](docs/api.md) — empirical notes on the SoundTouch HTTP/WS API.
- [`docs/rest-api.md`](docs/rest-api.md) — our own HTTP API surface for the PWA.
- [`docs/device.md`](docs/device.md) — facts about the user's specific speaker.

## License

Personal project; no license. Take ideas, don't reuse verbatim.
