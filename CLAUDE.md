# Mount Summon

NeoForge mod for Minecraft 1.21 that lets a player bond multiple mounts and recall any of them from anywhere via a custom GUI opened by a keybind. There are **no items** — bonding, summoning, and management are all driven by a screen.

Inspired by [Tschipp/CallableHorses](https://github.com/Tschipp/CallableHorses/tree/1.20) (1.20.1 Forge), but rewritten for NeoForge 1.21+ APIs and extended to support multiple mounts and any rideable entity (configurable). UI patterns are inspired by [Silvertide7/alchemical](https://github.com/Silvertide7/alchemical) — procedurally drawn screens (no PNG backgrounds), color palette as constants, eased animations.

## Project metadata

- Mod ID: `mountsummon`
- Group / base package: `net.silvertide.mountsummon`
- Main class: [MountSummon.java](src/main/java/net/silvertide/mountsummon/MountSummon.java)
- Java: 21
- Minecraft: 1.21
- NeoForge: 21.0.167 (NeoForge's version scheme: major version tracks the MC minor — `21.x.y` covers the 1.21.x line)
- Parchment: `2024.11.10` for 1.21
- Build: NeoForge ModDevGradle plugin 2.0.141, Gradle wrapper 9.2.1

## Build & run

- `./gradlew runClient` — dev client
- `./gradlew runServer` — dev server (`--nogui`)
- `./gradlew runGameTestServer` — gametest server
- `./gradlew runData` — data generators (output at `src/generated/resources/`)
- `./gradlew build` — produces `build/libs/mountsummon-<version>.jar`

Mod metadata lives in a **template** at [src/main/templates/META-INF/neoforge.mods.toml](src/main/templates/META-INF/neoforge.mods.toml) — `${mod_id}` etc. are expanded by the `generateModMetadata` task on every IDE sync. Edit the template, not a generated copy.

## Design overview

### Persistence — use NeoForge Data Attachments, not Forge capabilities

CallableHorses used Forge `Capability` + `LazyOptional` on both the player and the horse. NeoForge 1.21 deprecates that in favor of `AttachmentType` with codecs.

- **Player attachment** (`BondRoster`): list of `Bond` records, each holding `bondId` (UUID), display name, entity type, full mount NBT snapshot, last-seen `dimension`+`pos`, and a monotonically-incrementing `revision` (anti-dupe token). Codec-serialized; `.copyOnDeath(true)` so respawn keeps bonds.
- **Entity attachment** (`Bonded`): `bondId`, `ownerUUID`, `revision`. Travels with the entity NBT automatically.
- **Server `SavedData`** (`MountSummonWorldData`): authoritative `bondId → revision` map for cross-dim/offline anti-dupe. On `EntityJoinLevelEvent`, cancel any bonded mount whose stored revision is below the world-data value.

Why: codec attachments serialize cleanly across dim/respawn, no `LazyOptional` lifecycle bugs, and `copyOnDeath` removes the manual `Clone` event copy CallableHorses needed.

### Summoning logic

1. Client clicks "Summon" on a row → `C2S_SummonBond(bondId)` packet.
2. Server checks roster for `bondId`. If not present → reject.
3. Look up the bond's `lastSeenDim`. Index live entities by `bondId` per dimension (maintain via `EntityJoinLevelEvent` / `EntityLeaveLevelEvent` into a `Map<UUID, EntityRef>`) — **do not iterate every entity in every dim** like CallableHorses does.
4. Resolve:
   - **Same dim, within `walkRange`** → set follow target, pathfind to player.
   - **Same dim, far** → `entity.teleportTo(player.getX(), player.getY(), player.getZ())`.
   - **Other dim** → snapshot NBT + `discard()`, increment revision, spawn fresh copy at player using `EntityType.create` + `loadFromTag`. Update world data so any straggler in the old dim gets cancelled on next chunk load.
   - **Not loaded anywhere** → spawn from stored NBT, increment revision.
5. After spawn, optionally seat the player if `autoMount` config is on.
6. S2C ack to play summon sound/particle on the caller.

Cross-dim handling avoids the despawn-and-respawn quirk by treating offline mounts as data-only — the entity is only materialized when summoned.

### Bonding flow

There is **no bind item or bind keybind**. The only player input outside the GUI is the open-screen keybind.

1. Player presses keybind → screen opens. Server sends a roster snapshot via `S2C_RosterSync` so the screen has up-to-date data.
2. Screen has a "Claim Next Mount" affordance when there are free slots. Clicking it sets a server-side flag `awaitingClaim` on the player (with a short timeout, e.g. 30s).
3. Next time the player right-clicks a `#mountsummon:bondable` entity (datapack tag), the server consumes the flag, writes a `Bonded` attachment to the entity, appends a `Bond` to the player's roster, and pushes the updated roster.
4. Per-row "Break Bond" uses a two-step confirm (alchemical pattern): first click arms a 3s timer + recolors the button; second click within the window sends `C2S_BreakBond(bondId)`. Server clears entity attachment (if reachable) and removes the roster entry.

### GUI architecture (alchemical-inspired)

- `MountsScreen extends Screen` — no slots, no items, so no `AbstractContainerScreen` / `Menu` needed. Plain `Screen` plus payload packets.
- **No PNG textures** in `assets/mountsummon/textures/gui/`. Every pixel is `GuiGraphics.fill(...)` — borders, slot rects, dots, glyphs.
- ARGB palette as `static final int` constants (`C_BORDER`, `C_ROW_HOVER`, `C_BREAK_IDLE/HOVER/CONFIRM`, `C_EMPTY`, …) with a `withAlpha(color, a)` helper.
- Animation via `System.currentTimeMillis()` deltas + cubic ease-out (`easeOut(startMs, durationMs)` returns 0..1) for row slide-in / fade.
- Hand-rolled buttons via `drawButton(g, text, x, y, color, alpha)` + `isOverButton(...)`. Hit-test rects per row stored in a parallel list so `mouseClicked` can map clicks to row index.
- Two-step destructive confirm pattern for "Break Bond" (alchemical's `confirmPending` + `CONFIRM_TTL`).
- Scrollable list (alchemical does **not** have one — we do): `scrollOffset` int, override `mouseScrolled`, wrap row drawing in `g.enableScissor(...)` / `g.disableScissor()`.
- Validation enum on the menu side (`CAN_CLAIM`, `AT_CAPACITY`, `BOND_OFFLINE`, …) → tooltips when actions are disabled.

### Networking

Use NeoForge's `PayloadRegistrar` (registered in `RegisterPayloadHandlersEvent` on the mod bus). Packets:

- `C2S_OpenRoster` — request roster on screen open.
- `C2S_BeginClaim` — arm the next-interact claim.
- `C2S_BreakBond(bondId)` — second-click confirm.
- `C2S_SummonBond(bondId)` — fire summon.
- `S2C_RosterSync(List<BondView>)` — full roster (compact view: id, name, type, dim, alive, lastSeenAgoMs).
- `S2C_SummonResult(bondId, status)` — for client-side feedback.

Keep all client-only handlers in a separate class (`ClientPacketHandler`) so dedicated servers never classload it (alchemical pattern).

### Keybinds

Single keybind: `key.mountsummon.open_roster`, registered in `RegisterKeyMappingsEvent` (mod bus, client only). Polled via `ClientTickEvent.Post` or `InputEvent.Key`. On press → open `MountsScreen` and send `C2S_OpenRoster`.

### Config

NeoForge `ModConfigSpec` (server-side ModConfig — affects gameplay, must agree across server/client):

- `maxBonds` (int, default 5) — roster slot count.
- `bondableEntityTag` (string, default `mountsummon:bondable`) — datapack entity-type tag of allowed mounts.
- `walkRange` (double, default 30.0) — within this radius the mount walks instead of teleporting.
- `walkSpeed` (double, default 1.8).
- `crossDimAllowed` (bool, default true).
- `summonCooldownTicks` (int, default 100).
- `claimWindowSeconds` (int, default 30).
- `deathIsPermanent` (bool, default false) — if false, dead mounts respawn on next summon at full health.
- `autoMount` (bool, default false) — seat player on summon.
- `requireSpace` (bool, default true) — refuse summon if 3×3×3 around player is unsafe.

### Datapack tag

`data/mountsummon/tags/entity_types/bondable.json` — defaults to all `AbstractHorse` subtypes (horse, donkey, mule, skeleton/zombie horse, camel, llama). Datapack overrideable so users can add modded mounts.

## Source layout (current + planned)

```
src/main/java/net/silvertide/mountsummon/
├── MountSummon.java                     (mod entrypoint — registers everything)
├── attachment/
│   ├── BondRoster.java                  (player attachment record + codec)
│   ├── Bond.java                        (single-bond record + codec)
│   └── Bonded.java                      (entity attachment record + codec)
├── client/
│   ├── ClientSetup.java                 (RegisterKeyMappingsEvent, screen registration)
│   ├── ClientPacketHandler.java         (server-only safe — only loaded on Dist.CLIENT)
│   ├── input/Keybinds.java
│   └── screen/
│       ├── MountsScreen.java
│       └── ui/                          (drawButton, palette, easing helpers)
├── network/
│   ├── Networking.java                  (PayloadRegistrar wiring)
│   └── packet/                          (C2S_*, S2C_* records implementing CustomPacketPayload)
├── registry/
│   ├── ModAttachments.java              (DeferredRegister<AttachmentType<?>>)
│   └── ModSounds.java
├── server/
│   ├── BondManager.java                 (claim / break / summon — pure logic)
│   ├── BondIndex.java                   (per-dim Map<UUID, Entity> live index)
│   ├── MountSummonWorldData.java        (SavedData — revision counters)
│   └── events/
│       ├── EntityEvents.java            (join/leave level, interact, death)
│       └── PlayerEvents.java            (login, logout, respawn)
└── config/
    └── Config.java                      (ModConfigSpec)

src/main/resources/
├── assets/mountsummon/lang/en_us.json
├── data/mountsummon/tags/entity_types/bondable.json
└── pack.mcmeta (auto)
```

(Today only `MountSummon.java` exists. Everything else is the plan.)

## Conventions

- **Records over classes** for attachments, packets, and view DTOs. Codecs alongside the record.
- **All gameplay logic on the server**; the client only renders and dispatches packets.
- **No items, no blocks, no creative tab.** If you reach for `DeferredItem`/`DeferredBlock`, stop and reconsider.
- **No GUI textures.** Procedural draw only.
- **Datapack-first** — bondable entities are a tag, not a hardcoded list.
- **Lang keys** under `mountsummon.*` (`mountsummon.screen.title`, `mountsummon.bond.empty_slot`, `mountsummon.action.claim`, etc.). [en_us.json](src/main/resources/assets/mountsummon/lang/en_us.json) is empty — add keys as user-facing strings appear.

## Reference docs

- [FEATURES.md](FEATURES.md) — feature catalog (must-haves, niceties, stretch ideas) for scoping.
- CallableHorses 1.20 source: https://github.com/Tschipp/CallableHorses/tree/1.20
- Alchemical (UI patterns): https://github.com/Silvertide7/alchemical
- NeoForge docs: https://docs.neoforged.net/
- Parchment mappings: https://parchmentmc.org/docs/getting-started

## Known pitfalls (learned from CallableHorses)

- Don't scan all entities in all dims to find a mount — maintain an index.
- Don't store one shared keypress debounce across multiple keys.
- Don't fire fake `EntityInteract` / `AttackEntityEvent` to detect claim mods. Use a real protection API or skip.
- Reset `SavedData` indexing on save (their `i` field bug eventually loses entries).
- `PacketDistributor.NEAR` takes (x, y, z) — don't pass `getZ()` as y.
- Capabilities-equivalent (attachments) need `.copyOnDeath(true)` or roster vanishes on respawn.
