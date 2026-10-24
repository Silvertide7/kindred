## 1.4.0
---
- New Auto-Mount: rideable pets (saddled, if they take a saddle) are summoned right under you and you're mounted automatically
- Toggle Auto-Mount per pet with the new checkbox in the roster screen; on by default
- Sneak while a keybind summon completes to summon without mounting
- New server config `allowAutoMount` (default true) turns the feature off and hides the checkbox
- New entity tag `kindred:auto_mount_denylist` excludes entity types from Auto-Mount; llamas and trader llamas are in it by default
- Fix pets sitting in a boat or minecart snapping back to it after being summoned
- With `requireSpace` off, cross-dimension and revived summons now look for a free spot before falling back to your position
- Network protocol bumped: clients and servers must both run 1.4.0
