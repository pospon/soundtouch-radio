# Gotchas

Things that surprised us. New entries go on top. Each entry: what happened, why, how we work around it.

---

## 2026-05-17 · `source="INTERNET_RADIO"` is rejected with error 1005

`PLAN.md` originally instructed the `<ContentItem>` body for `/select` to use `source="INTERNET_RADIO"`. On our device (Bose SoundTouch 30, firmware `27.0.3.46298`) the speaker responds with:

```xml
<errors deviceID="7C3866495B48">
  <error value="1005" name="UNKNOWN_SOURCE_ERROR" severity="Unknown">1005</error>
</errors>
```

`GET /sources` on this firmware lists these as `status="READY"`:

- `AUX`
- `ALEXA`
- `TUNEIN`
- `LOCAL_INTERNET_RADIO`

There is no `INTERNET_RADIO` source. This is post-Bose-cloud-sunset behavior: Bose retired the cloud-side internet-radio catalog, and recent firmwares replaced `INTERNET_RADIO` with `LOCAL_INTERNET_RADIO` (the speaker fetches the stream URL directly without any cloud lookup).

**Working `/select` body for a direct stream URL:**
```xml
<ContentItem source="LOCAL_INTERNET_RADIO" location="http://icecast2.rozhlas.cz/vltava-mp3-128" sourceAccount="" isPresetable="true">
  <itemName>Vltava</itemName>
</ContentItem>
```

**Working `/select` body for a TuneIn station:**
```xml
<ContentItem source="TUNEIN" location="s15200" sourceAccount="" isPresetable="true">
  <itemName>FIP</itemName>
</ContentItem>
```

**Rule:** never hardcode a `source=` value. At startup (or in CI) hit `GET /sources` and pick whichever of `LOCAL_INTERNET_RADIO` / `INTERNET_RADIO` is `READY`. Older firmwares may still expose the latter.

---

## 2026-05-17 · `/now_playing` for `LOCAL_INTERNET_RADIO` omits `playStatus`

For TuneIn the speaker normally returns `<playStatus>PLAY_STATE</playStatus>` inside `<nowPlaying>`. For `LOCAL_INTERNET_RADIO` we observed `<nowPlaying source="LOCAL_INTERNET_RADIO">` with **only** the `<ContentItem>` nested — no `playStatus`, no `<track>`, no `<artist>`. So we cannot tell from `/now_playing` alone whether the stream is actively playing or paused.

**Workaround for v1:** trust local state. The PWA tracks "we issued PLAY / we issued PAUSE" via `PlayerState`. WebSocket updates (Phase 5) may give better fidelity — re-evaluate then.

---

## 2026-05-17 · Device exposes two MAC addresses for one IP

`GET /info` returns two `<networkInfo>` blocks:

```xml
<networkInfo type="SCM"><macAddress>7C3866495B48</macAddress><ipAddress>10.0.0.148</ipAddress></networkInfo>
<networkInfo type="SMSC"><macAddress>3CA3080E4D3A</macAddress><ipAddress>10.0.0.148</ipAddress></networkInfo>
```

`SCM` is the Wi-Fi NIC actually holding the IP on the LAN. `SMSC` is the internal/secondary interface. The mDNS hostname `Bose-SM2-3ca3080e4d3a.local` is derived from the SMSC MAC — confusingly, that's *not* the MAC the router sees.

**Rule:** for DHCP reservation, use the **SCM** MAC (`7C:38:66:49:5B:48` on our device). The SMSC MAC will never appear in the router's ARP/lease table.
