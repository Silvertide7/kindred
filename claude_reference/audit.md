# Kindred audit (branch main, NeoForge 1.21.1)

Port of the 1-20-1 audit fixes and the vanilla UI (`1-20-1` commit `f087ec6`) onto main. `./gradlew build` passes. Sources: self-audit (S) and an independent reviewer (R).

## Ported from 1-20-1
- [x] `breakBond` refuses while a revival cooldown is pending (closes the `/kindred break` bypass).
- [x] Death handler runs at `EventPriority.LOWEST`, so deaths cancelled by other mods are ignored.
- [x] Walk-range check uses 3D distance.
- [x] Summon and cross-dim teleport stamp a fresh `Bonded` instead of reading the old attachment.
- [x] Roster-related C2S packets are rate-limited; state cleared on logout.
- [x] Roster sync strips heavy NBT keys before sending.
- [x] `/kindred` commands send a roster sync; summon command uses `SummonResult.isSuccess()`.
- [x] Client ownability gate removed (server decides; client hides the not-ownable deny text).
- [x] Failed preview builds cached until the next cache clear.
- [x] Scroll offset clamped each frame before hold processing; selection no longer reset on resize and picks a default when empty.
- [x] `ui.useVanillaUI` client config (default false), vanilla panel and buttons, `ButtonState`.
- [x] No drop shadows on roster screen text.
- [x] `requireSpace` config text corrected; CI artifact renamed; dead code removed (`isOwnable`, `clearKilledOffline`, `selectActiveOnOpen`, unused imports, `kindred.screen.break`).

## Not ported (not applicable on NeoForge)
- `orElseThrow` on corrupt `Bonded` data: attachments decode through NeoForge, which drops bad data instead of throwing, and `getData` returns a default.

## Port-specific changes (S)
- [x] Vanilla buttons use `blitSprite` with the `widget/button`, `widget/button_disabled` and `widget/button_highlighted` sprites. 1.21.1 has no `WIDGETS_LOCATION` or `blitNineSliced`, and `AbstractButton.SPRITES` is protected.
- [x] Preview NBT strip list uses 1.21.1 key names (lowercase `leash`).

## Reviewer findings (R)
- [x] R1 (high). The dying filter added to `BondEntityIndex.find` hid the entity from the permanent-death `breakBond`, because `LivingDeathEvent` fires after health reaches 0. The bond was never stripped, vanilla drops were cancelled despite the config text, and `pendingDisbands` leaked. Fix: filter removed from the index; summon checks for a dying pet itself.
- [x] R2 (medium). Summoning during the death animation went to `materializeFresh`, which failed on the duplicate UUID of the corpse. Fix: summon returns `REVIVAL_PENDING` ("Pet is respawning") while the pet is dying.
- [x] R3 (high). Move Up, Move Down and Set Active send `C2SRenameBond` and then the action in the same frame. The shared rate-limit bucket dropped the action. Fix: rate limit is now per player per packet type.
- [x] R4 (low). Stripping `NeoForgeData` and `neoforge:attachments` could make compat mobs (Jurassic Reborn, Familiars Lib) render as their default variant in the preview. Fix: those two keys are no longer stripped; they are small.

## 1-20-1 follow-up
- [ ] R1, R2, R3 and R4 also apply to the 1-20-1 branch (Forge keys there are `ForgeData` and `ForgeCaps`). Not yet fixed there; 1-20-1 1.2.0 is not tagged.

## Release
- [x] Version bumped to 1.3.0 (1.2.0 already released as tag `21.1.2.0`); CHANGELOG.md rewritten for 1.3.0.
- [ ] Visual check of the vanilla roster screen on 1.21.1.

## Petting interop
- [x] Petting sets the sneak flag when it sits a pet, which dims the nametag. Wolves matched only the vanilla adapter, so Petting's `sitstill` and the sneak flag survived a walk or teleport summon. Fix: `CompanionAdapters.wake` clears the sneak flag and runs every matching adapter's wake. Not yet tested in game.

## Feature: pet nametag visibility
- [x] Server config `bonding.alwaysShowPetNames` (default false, vanilla behavior: nametag shows only while looked at). Applied on rename and whenever a bonded pet loads, so pets named under the old always-visible behavior switch over. True restores the old always-visible nametags. Not yet tested in game.

## Audit: nametag toggle and summon wake
Self-audit (S) found no style or dead-code issues. Independent reviewer (R) findings, each checked against Petting's bytecode:
- [x] R-A (medium). Petting sits vanilla-tamed wolves too (its owner check accepts `TamableAnimal.isOwnedBy`) without adding `pettingtamed`, so the Petting adapter never matched and `sitstill` survived summon. Fix: every adapter's `wake` now runs and guards its own type; Petting's wake clears its keys on any entity.
- [x] R-B (medium). Petting's follow tick stops navigation and applies a -100% `petting_stop` speed modifier while `sitstill` or `waiting` is set. Wake only cleared `sitstill`, so a waiting pet could not walk to the player. Fix: wake removes `sitstill`, `waiting` and `freewander`.
- [x] R-C (low). Claim checked only the first matching adapter's owner. Fix: `CompanionAdapters.ownerOf` returns the first owner any matching adapter reports and is now what claim uses; `adapterFor` replaced by `isOwnable`.
- [x] R-D (low). Claiming a pet already named with a name tag did not apply `alwaysShowPetNames` until the next load. Fix: claim calls `BondService.applyNameVisibility`.
- [x] R-E (low). Pets released (break, pending disband, orphan cleanup) kept the old forced-visible nametag forever. Fix: release resets nametag visibility to vanilla.
- [ ] R-F (low, accepted). Changing `alwaysShowPetNames` while the server runs only affects loaded pets after they reload, are summoned or renamed.
- [ ] Note (accepted). Petting's separate area binding (`pettingbound`) is left untouched by summon; a pet bound to an area may walk back to it.
- [ ] In-game check: wolf sat via Petting then summoned; Petting pet in waiting mode summoned; nametag toggle both ways.
