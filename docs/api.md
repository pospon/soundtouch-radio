# SoundTouch API — empirical notes

Observed against our SoundTouch 30 III on firmware 27.0.3. Public Bose docs no longer exist (the developer program was retired); this is reverse-engineered from live traffic. Anything contradicting `PLAN.md` wins.

Base URL: `http://10.0.0.148:8090`

## `GET /info`

Returns a single `<info>` element. Useful fields:

```xml
<info deviceID="7C3866495B48">
  <name>SoundTouch oobýval30</name>
  <type>SoundTouch 30</type>
  <components>
    <component><componentCategory>SCM</componentCategory><softwareVersion>27.0.3...</softwareVersion></component>
    <component><componentCategory>PackagedProduct</componentCategory></component>
  </components>
  <networkInfo type="SCM"><macAddress>7C3866495B48</macAddress><ipAddress>10.0.0.148</ipAddress></networkInfo>
  <networkInfo type="SMSC"><macAddress>3CA3080E4D3A</macAddress><ipAddress>10.0.0.148</ipAddress></networkInfo>
</info>
```

Health-check: any 200 with parseable `<info>` = device alive.

## `GET /sources`

Returns `<sources><sourceItem ... /></sources>`. `status="READY"` items are usable in `/select`. See [`device.md`](device.md#ready-sources) for what's READY on our unit. **Do not hardcode source names from the plan** — see [`gotchas.md`](gotchas.md).

## `POST /select`

Body is a single `<ContentItem>`. Source-specific shapes:

**Direct stream URL (Icecast/Shoutcast):**
```xml
<ContentItem source="LOCAL_INTERNET_RADIO" location="http://icecast2.rozhlas.cz/vltava-mp3-128" sourceAccount="" isPresetable="true">
  <itemName>Vltava</itemName>
</ContentItem>
```

**TuneIn:**
```xml
<ContentItem source="TUNEIN" location="s15200" sourceAccount="" isPresetable="true">
  <itemName>FIP</itemName>
</ContentItem>
```

Response on success: `<status>/select</status>`. On bad source: `<errors><error value="1005" name="UNKNOWN_SOURCE_ERROR">`.

## `POST /key`

Sender ID can be any string; community convention is `Gabbo`. Must always send **press** then **release** with ~50–100 ms gap:

```xml
<key state="press" sender="Gabbo">PAUSE</key>
<key state="release" sender="Gabbo">PAUSE</key>
```

Each call returns `<status>/key</status>`. Keys observed working: `PLAY`, `PAUSE`, `PLAY_PAUSE`, `STOP`, `POWER`, `VOLUME_UP`, `VOLUME_DOWN`, `MUTE`, `NEXT_TRACK`, `PREV_TRACK`. (Phase 1 verified: `PAUSE`, `PLAY`.)

## `GET /volume`

```xml
<volume deviceID="...">
  <targetvolume>39</targetvolume>
  <actualvolume>39</actualvolume>
  <muteenabled>false</muteenabled>
</volume>
```

`targetvolume` is what the user/app requested; `actualvolume` is what the amp is doing (they diverge briefly during ramp). For UI, show `actualvolume`.

## `POST /volume`

```xml
<volume>30</volume>
```

Range 0–100. Clamp client-side.

## `GET /now_playing`

For `LOCAL_INTERNET_RADIO` we see only:
```xml
<nowPlaying deviceID="..." source="LOCAL_INTERNET_RADIO" sourceAccount="">
  <ContentItem .../>
</nowPlaying>
```

**No `playStatus`, no track metadata.** Different sources (TUNEIN, AIRPLAY) return richer payloads — we'll document those when we encounter them. Track playback state in app memory; don't rely on the speaker to tell us "playing vs. paused" for direct streams. See [`gotchas.md`](gotchas.md).

When nothing has ever been selected, `source="INVALID_SOURCE"` and the ContentItem is mostly empty.

## WebSocket `:8080` (Phase 5 — not yet exercised)

To document once Phase 5 verifies it. Spec: connect with subprotocol `gabbo`, server pushes `<updates>` frames.
