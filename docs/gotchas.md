# Gotchas

Things that surprised us. New entries go on top. Each entry: what happened, why, how we work around it.

---

## 2026-05-17 · Spring `@ConfigurationProperties` binder does not support `@JsonSubTypes`

I modelled `ButtonAction` as a sealed interface (`PlayStation`, `Key`) with Jackson's `@JsonTypeInfo(use=NAME, property="type")` + `@JsonSubTypes(...)` annotations and expected `application.yml` binding (`action.type: play_station`, `action.station: vltava`) to work. It bound the outer `ButtonBinding` but left the inner `action` field null/empty. Spring's properties binder is **not** Jackson — it doesn't read `@JsonSubTypes`. The polymorphic-by-property-discriminator pattern from REST/JSON DTOs doesn't translate.

**How to apply:** for `@ConfigurationProperties`, use a flat record with an enum `type` and nullable fields, validate in the canonical constructor. Keep polymorphic Jackson types for the REST/JSON layer if you want them.

---

## 2026-05-17 · `Map.copyOf` silently breaks `LinkedHashMap` insertion order

`StationRegistry` built a `LinkedHashMap` to preserve config order, then returned `Map.copyOf(result)`. The copy is unmodifiable but **its iteration order is not guaranteed** — JDK can reshuffle for security/hashing reasons. We hit this when the controller test asserted vltava-first and got fip-first instead. Fixed by returning `Collections.unmodifiableMap(result)` (which is a view that preserves the underlying order).

**Why:** `Map.copyOf`'s contract says nothing about iteration order. The fact that small maps *sometimes* preserve order is a happy accident. Don't rely on it.

**How to apply:** when order matters (config files, UI display lists, anything user-visible), use `Collections.unmodifiableMap(linkedHashMap)` or `List.copyOf` of an explicitly-ordered collection. Don't reach for `Map.copyOf` unless you've thought through it.

---

## 2026-05-17 · Speaker emits `errorUpdate value="4505" BMX_UNKNOWN_PLAYBACK_CONTENT` even when the stream plays fine

After `POST /select` with a valid Icecast URL, the speaker emits an `<errorUpdate severity="Recoverable">Unsupported content item</errorUpdate>` over WS *along with* the successful `<nowSelectionUpdated>`. The stream still plays audibly and `/now_playing` shows the new ContentItem correctly.

**Why:** likely the post-Bose-cloud firmware tries to look the URL up in Bose's (now-dead) content catalog, fails, but falls back to the local raw stream. The error is internal bookkeeping noise from the cloud-lookup attempt, not a real failure.

**How to apply:** `UpdatesDispatcher` ignores `<errorUpdate>` entirely. Don't surface it to the user. If we ever start surfacing speaker errors, filter out `4505 BMX_UNKNOWN_PLAYBACK_CONTENT` specifically.

---

## 2026-05-17 · SoundTouch WS emits `<nowSelectionUpdated>`, not `<nowPlayingUpdated>`, for station changes

`PLAN.md` says the speaker emits `<nowPlayingUpdated>` when the track/station changes. On our firmware 27.0.3, the actual frame is `<nowSelectionUpdated>` wrapping `<preset id="0"><ContentItem .../></preset>`. The plan's `nowPlayingUpdated` may exist for non-radio sources (AIRPLAY, BT) — we haven't seen it yet.

**How to apply:** the `Updates` DTO models both inners so we'll handle either shape when it appears. The dispatcher applies whichever one is non-null.

---

## 2026-05-17 · App with open SSE emitters + `@EnableScheduling` won't shut down on SIGTERM

`pkill -f cz.poposkoc.radio.RadioApplication` and even an explicit `kill <pid>` failed to stop the running app; only `kill -9` worked. With an active `SseEmitter` registered through `CopyOnWriteArrayList` and the `@Scheduled` 25-s heartbeat task, the JVM has non-daemon threads blocked in I/O or scheduler loops that don't honor Spring's normal shutdown signal.

**Phase 5 partial fix:** added `@PreDestroy closeAllOnShutdown()` on `EventsController` to complete all open `SseEmitter`s, and a matching `@PreDestroy stop()` on `SoundTouchEventListener` to close the WS session and shut down the reconnect scheduler. Spring `@SpringBootTest` shutdowns are now clean (confirmed by the `WS closed during shutdown` log line at test teardown). **However** `bootRun` shutdown is still broken: gradle's `bootRun` fork doesn't propagate `SIGTERM` to context-close, so `kill <pid>` still hangs. Use `kill -9` locally; production systemd will handle this properly.

**Workaround for local dev:** `kill -9` it. **For production (Phase 8):** systemd with `KillMode=process` + `TimeoutStopSec=10s` should make this work without `-9`. Will verify in Phase 8.

**Why:** Spring's default `TaskScheduler` for `@Scheduled` is a single-threaded scheduler with non-daemon threads. Gradle's `bootRun` adds another layer of process forking that swallows signals.

---

## 2026-05-17 · Boot 4 `@WebMvcTest(controllers = ...)` does not actually register the listed controllers

`@WebMvcTest(controllers = {RadioController.class, HealthController.class})` ran the test context, but every request returned 404 with `Handler = ResourceHttpRequestHandler`. The controller beans were not in the context — they fell through to the static-resource handler. Worked once we switched to `@WebMvcTest` (no `controllers =`) plus explicit `@Import({RadioController.class, HealthController.class, RadioExceptionHandler.class, ...})`.

**Why:** in Boot 4 the `controllers` attribute appears to *filter* type-includes rather than *register* the listed classes as beans — without a scanned base package the listed controllers never get instantiated. We did not chase the exact semantics; `@Import` is unambiguous.

**How to apply:** for `@WebMvcTest` in this project, list controllers and `@ControllerAdvice`s in `@Import`. Don't bother with the `controllers =` attribute.

---

## 2026-05-17 · `POST /select` is silently ignored while the speaker is in STANDBY

If `/now_playing` returns `source="STANDBY"`, sending `/select` still returns `<status>/select</status>` — but the speaker doesn't switch sources and stays asleep. After a successful-looking call, `/now_playing` is *still* `STANDBY`.

To play a station from a cold start, send `POST /key POWER` (press+release) first, wait ~1.5 s, then `/select`. The `Phase2SmokeRunner` checks `nowPlaying().source()` and wakes via `POWER` if needed before selecting.

**Why:** the Bose firmware treats STANDBY as a global off-switch — the source picker is gated behind it. The HTTP API doesn't surface the "I ignored you" reaction.
**How to apply:** when wiring `StationService.play()` (Phase 3), do the wake-on-standby dance there. The PWA should not have to know about it.

---

## 2026-05-17 · Jackson 3 + Java records + `@JacksonXmlText` do not deserialize cleanly

For Jackson 3 / Boot 4, an XML element that has both attributes *and* a text body — e.g. `<sourceItem source="..." status="...">label text</sourceItem>` — cannot be deserialized into a Java record whose components include one annotated with `@JacksonXmlText`. You get:

```
tools.jackson.databind.exc.InvalidDefinitionException:
  Invalid definition for property '' (of type X): Could not find creator property with name ''
  (known Creator properties: [...])
```

Jackson assigns the `@JacksonXmlText` parameter the implicit name `""` and then can't reconcile it with a canonical record creator. Adding `@JsonProperty("")` doesn't help. Adding an all-args constructor to a POJO doesn't help either — Jackson hijacks it as the creator and hits the same issue.

**Workaround:** for these specific types, drop records, use a POJO with **only** a no-arg constructor + private fields annotated with `@JacksonXmlProperty(isAttribute=true)` / `@JacksonXmlText`. Jackson uses the field-setter path. Add hand-written accessors that mimic the record API (e.g. `source()` returning the value), so call sites don't need to change.

Types that follow this pattern in our code (as of 2026-05-17):
- `Sources.SourceItem`
- `ErrorResponse.ErrorEntry`

Types that are records and work fine (no `@JacksonXmlText` text-with-attrs combo): `ContentItem`, `Info`, `NowPlaying`, `VolumeStatus`, `Sources`, `ErrorResponse`. Records used only for *serialization* (`KeyCommand`, `VolumeCommand`) can include `@JacksonXmlText` safely — the issue is deserialization-only.

**Why:** Jackson 3 record-creator detection plus the XML text-pseudo-property is an unresolved sharp edge. Spent ~30 minutes confirming this; not worth re-deriving each time.

---

## 2026-05-17 · `RestClient` + JDK request factory + `String.class` response decodes as ISO-8859-1

The speaker omits a `charset=UTF-8` parameter on its `Content-Type` header (`Content-Type: application/xml`). When Spring's `RestClient` is asked to return `body(String.class)`, it picks the converter's default charset (ISO-8859-1), and any non-ASCII characters (like `ý` in `obývák`) come back mojibake.

**Workaround:** ask `RestClient` for `body(byte[].class)` and let the `XmlMapper` parse the bytes — it honors the `<?xml encoding="UTF-8"?>` PI. Done in `SoundTouchClient`.

**Why:** the `<?xml ...?>` PI is the source of truth for XML, and trusting it sidesteps the HTTP charset miss. Re-encoding ourselves would be fighting Spring.

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
