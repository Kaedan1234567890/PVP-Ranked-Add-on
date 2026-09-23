# Chill Zone PvP Rank Admin

Operator-only administrative add-on for the exact Combat-Ranked Fabric 26.2 mod inspected from the server.

## Requires
- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API
- Combat-Ranked (`mod id: combat`)

## Permission
Only Minecraft server operators (OPs) and the server console can use `/pvprank` commands. Non-OP players cannot use or modify these controls. LuckPerms is not required by this add-on in 0.1.7-alpha.

If desired later, Owner-group/LuckPerms access can be added back as an additional permission path.

## Commands
- `/pvprank on` — turn the PvP ranking system on and restore the saved Top 10.
- `/pvprank off` — pause the PvP ranking system, preserve the current Top 10, and keep live ranks cleared while off.
- `/pvprank toggle` — switch the PvP ranking system between on and off.
- `/pvprank status` — show whether PvP rankings are currently on or off.
- `/pvprank set <player> <1-10>` — move an online player to that rank; other ranks shift to keep positions unique. Requires rankings ON.
- `/pvprank remove <player>` — remove an online player from rankings and keep them unranked until an operator uses `set` or `reset`. Requires rankings ON.
- `/pvprank swap <player1> <player2>` — swap two online players' current rank positions. Requires rankings ON.
- `/pvprank reset <player>` — re-enable the player and move them to the bottom of the current rankings. Requires rankings ON.
- `/pvprank list` — list live rankings while ON, or the preserved saved order while OFF.
- `/pvprank nametag toggle` — flip between compact and full nametag style.
- `/pvprank nametag compact` — use `[#2]`.
- `/pvprank nametag full` — use Combat-Ranked's native `[Rank #2]`.
- `/pvprank nametag status` (or `/pvprank nametag`) — show the current nametag style.
- `/pvprank resetall confirm` — intentionally clear every live and saved rank.

All player arguments suggest ONLY players currently online. This naturally includes Java and Bedrock players that appear in the server's normal online player list.

### PvP on/off behavior
`OFF` is a pause, not a destructive reset. The add-on saves the current Top 10 before turning off, clears Combat-Ranked's live ranking table, and continuously removes any ranks Combat-Ranked tries to create while the system is off. Turning the system back `ON` restores the saved Top 10 and then normal Combat-Ranked kill-based rank swaps continue as before. The on/off choice is saved in `config/chillzone-pvprank-admin/settings.json` and survives restarts.

## Compatibility approach
This add-on does not modify or redistribute Combat-Ranked. It talks to the exact public runtime classes/fields found in the uploaded Combat-Ranked 1.0.0 Fabric 26.2 JAR:
- `com.combat.DataManager`
- `com.combat.PlayerData`
- `com.combat.CombatMod.updatePlayerNametag`

Combat-Ranked stores its data at `config/combat/combat_data.json`.

## 0.1.1 restart-persistence change

This version adds an independent rank backup at:

`config/chillzone-pvprank-admin/saved-ranks.json`

On server start, the add-on restores the last saved ranking list into Combat-Ranked before normal play. During runtime it quietly keeps that backup synchronized, including rank changes made by normal Combat-Ranked gameplay. `/pvprank resetall confirm` intentionally saves an empty list so a deliberate reset stays reset after restart.

## Name-tag rank format (0.1.2-alpha)
- Ranked players use the compact label `[#1]`, `[#2]`, `[#3]`, etc.
- Players without a PvP rank continue to display `Unranked`.
- The add-on also normalizes these labels during its quiet once-per-second sync, so ranks changed by Combat-Ranked itself are converted to the compact format too.

## 0.1.3-alpha durability change
- The Top 10 backup is checked every server tick, but the JSON file is only rewritten when the ranking order actually changes.
- Normal Combat-Ranked kill swaps are therefore captured immediately and remain the source of gameplay rank changes.
- A normal server stop/restart performs one final best-effort rank capture.
- A temporary empty Combat-Ranked table during startup/shutdown cannot overwrite a non-empty saved Top 10.
- Rank saves use a temporary file + atomic replacement when supported, reducing the chance of a half-written save file.
- Saved positions are restricted to unique Top 10 slots (#1-#10).
- `/pvprank resetall confirm` still intentionally stores an empty list, so a deliberate owner reset remains empty after restart.

### 0.1.4-alpha nametag fix
Combat-Ranked 1.0.0 formats the visible scoreboard-team prefix separately from the stored `PlayerData.rank` text. This version therefore patches the actual live team prefix after Combat-Ranked updates it. Ranked players are forced to display `[#1]`, `[#2]`, etc. while the existing `Unranked` display is left unchanged. The prefix style/color already supplied by Combat-Ranked is preserved. The add-on re-checks online ranked players once per second so Combat-Ranked cannot quietly change the wording back to `[Rank #N]`.

## 0.1.5-alpha toggleable nametag style
The owner can now switch the ranked-player nametag wording without rebuilding the mod:

- `/pvprank nametag toggle` — flip between compact and full style.
- `/pvprank nametag compact` — force `[#2]`.
- `/pvprank nametag full` — use Combat-Ranked's native `[Rank #2]`.
- `/pvprank nametag status` (or just `/pvprank nametag`) — show the current style.

The selected style is saved at `config/chillzone-pvprank-admin/settings.json` and survives server restarts. `Unranked` is unchanged in both modes. The default on first run is compact mode.

## 0.1.6-alpha PvP on/off + OP-only controls
- Added `/pvprank on`, `/pvprank off`, `/pvprank toggle`, and `/pvprank status`.
- OFF pauses rankings while preserving the saved Top 10; ON restores it and resumes normal Combat-Ranked gameplay changes.
- The enabled/disabled state survives server restarts. Existing 0.1.5 settings files default to ON when upgraded.
- All `/pvprank` commands are now restricted to Minecraft OPs and the server console.
- LuckPerms is no longer a required dependency for this add-on.


## 0.1.7-alpha Minecraft 26.2 OP-check build fix
- Fixed the Minecraft 26.2 compile error in `Permissions.java`.
- Minecraft 26.2 `PlayerList.isOp(...)` expects the newer `NameAndId` identity object instead of a `GameProfile`.
- OP checks now use `player.nameAndId()`, keeping `/pvprank` restricted to server operators and the console as intended.
- No PvP ranking, persistence, nametag, or toggle behavior was removed by this fix.
