## 1.0.2
---
### Added
- Hold-to-bond on the Bind button — short press doesn't fire, prevents accidental claims.

### Changed
- Summoning a pet now clears some effects (fire, freeze ticks, fall distance, air). Rescuing a burning pet actually puts it out.
- Hold progress bar interpolates between server ticks; renders smoothly at high framerates instead of stepping every 50ms.

### Fixed
- Per-player summon cooldowns no longer carry over between worlds in single-player.
- Several deny messages (no-such-bond, not-loaded, etc.) now render correctly instead of falling back to raw translation keys.

## 1.0.1
---
### Added
- Bond allowlist / denylist entity tags (replaces the old `cant_bond` tag) for controlling which mobs can be bonded.
- Config option to allow summoning while swimming (`allowWaterSummon`, default on). Pet spawns at the player's position when no land footprint is available.

### Changed
- Cross-dimension and same-dimension-far summons now route through vanilla's `Entity.changeDimension`, preserving mod-attached state better. Mods that explicitly opt out of cross-dim transfer are respected — the summon fails cleanly rather than corrupting the entity.
- Roster screen reflects cap changes (e.g. PMMO level-ups) immediately on open instead of lagging by a sync round-trip.

## 1.0.0
---
Initial Release.

### Highlights
- Bond, summon, dismiss, and break system for tameable pets, with a roster screen.
- Summon keybind, hold-to-confirm gates on summon / dismiss / break.
- Active pet selection, per-bond rename, manual reordering.
- PMMO integration: gate bond claims behind a configurable skill, with `ALL_OR_NOTHING` and `LINEAR` cap modes.
- Optional XP-level cost per bond claim.
- Biome and dimension tags for blocking summons in specific areas.
- Per-bond and per-player summon cooldowns; revival cooldown for non-permanent deaths.
- Cross-dimension summon support (configurable).
- Death handling: permanent or revivable, with pre-drop snapshots so revived pets keep their gear.
- `BondClaimEvent` for external mods / datapacks to cancel claims.
- Configurable max bonds, saddleable-only mode, summon space requirement, walk vs. teleport range.
