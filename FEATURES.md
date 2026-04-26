# Mount Summon — Possible Features

Catalog of features to consider, grouped by scope. Treat tier 1 as the MVP definition; everything below is opt-in. Not a roadmap — a menu.

---

## Tier 1 — Core (MVP)

The minimum to ship something that beats CallableHorses on day one.

- **Multi-mount roster**, configurable max (default 5). CallableHorses caps at one — this is the headline differentiator.
- **Keybind opens a custom screen.** No items, no commands required for normal play. Default key unbound (let the user pick) to avoid conflicts.
- **Claim flow**: from the screen, arm "claim next interaction"; right-click a bondable mount within N seconds to bond.
- **Break-bond flow**: per-row button with two-step confirm (alchemical-style 3s arm window).
- **Summon button** per row → mount walks to you if close, teleports if far in same dim, materializes from stored NBT if in another dim or unloaded.
- **Cross-dimensional summon** with safe handling — store mount as data when its owner leaves the dim, materialize fresh on summon.
- **Anti-dupe** via per-bond revision counter in player attachment + world `SavedData`. Cancel `EntityJoinLevelEvent` for stale revisions.
- **Persistence across player death** — `AttachmentType.copyOnDeath(true)` so the roster survives respawn.
- **Datapack tag** `#mountsummon:bondable` for which entity types can be bonded. Defaults: all `AbstractHorse` (horse, donkey, mule, skeleton, zombie), camels, llamas.
- **Server-side configs** (`ModConfigSpec`): `maxBonds`, `walkRange`, `walkSpeed`, `summonCooldownTicks`, `crossDimAllowed`, `claimWindowSeconds`, `deathIsPermanent`, `autoMount`, `requireSpace`.
- **Whistle sound + particles** on summon (server-broadcast S2C ack).
- **Lang file** with all user-facing strings under `mountsummon.*`.

---

## Tier 2 — Polish & quality of life

Nice things that make it feel finished, but you can ship without them.

- **Rename mounts** from the screen (inline edit; click name, type, save). Stored in `Bond.displayName`, syncs to entity custom name.
- **Mount preview in the row** — render the entity model at small scale on the left of each row using `InventoryScreen.renderEntityInInventoryFollowsMouse` style or a static front-3/4 view.
- **Quick stats** per row: speed, jump, max HP, current HP%. Computed from stored NBT.
- **Reorder rows** by drag, or with up/down buttons. Stored as a position int per bond.
- **Set primary mount** (star icon) — a separate "summon primary" hotkey bypasses the screen entirely.
- **Search / filter** when many mounts (input field at top, fuzzy on name + type).
- **Cooldown indicator** — radial sweep over the summon button using `g.fill` arcs while on cooldown.
- **Last-known-location** display per row — dimension and approx coords, "X seconds/minutes ago".
- **Sound packs** — datapack-overridable `mountsummon:summon.whistle` per mount type.
- **Keybind for "summon last used"** — fast re-summon without opening the screen.
- **Auto-mount toggle per bond** (overrides global config) — for players who want one combat mount that auto-seats and one packhorse that doesn't.
- **Confirm dialog when breaking a bond on a mount carrying a chest with items** — prevent accidental loss.

---

## Tier 3 — Bigger features

Worth doing if you commit to the mod long-term.

- **Stable / barn block (optional)** — if you change your mind on "no items/blocks": a block that displays roster mounts as live entities, walking around in pens. Pure visual; bonds are still the source of truth. Disabled by default.
- **Mount equipment persistence** — saddle, armor, chest contents. Already covered if you snapshot full NBT, but explicitly tested as a feature.
- **Revival cost on permadeath** — when `deathIsPermanent=true`, optionally allow revival by consuming a configurable item (gold/diamond/totem/datapack-defined ingredient list).
- **XP / leveling per mount** — accumulate XP from time ridden / blocks traveled, unlock perks (extra inventory slot, fall-damage absorb, jump boost).
- **Mount perks / traits** — random or rare traits (Surefooted: no fall damage, Frostpaws: doesn't slip on ice, Beast of Burden: +chest slots). Datapack-defined.
- **Trust / loyalty stat** — feeding, riding, brushing increases loyalty; high-loyalty mounts come faster, low-loyalty mounts ignore the first whistle.
- **Multi-player bonds** — co-bond a mount to a party so both players can summon. Permission-gated.
- **Banned-zone tag** `#mountsummon:no_summon_dimensions` and `#mountsummon:no_summon_biomes` — datapack control over where summoning is allowed (e.g. block PvP arenas, the End fight).
- **Claim by lasso** — optional alternate claim flow via a lasso item, for players who'd rather have the bind action be physical. (Conflicts with the "no items" rule — leave off by default.)
- **Camel double-seat + llama caravans** — preserve attached llamas in a caravan when summoning the leader.
- **Mount loadouts** — save a mount's saddle/armor/chest layout as a named loadout, swap presets from the screen.

---

## Tier 4 — Server / admin features

Needed once people deploy this on multiplayer.

- **Permissions** via NeoForge `PermissionAPI` nodes: `mountsummon.claim`, `mountsummon.summon`, `mountsummon.break`, `mountsummon.bypass_cooldown`, `mountsummon.admin.list`, `mountsummon.admin.transfer`.
- **Admin commands**:
  - `/mountsummon list <player>` — show bonds.
  - `/mountsummon transfer <bondId> <newOwner>`.
  - `/mountsummon revoke <bondId>` — force-remove.
  - `/mountsummon find <bondId>` — print last-seen dim + pos.
- **Audit log** of bond/break/summon events to a per-world log file (opt-in).
- **Per-dimension summon allow/denylist** in config (legacy CallableHorses had this — keep it).
- **Rate limit per player** to prevent spam (server config). Distinct from per-bond cooldown.
- **Concurrent-summon cap** — limit how many mounts a player can have materialized at once if you ever support multi-active mounts.

---

## Tier 5 — Compatibility & integrations

- **Curios / Trinkets** — if a "summon whistle" item is ever added, support equipping in a trinket slot. (Default mod has no items, so this is conditional on Tier 3 stable/whistle.)
- **JEI / EMI** — only relevant if items/recipes get added.
- **Carry On / Pickup** — ensure being carried doesn't desync ownership. (Test, don't necessarily code for.)
- **Iron's Spells & Spellbooks / Apotheosis** — mount enchantments on saddles. Probably nothing to do; just make sure NBT round-trips.
- **FTB Chunks / OpenPAC / claim mods** — replace CallableHorses' fake-event hack with real claim API queries. Soft-dep where possible.
- **CompactMachines / dimension-bridging mods** — make sure cross-dim summon respects their dimension types.
- **Citadel / GeckoLib mounts** — many modded horse-likes are not `AbstractHorse`. The datapack tag handles this — verify NBT snapshot/restore preserves the GeckoLib animation state slot.
- **Mod menu / Configured** — `ModConfigSpec` already integrates; just make sure category labels are translated.

---

## Tier 6 — Aesthetic / fun

Cheap wins that delight users.

- **Custom whistle melodies per mount** — datapack-defined note sequences.
- **Particle trail on summoned mount** for a few seconds (color configurable per bond).
- **Recall animation** — mount fades in with smoke or sparkles instead of popping.
- **Mount portrait in screen** — render entity head with `EntityRenderDispatcher` at scale 0.5 in the row.
- **Achievements / advancements**: bond first mount, bond max roster, summon across all dimensions, ride a bonded mount 10km.
- **Stats command** — `/mountsummon stats` shows total distance ridden per mount, summons used.

---

## Explicitly out of scope (don't build these)

- A bind item / whistle item that the player carries. The whole point of the redesign is the keybind+screen. (Reconsider only if Tier 3 lasso comes up.)
- Player-vs-player mount stealing.
- Auto-breeding from the screen.
- Anything that requires editing vanilla horse AI globally — only modify entities the player has bonded.

---

## Decision log to fill in

When picking what to ship, note here:

- What's in v1.0?
- What's the next milestone after v1.0?
- Any features above that turned out infeasible — and why?
