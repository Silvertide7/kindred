# Kindred audit (branch 1-20-1, Forge 1.20.1)

`./gradlew compileJava` passes. Sources: self-audit (S) and an independent reviewer (R).

## Medium
- [x] 1. (R) Petting-tamed mobs can't be bonded from the GUI: `BindFooter.java:191` calls `CompanionAdapters.isOwnable` client-side, but `PettingCompat.handles` reads `getPersistentData()`, which the client never receives. Only `/kindred claim` works.
- [x] 2. (R) `/kindred break` skips `isRevivalPending` (only `HoldEligibility.checkBreak` checks it), so a dead pet is revived via `materializeFresh` and can be re-claimed, bypassing `revivalCooldownSeconds`. Fix inside `BondService.breakBond`.
- [x] 3. (R) `EntityEvents.onLivingDeath` records `diedAt` even if another mod cancels the death afterwards. The pet then stays stuck behind the revival cooldown and its snapshots are skipped. Set the handler to `priority = LOWEST` so it only sees uncancelled deaths.
- [x] 4. (R) `BondService.summon` accepts an entity that is dying (`BondEntityIndex.find` only checks `isRemoved`), so it teleports the corpse, reports success and burns the cooldowns. Reject `isDeadOrDying`.
- [x] 5. (S) `S2CRosterSync` sends full entity NBT for every bond in one packet. Clientbound custom payloads are capped at 1 MiB in 1.20.1, so a large roster of NBT-heavy pets would disconnect the player. Trim the NBT to what the preview needs or split the packet.

## Low
- [x] 6. (R) No throttling on open-roster, rename, reorder or set-active. Each one triggers a full `sendRosterSync` with `saveWithoutId` on every loaded pet.
- [x] 7. (R) `PreviewEntityCache` doesn't cache failed builds, so it retries and logs every frame.
- [x] 8. (R) `RosterScreen` scroll offset isn't clamped when bonds shrink (the list looks empty after a break). `init()` resets the selection on every window resize.
- [x] 9. (R) `getBonded(...).orElseThrow()` at `BondService:289,351` and `EntityEvents:41,76,88` throws if the Bonded NBT is corrupt, which can crash the server tick. Handle the empty case.
- [x] 10. (R) Commands (`claim`, `summon`, `break`, `dismiss`) never call `sendRosterSync`, so the client view goes stale.
- [x] 11. (R) Walk-range check uses horizontal distance only, so a pet far above or below takes the WALKING path and may never arrive.
- [ ] 12. (S) Commands at permission level 0 bypass hold-to-confirm for summon and break. Decide whether that is intended and, if not, gate them.
- [ ] 13. (S) `KindredData.getRoster` decodes the whole roster, including every NBT snapshot, on every call. This is hot in `EntityLeaveLevelEvent` and packet handlers. Cache per tick or per player if it shows up in profiling.
- [ ] 14. (S) `pendingDisbands`, `diedWhileOffline` and `offlineSnapshots` entries leak if the entity never rejoins or the owner never logs in again.

## Dead code / cleanup
- [x] 15. (S) Unused: `KindredSavedData.clearKilledOffline`.
- [x] 16. (R) Unused imports in `HoldManager` (`ServerLevel`), `ServerPacketHandler` (`UUID`) and `RosterScreen` (`InventoryScreen`, `LivingEntity`).
- [x] 17. (R) Unused lang key `kindred.screen.break`. `kindred.dim.` is used through dynamic key building, so it is fine.
- [x] 18. (R) `KeybindHandler` has a private constructor whose only body is an unreachable trace log.
- [x] 19. (S) `KindredCommand.runSummon` hand-lists the success results instead of calling `SummonResult.isSuccess()`.
- [ ] 20. (S) About 300 comment/javadoc lines remain. The project rules say to delete them when touching the surrounding code.

## Docs / build
- [x] 21. (S) `CLAUDE.md` still describes NeoForge 1.21.1 (attachments, payload API, `neoforge.mods.toml`), but this branch is Forge 1.20.1 (capability-free persistent NBT, SimpleChannel, `mods.toml` in `src/main/resources`).
- [x] 22. (S) `.github/workflows/build.yml` artifact is still named `structure_shield-jar`.
- [x] 23. (R) Misleading docs: `PreviewEntityCache` javadoc (when it is cleared) and the `requireSpace` config text (says 3x3x3, the locator searches 5x5).
- [ ] 24. (S) No automated tests. The revision/dedupe logic in particular has none.

## Checked and fine
- Packet handlers run on the main thread via `consumerMainThread`.
- Revision-counter dedupe path, access transformer entry, tag directories, `mods.toml` dependencies.
- `dropLootOnDeath` is ignored when `deathIsPermanent=true`, and the config text says so, so it is documented behaviour.

## Not done
- Step 4 (the IDE Problems-tab export into `claude_reference/problems`) needs you to supply the file.

# useVanillaUI audit

`./gradlew compileJava` passes. Sources: self-audit (S) and an independent reviewer (R). 1.20.1 API use (`blitNineSliced`, `WIDGETS_LOCATION`, texture Y offsets, mouse scaling) was checked and is correct.

## Medium
- [x] V1. (S, R) `ScreenDrawUtil.drawVanillaButton` infers the disabled state from `textColor == BUTTON_TEXT_DISABLED_COLOR`. The "Active" button (`PreviewPane` set-active) looks enabled and lights on hover in vanilla mode. Fix: pass an explicit `enabled` boolean to `drawButton` and drop the magic-color check.
- [x] V2. (R) Vanilla mode ignores the `color` argument, so rename-while-editing looks idle and break-confirm has no red warning. The hold overlay `0x66FFFFFF` on the light gray texture is nearly invisible. Fix: use a darker or colored hold overlay in vanilla mode, and tint the confirm/editing buttons.
- [x] V3. (S, R) `drawVanillaButton` recomputes the mouse position from `mouseHandler` while every caller already has `mouseX`/`mouseY`. Pass hover in from the caller.

## Low
- [x] V4. (R) `C_XP_COST_UNAFFORDABLE` (`0xFFE57878`) on the `0xFFC6C6C6` panel is about 2:1 contrast.
- [x] V5. (S, R) Inactive star `0xFF4A5260` on vanilla row `0xFF373737` is almost invisible.
- [x] V6. (R) `panelMutedTextColor` `0xFF555555` on the panel is about 4:1, borderline for small text.
- [ ] V7. (S) The radial cooldown sweep (`C_PIE_FILL`) on the gray vanilla button has not been checked visually.
- [ ] V8. (R) `vanillaStyle()` reads the config on every draw call. Optionally cache once per `render`.

## Cleanup
- [x] V9. (R) Stray 8-space indent on `C_BTN_CLAIM` in `BindFooter` and `C_BTN_TEXT_DISABLED` in `PreviewPane`.
- [x] V10. (R) Inline vanilla row and separator hex literals in `RosterScreen` should be named constants.
- [x] V11. (R) `C_BTN_TEXT_DISABLED` aliases in `RosterScreen` and `PreviewPane` duplicate `ScreenDrawUtil.BUTTON_TEXT_DISABLED_COLOR`. Use the util constant directly.
- [x] V12. (R) New imports are out of alphabetical order in `ScreenDrawUtil`, `BindFooter` and `PreviewPane`.
- [x] V13. (R) Javadoc above `C_BTN_TEXT_DISABLED` in `RosterScreen` is on a line I touched. Per the style rules it should be deleted.

# Summon "puff, then no horse" audit (Icy's Better Horses 2.0.6)

**Status: accepted, not pursued.** The Icy's Better Horses adapter and the `prepareRebuilt` hook were removed on 2026-10-23. The analysis below is kept for reference.

Method: read Kindred's summon path, then decompiled the mod jar (`javap -c` on the Gradle-cached dep, closed source so this is bytecode reading, not source). Nothing was run in a client. Mod jar version is the one pinned in `gradle.properties` (`icys_better_horses_version=9022115`); the reporting user's version is unknown.

## What the puff proves
`playSummonFx` (POOF + chime) only runs after every success path in `BondService` (`teleportSameDim`, `teleportLoaded`, `materializeFresh`). So the server believed it placed a live entity. The horse then vanished, was moved away, or is not rendered. Kindred has no post-spawn check, so it reports success, burns both cooldowns, sets `dismissed=false`, and clears `diedAt` regardless.

## Hypotheses, ranked

### H1 Mod silently discards the rebuilt horse as a "stale copy" (mechanism confirmed, trigger unconfirmed)
- `HorseTracker.isStale` is true if (a) another live, registered body has the same identity, or (b) the horse's `BH_Generation` NBT is lower than the tracker's stored generation for that UUID.
- Checked on join (`IcysBetterHorses.onEntityJoinLevel` queues it, `discardStaleHorses` discards at server tick), every 100 ticks in `AbstractHorseMixin` (tickCount 0 included), and by `BhTwinWatch` every 600 ticks. All call `discard()` with no player message.
- Generation only rises when the mod's own whistle/call rebuilds a horse (`HorseManagement.respawnFromSnapshot`). So a Kindred snapshot taken before a mod whistle/call is permanently stale.
- Self-perpetuating: the discarded body fires `EntityLeaveLevelEvent`, Kindred's `snapshotEntity` re-saves the stale body into the bond, so every later summon repeats the puff.
- Log signature: `[whistle] discarding stale horse copy ... (generation X < Y)` or `[twin] discarding stale body ...`.
- Already mitigated in part: `IcysBetterHorsesCompat.prepareRebuilt` lifts the rebuilt horse's generation to the tracker value. Does not cover path (a) or a snapshot that is stale for other reasons.

### H2 Horse is commanded Return Home / Wander and leaves right after the summon (mechanism confirmed)
- `HorseReturnHomeGoal` walks the horse home and teleports it (`HorsePlacement.teleport`) after a 32-block walk or when stuck. `HorseWanderBoundsGoal` walks it back to its wander centre.
- `IcysBetterHorsesCompat.wake` only clears Stay. Return Home and Wander survive a summon, so the horse appears, then heads off.
- Needs the player to have used the mod's radial menu on that horse.

### H3 Stay survives a summon (fixed)
Horse stays where Kindred put it. Visible, so not a vanish. Fixed by the adapter's `wake`.

### H4 Vanilla Horse is auto-converted by the mod on its first tick (risk, probably works)
- `AbstractHorseMixin.bh_tick` calls `BhVanillaHorseSwap.trySwap` on every non-mod `Horse`: saves NBT, loads it into a new breed horse, removes the old with `CHANGED_DIMENSION`, `addFreshEntity`s the new one, and restores the old one if that fails.
- Kindred handles the swap through join/leave events, but the bond keeps `entityType = minecraft:horse`, and its snapshot flips between vanilla and breed-horse NBT. Any failure in the swap (including Kindred cancelling the new body's join) would look exactly like a vanish.
- Relevant to anyone who bonded a vanilla horse before installing the mod.

### H5 Kindred cancels the mod's own respawn (reverse interop bug, confirmed by reading both sides)
- Kindred's `EntityEvents.onEntityJoinLevel` cancels any bonded entity whose `Bonded.revision` is below the world revision.
- The mod's whistle rebuilds from its own stored snapshot, which contains the `Bonded` tag from the last time the mod recorded it. Every Kindred summon bumps the revision, so that snapshot is usually behind. The join is cancelled, the mod logs `failed to spawn respawned horse`, and the whistle silently does nothing for bonded horses.
- Not the reported symptom, but the same interop family.

### H6 Client cannot render the horse (unverified)
Mod uses GeckoLib-style custom renderers, plus `BhMountedHorseVisibility`. Entity would exist server-side. Check client log for renderer exceptions.

### Ruled out
- `dismiss` dropping items: mod's `bh_onRemoved` only cleans up breed-ability modifiers.
- `entity.saveWithoutId` being hijacked: the mod's "save as plain horse" hook is on `saveAsPassenger`, which Kindred never calls.
- Client preview entity polluting the mod's static tracker: `bh_setOwner` registers only when `!level.isClientSide`.
- Stale `BondEntityIndex` entry: `find` already drops removed and dying entities.

## Gaps in Kindred itself
- [ ] G1. No verification that a summoned entity survives. A success result with a missing entity is indistinguishable from a real success. Cooldowns are burned.
- [ ] G2. `snapshotEntity` on leave trusts any body, including one another mod just discarded, so a bad body overwrites a good snapshot.
- [ ] G3. `Bond.entityType` never updates when a mod swaps the entity class (vanilla Horse -> breed horse).
- [ ] G4. `wake` only clears Stay; Return Home and Wander are left in place.
- [ ] G5. `materializeFresh` bumps the world revision and stamps `Bonded` before `addFreshEntity`; if the add is rejected the bump is not rolled back (harmless today, but it culls any unindexed live copy).
- [ ] G6. No success-path logging, so none of this is diagnosable from a user's `latest.log`.

## Plan

### Step 0. Diagnose (no code; ask the user)
- [ ] Mod version, singleplayer vs server, horse type (vanilla or breed), bonded before or after the mod was added.
- [ ] Did they use the mod's radial menu (Stay, Return Home, Wander), whistle, or call on that horse?
- [ ] `latest.log` lines: `discarding stale horse copy`, `[twin]`, `failed to spawn respawned horse`, `cancelled stale duplicate`, `released orphaned copy`, `SPAWN_FAILED`.
- [ ] Right after a failed summon: `/data get entity @e[type=!player,limit=1,sort=nearest]` to see if a horse exists (invisible) or not (discarded).

### Step 1. Make failures visible (Kindred only, small)
- [ ] `SummonWatch`: after any successful summon, remember (entity, bondId, playerId). On the next tick and again ~20 ticks later, if `entity.isRemoved()`, log WARN with bond id, type, `getRemovalReason()`, position; tell the player (new lang key `kindred.summon.vanished`); undo `lastSummonedAt` and the global cooldown so they can retry.
- [ ] INFO log on every successful summon (type, path taken, position).

### Step 2. Stop the bleeding in the BH adapter
- [ ] `wake`: set Follow for any command other than Follow (covers Stay, Return Home, Wander).
- [ ] New adapter hook `shouldSnapshot(entity)`; BH implementation returns `!HorseTracker.isStale(horse)`. Used by `EntityEvents.snapshotEntity` (fixes G2, breaks the self-perpetuating loop).
- [ ] Keep the generation sync already added in `prepareRebuilt`.

### Step 3. Keep the bond truthful
- [ ] In `snapshotEntity`, if the live entity's registry type differs from `Bond.entityType`, write the new type (needs `Bond.withEntityType`). Fixes G3 and stops the vanilla/breed flip-flop.

### Step 4. Decide on the two-way conflict (needs a product decision)
- [ ] H5: either (a) accept that the mod's whistle/call does not work for Kindred-bonded horses and say so in the README, (b) let the adapter veto Kindred's stale-revision cull when the mod's own rebuild is the source, or (c) re-stamp revision from the mod side. (b) weakens dedupe; (a) is the cheapest.

### Step 5. Verify (user runs these in a client; compile-only on my side)
1. Bond a fresh breed horse, dismiss, summon.
2. Bond, travel far so it unloads, summon.
3. Set Stay / Return Home / Wander in the mod menu, then summon.
4. Use the mod's whistle, then Kindred summon, and the reverse.
5. Bond a vanilla horse with the mod installed, summon twice.
6. Cross-dimension summon.
7. Log out and back in, then summon.
Expected: horse visible after every step, and no `discarding stale horse copy` / `vanished` lines in the log.

### Order
Step 0 and Step 1 first, because they tell us which hypothesis is real. Step 2 is cheap and safe regardless. Step 3 is a general fix. Step 4 waits on the answer to Step 0.

## Review status
Self-audit only. The independent reviewer pass (`/code-review`) has not been run, since there is no fix diff beyond the small adapter yet. Run it after Steps 1-3.
