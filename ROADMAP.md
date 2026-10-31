# Roadmap

Kept in sync with the maintainer's task list. Status: ✅ done · 🔄 in progress · ⏳ planned.

Test device: Samsung Galaxy S22 Ultra. TV-specific work waits unless it comes free with shared code.

## Phase 1: Fork and CI 🔄
- ✅ Fork, own application id, app name, update source
- ✅ README, fork notes, changelog, roadmap, issue templates
- ✅ Beta APK on every push to `main`; PR check workflow
- 🔄 Fork signing key in repository secrets
- 🔄 Branch protection on `main`

## Phase 2: Background sync and performance ⏳
- Measure the baseline on the S22 (first sync of large Xtream providers: ~56k channels, ~400k movies)
- Sync as foreground work with progress notification and Cancel; keeps running with the app closed or the screen locked
- Non-blocking UI: sync status chip, every section usable during sync
- Staged import: live channels first, then favourite EPG, then VOD/series and full EPG
- Parallel fetches where the provider allows; larger batches on high-tier devices
- One-time Samsung unrestricted battery prompt
- Measure again and compare

## Phase 3: Mobile layout and navigation ⏳
- UI mode setting: Auto / TV / Mobile
- Mobile shell with bottom navigation (Live, Sports, Movies, Series, Search)
- Ported screens in order: Live TV, player (gestures, picture-in-picture, landscape), Search, Sports

## Phase 4: Sports ⏳
- No-key schedule source with official broadcasters (fixes missing Premier League)
- Broadcaster to channel matching with a star marker
- EPG programme title/description search for the teams
- Merged channel list: airing now, upcoming, name-only

## Phase 5: Probe and rank ⏳
- Probe matched channels, rank by fps, resolution, bitrate; dead streams last
- Probe cache, re-probe, exportable log
- Background probing with a progress chip; auto-pick after ~10 probes
- Later: find the same channel on the other provider while watching

## Later
- Download folders and Sonarr/Radarr naming
- EPG source yield summary to prune dead guide URLs
- TV-specific fixes
