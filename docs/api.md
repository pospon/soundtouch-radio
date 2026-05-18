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

## `POST /storePreset` + `POST /key PRESET_N` (current flow on firmware 27.0.6+)

The current playback path. Store a station as a preset whose `location` points at a JSON catalog URL we host:

```xml
<preset id="6">
  <ContentItem source="LOCAL_INTERNET_RADIO" type="stationurl"
               location="http://10.0.0.221:8080/api/stations/vltava/station.json"
               isPresetable="true">
    <itemName>ČRo Vltava</itemName>
  </ContentItem>
</preset>
```

POST to `/storePreset`. The JSON our app serves must be:

```json
{
  "audio": {
    "hasPlaylist": true,
    "isRealtime": true,
    "streamUrl": "http://icecast2.rozhlas.cz/vltava-mp3-128"
  },
  "name": "ČRo Vltava",
  "streamType": "liveRadio"
}
```

`location` must be plain HTTP — the firmware does not follow HTTPS. After storing, trigger via `POST /key PRESET_N` (press+release pair). **The Bose IR remote's preset buttons trigger the same firmware path**, so pinning a station to slot 1..6 makes the physical remote work for it.

`POST /removePreset` with `<preset id="N"/>` clears a slot.

## `POST /select` (LEGACY — does not work on firmware 27.0.6+)

Body is a single `<ContentItem>`. Source-specific shapes (firmware 27.0.3 only):

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

Response on success: `<status>/select</status>`. On bad source: `<errors><error value="1005" name="UNKNOWN_SOURCE_ERROR">`. **If the speaker is in STANDBY, `/select` returns `<status>/select</status>` but does nothing** — send `POST /key POWER` (press+release pair) first. See `gotchas.md`.

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

When nothing has ever been selected, `source="INVALID_SOURCE"` and the ContentItem is mostly empty. When the speaker is asleep, `source="STANDBY"` and the ContentItem is `<ContentItem source="STANDBY" isPresetable="false" />`.

## WebSocket `:8080`

Connect with subprotocol `gabbo` (`Sec-WebSocket-Protocol: gabbo`). No keepalive required from the client. Verified against firmware 27.0.3 on our device.

On connect, the speaker sends a greeting frame:
```xml
<SoundTouchSdkInfo serverVersion="4" serverBuild="trunk r46298 v4 epdbuild hepdswbld04" />
```

Subsequent frames have known shapes:

- **`<updates>` envelope** — the main change feed. Wraps a single inner element. Observed inners:

  - `<volumeUpdated><volume><targetvolume>26</targetvolume><actualvolume>26</actualvolume><muteenabled>false</muteenabled></volume></volumeUpdated>`
  - `<nowSelectionUpdated><preset id="0"><ContentItem ... /></preset></nowSelectionUpdated>` — fires when the source/station changes. Note: **`nowSelectionUpdated`**, not `nowPlayingUpdated`, on this firmware.

- **`<userActivityUpdate deviceID="..." />`** — fired on most user-visible actions. Carries no useful payload; ignored by the dispatcher.

- **`<errorUpdate>`** — recoverable error notifications. Example:
  ```xml
  <errorUpdate deviceID="..."><error value="4505" name="BMX_UNKNOWN_PLAYBACK_CONTENT" severity="Recoverable">Unsupported content item<customError>...</customError></error></errorUpdate>
  ```
  Often fires alongside `nowSelectionUpdated` even when the content actually plays. See `gotchas.md`.

Frames the plan mentioned but we have NOT observed yet on this firmware:
- `<sourceUpdated>` — may only fire on source-enum transitions (e.g. INTERNET_RADIO → AIRPLAY), not within-source moves.
- `<nowPlayingUpdated>` — likely the form for non-radio sources (AIRPLAY, BT). Will rediscover when we test those.

Captured samples live at `src/test/resources/cz/poposkoc/radio/soundtouch/fixtures/ws/*.xml`.
