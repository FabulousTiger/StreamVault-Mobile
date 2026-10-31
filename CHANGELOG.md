# Fork Changelog

Changes specific to StreamVault Mobile. Upstream product changes stay in [docs/CHANGELOG.md](docs/CHANGELOG.md) (the release workflow reads release notes from there).

Format: one section per fork release, newest first. Groups: Added, Changed, Fixed, Upstream sync.

## [Unreleased]

### Added
- Fork documentation: README fork section, FORK_NOTES.md, ROADMAP.md, this changelog.
- Issue templates (bug report, feature request) and a PR check workflow.
- About screen links to the upstream StreamVault repository and to this fork.

### Fixed
- Backups exported by the official StreamVault release can be imported (their obfuscated field names are mapped, missing lists no longer crash inspection). All backup classes now keep stable field names in release builds.

### Changed
- Roadmap updated: phases 2-6 plus the future unified channel variants feature.
- Application id `com.streamvault.mobile` and app name "StreamVault Mobile", so the fork installs next to the official app.
- In-app update checker reads releases from `FabulousTiger/StreamVault-Mobile`.
- Beta workflow builds from `main` instead of `develop`.

### Upstream sync
- Based on upstream `master` at `b777a112` (StreamVault 1.2.2).
