# Fork Notes

Technical differences between StreamVault Mobile and upstream StreamVault, and the design behind planned changes. Keep this file current whenever a fork change lands.

## Baseline

- Upstream: <https://github.com/Davidona/StreamVault-IPTV>, branch `master`, forked at `b777a112` (1.2.2).
- Stack (unchanged): Kotlin, Jetpack Compose (incl. `androidx.tv` material3), Room, Hilt, WorkManager, Media3.

## Identity and distribution

| Item | Upstream | Fork |
|------|----------|------|
| `applicationId` | `com.streamvault.app` | `com.streamvault.mobile` (`.beta`, `.debug`, `.syncbenchmark` suffixes unchanged) |
| Kotlin `namespace` / packages | `com.streamvault.*` | unchanged, to keep upstream merges clean |
| App name | StreamVault | StreamVault Mobile (all locales) |
| Update source | `Davidona/StreamVault-IPTV` releases | `FabulousTiger/StreamVault-Mobile` releases |
| Signing | upstream key | fork key; `OFFICIAL_SIGNING_CERT_SHA256` is computed from it at build time, so the build-verification row reads as verified for fork builds |
| Beta builds | push to `develop` | push to `main` |

The keystore is never committed. CI reads it from the `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` repository secrets (the same names upstream uses).

## Localization

Upstream requires every user-facing string to be translated into all advertised locales (see `AGENTS.md`). Fork-only exceptions: the About rows "Based on StreamVault" (wording required by the license) and the repository URLs are `translatable="false"`. New fork UI text follows the upstream rule.

## Planned architecture changes

### Background sync (roadmap phase 2)
- Today: catalog/EPG sync runs in WorkManager workers (`ProviderSyncWorker`, `SyncWorker`, Xtream/Stalker index workers) without `setForeground`, so Android and Samsung's battery manager defer or stop them in the background. Only downloads use a `dataSync` foreground service.
- Plan: promote catalog and EPG sync to foreground work (`dataSync` type) with a progress notification and Cancel action; screens observe Room and fill in as rows land instead of waiting on a sync gate; staged order (live → favourite EPG → VOD/series → full EPG); provider-aware parallel fetches; batch sizes tuned for the high tier in `CatalogSyncRuntimeProfile`.
- Measurement: the upstream `xtreamSyncBenchmark` build (`-PxtreamSyncBenchmark=true`) before and after, on a Samsung Galaxy S22 Ultra.

### Mobile UI (phase 3)
- Device detection already exists (`core/ui/device/TelevisionDevice.kt`). Plan: a UI mode setting (Auto / TV / Mobile) feeding a mobile shell with bottom navigation; screens are ported one at a time and fall back to the TV layout until done. Shared data, sync, player and sports layers stay untouched.

### Sports (phase 4)
- Upstream sports providers need keys (TheSportsDB rejects the free key `123`), so without one, events only come from EPG classification. Plan: add a no-key schedule source with official broadcasters per match, map broadcasters to provider channels, and merge with EPG programme matches.

### Logical channel model

Requirement: favorites, cross-provider linking, probe & rank and failover must allow several streams (SD/HD/HEVC/UHD, and the same channel on another provider) to belong to one logical channel, so the future unified-variants feature needs no rewrite.

Upstream already has the core of this in `domain/model/Channel.kt`:
- `LiveChannelVariant` carries a `logicalGroupId`, `providerId`, parsed `attributes` (resolution, codec, fps, HDR, region, language) and `observedQuality` (last width/height/bitrate/fps, success count).
- `Channel.selectedVariantId`, `qualityOptions` and `alternativeStreams` expose the variant choice; `liveChannelGroupingMode` / `liveVariantPreferenceMode` control grouping and the preferred variant, and backups carry `PortableVariantChoiceBackup(logicalGroupId, ...)`.

Fork rules built on that:
- Probe results are stored per variant (provider + stream id) and feed `observedQuality`; ranking works on a logical group's variants.
- Cross-provider links are stored as accepted mappings into one logical group (variants from different `providerId`s), never by merging provider rows.
- Favorites and reminders point at the logical group when one exists, else at the raw channel.
- Failover walks the group's variants in rank order, respecting each provider's connection limit.

### Probe & rank (phase 5)
- Ported from the YNOTV fork: probe matched channels serially per provider (one connection each), rank by fps ≥ 50, then height, then bitrate, < 100 kbps = no signal (sorted last), 30-minute probe cache with a log.
