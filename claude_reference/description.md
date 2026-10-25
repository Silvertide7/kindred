<p style="text-align: center;"><img src="https://media.forgecdn.net/attachments/description/1526429/description_9d865479-26ae-4e39-ada5-cadf0be1707a.png" width="600" height="90" /></p>

<div><h3>Kindred turns your tamed pets or mounts into true companions. Bond a pet once, then call it to your side anywhere in the world, across dimensions, back from death, or even just a couple chunks away. No more leaving your wolf at home, losing your horse in a cave, or starting over every time something dies.</h3><p>Almost everything about this mod is configurable. Change it to fit your needs.</p><p>Visit the <a href="https://mods.silvertide.net/kindred" target="_blank" rel="nofollow noopener">website</a> for more info or to see some of my other mods.</p></div>

## Features

### Bond with any tamed pet

Look at one of your tamed pets, open the **Companion** menu (g by default), and bond to it.

<p style="text-align: center;"><img src="https://media.forgecdn.net/attachments/description/1526429/description_fa71df6f-a5a2-42c4-bbba-26468571a1d8.png" /></p>

Bonded pets are stored on you — not in the world. They survive logouts, chunk unloads, and dimension changes.

*   Works with any ownable, tameable mob out of the box (wolves, cats, parrots, horses, donkeys, mules, llamas, camels, foxes, axolotls — anything that implements vanilla ownership, including modded mobs).
*   Built-in compat for **Petting - tame any mob**, **Jurassic Reborn**, and **Familiars Lib**, so their pets can be bonded too.
*   How many pets you can bond is controlled by the `max_companion_bonds` attribute, so skill and perk mods can raise it.
*   Optional **mount-only mode** restricts bonding to saddleable mounts.
*   Optional **XP level cost** per bond — makes bonding feel earned, with the cost previewed before you confirm.
*   Optional **datapack allowlist / denylist** for species that should or shouldn't be bondable on your server.

<p style="text-align: center;"><img src="https://media.forgecdn.net/attachments/description/1526429/description_4850ef2c-e7c5-447e-baf3-cc156cae384e.png" width="75" height="75" /></p>

### Summon & Dismiss with a keybind

<p style="text-align: center;"><img src="https://media.forgecdn.net/attachments/description/1526429/description_b7b1f4c1-66c9-47c4-8f41-1815149aac82.png" /></p>

One key summons your active pet (v by default). Hold it again while near the pet to dismiss them back to a safe place. Open the roster with another key to switch active pet, rename, reorder, or break bonds.

*   **Hold-to-confirm** prevents misclicks — configurable hold duration for both summon and dismiss.
*   Smart placement: pets within range walk to you naturally; farther pets teleport to a safe spot beside you.
*   Refuses to summon when the space around you is obstructed (configurable).
*   Summon while swimming (configurable), handy for aquatic pets.
*   Cancels the hold if you take damage (configurable) — mirrors vanilla bow-draw / eating behavior.
*   Optional **cross-dimensional summons** (defaults on) so your pet follows you to the Nether and back.
*   Dismissing can be turned off entirely if your server wants pets to stay in the world.

### Auto-Mount

Summon your horse and you're already in the saddle. Rideable pets appear right under you and you're mounted automatically.

*   Toggle it per pet with the **Auto-Mount** checkbox in the Companions screen. On by default.
*   Pets that take a saddle have to be wearing one, so you never end up on a mount you can't steer.
*   Works with horses, donkeys, mules, camels, pigs, striders, and modded mounts built on the vanilla riding interfaces.
*   Sneak while the summon finishes to summon normally without mounting.
*   Only mounts you when there's room for both of you, so you won't get stuck in a ceiling.
*   Server owners can turn it off with a config switch, or exclude species with a datapack tag. Llamas are excluded by default since they can't be steered.

<p style="text-align: center;"><img src="https://media.forgecdn.net/attachments/description/1526429/description_78df839b-7762-4fef-8a76-1f42cca64752.png" /></p>

### Bring them back from the grave

By default, when a bonded pet dies, the bond doesn't break — it just goes dormant. Summon them again and they respawn at your side, gear and all.

*   **Soft death:** pet returns on next summon. Optionally add a revival cooldown to add some weight to death without going full permadeath.
*   **Permadeath mode:** flip a config switch and death breaks the bond for keeps.
*   **Loot drops** are off by default in soft-death mode, otherwise you could infinitely kill them for their loot, like leather. Re-enable for vanilla behavior, especially if you turn permanent death on.

### Companions GUI

A clean inventory-style screen for managing your bonds.

*   Live 3D preview of the selected pet.
*   Set active, rename, reorder (▲ / ▼), and break bonds with a hold-to-confirm.
*   Auto-Mount checkbox for rideable pets.
*   Per-pet summon cooldown indicator.
*   Respawn timer for pets in revival cooldown.
*   "Limbo" / "Resting" state hints so you always know where each pet is. Limbo is where they go when they die, and resting means they've been dismissed from the world.
*   Prefer the classic Minecraft look? Switch to the **vanilla-style UI** in the client config.

### Extremely customizable

Server behavior lives in `kindred-server.toml`, and client options in `kindred-client.toml`:

| Category  |Knobs                                                                                                                          |
| --------- |------------------------------------------------------------------------------------------------------------------------------ |
| <strong>Bonding</strong> |starting bonds per player, mount-only mode, XP level cost per bond, rename toggle, always-visible pet nametags                 |
| <strong>Summoning</strong> |walk vs. teleport range, walk speed, cross-dimensional toggle, space-required toggle, summon while swimming, Auto-Mount toggle |
| <strong>Cooldowns</strong> |per-bond summon cooldown, global per-player summon cooldown                                                                    |
| <strong>Death</strong> |permadeath toggle, loot drop toggle, revival cooldown                                                                          |
| <strong>Input</strong> |hold-to-summon seconds, hold-to-dismiss seconds, dismiss toggle, cancel-on-damage                                              |
| <strong>Client</strong> |hold bar position, vanilla-style UI                                                                                            |

### Datapack control

Five tags let server owners and modpack authors tune the experience without code:

*   `kindred:bond_allowlist` (entity types) — when non-empty, **only** species in this tag can be bonded and `bond_denylist` is ignored. Leave empty to fall back to denylist behavior.
*   `kindred:bond_denylist` (entity types) — species that can never be bonded. Only consulted when `bond_allowlist` is empty.
*   `kindred:auto_mount_denylist` (entity types) — rideable species that never get Auto-Mount. Ships with llamas and trader llamas.
*   `kindred:no_summon_dimensions` (dimension types) — dimensions where summoning is blocked.
*   `kindred:no_summon_biomes` (biomes) — biomes where summoning is blocked (great for boss arenas, dungeon biomes, PvP zones).

### Bond capacity attribute

The number of pets a player can bond is the `max_companion_bonds` attribute. Its base value comes from the `startingCompanionBonds` config, and any mod that adds attribute modifiers can raise or lower it.

This replaces the old [Project MMO](https://www.curseforge.com/minecraft/mc-mods/project-mmo) integration. You can use the attribute with Project MMO perks to get the same effect, or with any other mod (Pufferfish's Skills, etc.) to modify it.

### Commands

For server admins and debugging:

```
/kindred claim                — claim the entity at your crosshair
/kindred list                 — list your bonds with indices
/kindred summon <index>       — summon a bond
/kindred dismiss <index>      — dismiss a bond
/kindred break <index>        — break a bond
/kindred active <index|none>  — set or clear the active pet
```

### Translations

English and Simplified Chinese (thanks nil0bug).

***

## Why Kindred?

Vanilla pets are fragile, stationary, and easy to lose. Existing pet mods either turn them into items (which feels weird) or add a stable system (which feels like extra inventory management). Kindred takes a different angle: your pets live on **you**, not in the world, kind of like Pokemon. They're always one keypress away, they survive deaths, and the whole thing tunes from a config file so it fits any modpack — hardcore, casual, or RPG-progression.

## Keybinds

| Default |Action            |
| ------- |----------------- |
| v       |Summon active pet |
| g       |Open pet roster   |

Set them in **Options → Controls → Kindred**.

This mod was inspired by Callable Horses, which seems to not be moving past 1.20.1. I wanted to expand on it a little bit and include some new features and upgrades.