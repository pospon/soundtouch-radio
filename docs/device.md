# Device — Bose SoundTouch 30 III (this user's unit)

All values verified against the running device during Phase 1 (2026-05-17).

| Field | Value |
|---|---|
| Model | Bose SoundTouch 30 Series III |
| Firmware | `27.0.3.46298.4608935` (epdbuild.trunk, 2021-10-06) |
| Module type | `sm2`, variant `mojo`, normal mode |
| Region | GB |
| Device name (mDNS / `/info`) | `SoundTouch oobýval30` (Czech "obývák" mangled by Bose's encoding — not a typo to fix) |
| LAN IP | `10.0.0.148` (DHCP; reservation pending — see [Open actions](#open-actions)) |
| mDNS hostname | `Bose-SM2-3ca3080e4d3a.local` |
| HTTP port | `8090` |
| WebSocket port | `8080` (Phase 5 — not yet verified) |
| Active MAC (SCM / Wi-Fi) | `7C:38:66:49:5B:48` |
| Secondary MAC (SMSC) | `3C:A3:08:0E:4D:3A` |
| Device ID (in XML) | `7C3866495B48` (= SCM MAC, no separators) |

## READY sources

From `GET /sources`:

| Source | Use for |
|---|---|
| `AUX` | physical AUX-IN |
| `ALEXA` | (n/a for this project) |
| `TUNEIN` | TuneIn station IDs (`s15200` etc.) — `location=` is the station ID |
| `LOCAL_INTERNET_RADIO` | direct Icecast/Shoutcast stream URLs — `location=` is the full URL |

Sources listed as `UNAVAILABLE`: `NOTIFICATION`, `AIRPLAY`, `QPLAY` (x2), `BLUETOOTH`, `SPOTIFY` (x2), `UPNP`, `STORED_MUSIC_MEDIA_RENDERER`.

## Pi host

| Field | Value |
|---|---|
| Hostname | `piserver` (SSH alias → `piserver.local` user `piserver`) |
| IP | `10.0.0.221` (same subnet `10.0.0.0/24` as speaker) |
| Arch | `aarch64` |
| OS | Debian GNU/Linux 13 (trixie) |
| Notes | Runs dockerized apps auto-synced from GitHub — see [pospon/tuya-horakova](https://github.com/pospon/tuya-horakova) for the pattern we'll mirror in Phase 8 |

## Open actions

- [ ] **DHCP reservation** on router: bind MAC `7C:38:66:49:5B:48` → IP `10.0.0.148`. Without this, the speaker may grab a new IP after a reboot and the app's `soundtouch.host` will stop working.
