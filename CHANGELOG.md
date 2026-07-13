## 1.1.0
---
- Added a new attribute, max_companion_bonds.
  - You can now control how many bonds a player has access to using this attribute.
  - This attribute's initial base value is driven by the config setting. Whenever a player logs in it will set the attributes base to the config settings value if it is not that already.
  - This means you should be using attribute operations, not modifying the base value, which is standard attribute procedure anyway, just thought I'd point that out.
- Removed PMMO integration entirely, this can now be handled much more cleanly using the attribute instead of custom coding it.
- Config migration note: the `maxBonds` setting was renamed to `startingCompanionBonds`. If you had customized `maxBonds`, re-apply your value under the new name — the old entry is ignored and the cap will otherwise reset to the default (10).