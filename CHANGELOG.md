All notable changes to DimensionDaddy are documented here. The format follows Keep a Changelog, and this project uses Semantic Versioning.

## 2.0.0 (26.2) - 2026-07-02

### Changed
- Ported to Minecraft 26.2 and Fabric API 0.153.0+26.2
- No functional or behavioral changes from the 26.1.2 release; this is the same 2.0.0 feature set targeting a newer Minecraft version. Nothing in 26.2's own patch notes touches the world save layout, portal block handling, or command and permission APIs this mod depends on, so the port did not require any code changes

## 2.0.0 - 2026-07-02

### Added
- Unified command tree under a single `/DimensionDaddy` root, with `enable`, `disable`, `status`, `backup`, `restore`, `reset`, and `list` subcommands, each taking `overworld`, `nether`, `end`, or `all` as the target
- Overworld toggle support; previously only the End and Nether could be disabled
- Blocking of the Nether portal's return trip and the End's exit portal when the Overworld is disabled, in addition to the existing entry-side blocking
- Real world data backup, restore, and reset, covering the `region`, `entities`, `poi`, and per-dimension `data` folders for each dimension, rather than only an enabled and disabled flag
- A one-time Initial Backup, captured automatically the first time the mod runs on a world, preserving the world exactly as it was before the mod made any changes
- An in-game report broadcast the moment a scheduled restore or reset finishes applying, showing per-dimension file counts or the specific failure reason
- Retry logic with a short backoff on every file delete and copy operation, to avoid silent failures caused by brief file locks right after a world reload
- Tab completion for dimension names and known backup ids
- Support for Minecraft 26.1's restructured world save layout, where each dimension's data lives under `dimensions/minecraft/<dimension>/` instead of the previous root-level layout

### Changed
- Replaced the previous six separate commands, `enableend`, `disableend`, `endstatus`, `enablenether`, `disablenether`, and `netherstatus`, with the unified `/DimensionDaddy` command tree. This is a breaking change for anyone with the old commands saved in scripts, macros, or keybinds
- Backup storage changed from a single properties file per backup to a folder per backup, containing a metadata file plus copied world data
- Removed the automatic safety backups that were previously taken on every server startup and immediately before every restore or reset; the mod now only creates a backup automatically once, the Initial Backup, and otherwise only when explicitly requested

### Fixed
- Restore and reset previously only ever changed the enabled and disabled flag and never touched actual block or entity data on disk; they now genuinely restore or regenerate world data
- Fixed a bug where a partially completed Initial Backup left behind by an earlier failed run would permanently prevent the mod from ever creating a valid one; it now retries automatically on the next server start
- Fixed dimension folder paths to match Minecraft 26.1's new world save layout; the previous paths were left over from the pre-26.1 layout and caused every backup to silently capture no data at all
- Fixed a bug where a single file that could not be deleted, typically due to a brief lock immediately after a world reload, would cause the entire delete step to be silently treated as successful

## 1.0.1

### Added
- Initial release with separate operator commands to enable and disable the End and Nether dimensions independently
- Mixins blocking Nether and End portal usage when the corresponding dimension is disabled
- A properties file storing the enabled and disabled state of each dimension across restarts
