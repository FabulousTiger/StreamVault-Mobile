# Roadmap

Kept in sync with the maintainer's task list. Status: ✅ done · 🔄 in progress · ⏳ planned · 💤 future (not scheduled).

Test device: Samsung Galaxy S22 Ultra. TV-specific work waits unless it comes free with shared code.

## Phase 1: Fork and CI ✅
- ✅ Fork, own application id, app name, update source
- ✅ README, CHANGELOG, ROADMAP, FORK_NOTES; attribution and license requirements kept
- ✅ Issue templates, PR check, branch protection on `main`
- ✅ Signed beta APK on every push to `main`
- ✅ Import of backups made by the official release build

## Phase 2: Sync, performance, typing 🔄 (highest priority)
- Foreground sync with progress notification and Cancel; keeps running with the app closed or the screen locked
- Non-blocking UI during sync: status chip, every section usable
- Incremental loading: Live → EPG → Movies → Series
- Import performance (measured before and after on the S22)
- Samsung battery optimization guidance (one-time prompt)
- Typing and search focus fixes: no dropped letters, no cursor jumps, no focus stealing
- Fast reopen: cached lists and guide, changes fetched in the background
- Hide junk rows: `#### HEADER ####` separators, 24/7 loops, radio

## Phase 3: Mobile usability ⏳
- UI mode: Auto / TV / Mobile
- Bottom navigation, touch-friendly lists and dialogs
- Player gestures (volume, brightness, seek), picture-in-picture
- Mobile data quality limits (separate max quality for mobile data and Wi-Fi)
- Global search across channels, guide, movies, series and sports

## Phase 4: Sports ⏳
- ESPN (no key) and OpenLigaDB as default schedule sources; optional free keys for football-data.org and Highlightly
- Official broadcaster badges
- EPG team matching, merged channel list
- Android notification reminders: single match and team reminders, with a quick "Watch" action

## Phase 5: Probe, rank and failover ⏳
- Probe matched channels; rank by fps, resolution, bitrate; dead streams last
- Cross-provider channel linking: automatic detection, user confirmation, mappings stored permanently
- Automatic failover to a linked channel
- Best-provider selection

## Phase 6: Distribution and support ⏳
- Update channels: Stable / Beta / Nightly
- Sanitized log export and sharing

## Future 💤 Unified channel variants
Show SD / HD / HEVC / UHD variants of a channel as one logical channel with a quality picker (Auto, SD, HD, HEVC, UHD). Auto weighs provider ranking, bandwidth, user preferences and device codec support. Settings: prefer highest quality, prefer HEVC, data saver, max mobile quality, max Wi-Fi quality.

Not implemented yet, but favorites, channel linking, probe & rank and failover must be designed so several streams can belong to one logical channel later (see [FORK_NOTES.md](FORK_NOTES.md#logical-channel-model)).
