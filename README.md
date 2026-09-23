# Chill Zone PvP Rank Admin

Operator-only administrative add-on for the exact Combat-Ranked Fabric 26.2 mod inspected from the server.

## Requires
- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API
- Combat-Ranked (`mod id: combat`)

## Permission
All `/pvprank` commands are restricted to Minecraft server operators (OPs) and the server console. Non-OP players cannot use or modify these admin controls. LuckPerms is not required by this add-on.

## Commands
- `/pvprank set <player> <1-10>` — move an online player to that rank; other ranks shift to keep positions unique.
- `/pvprank remove <player>` — remove an online player from rankings and keep them unranked until an owner uses `set` or `reset`.
- `/pvprank swap <player1> <player2>` — swap two online players' current rank positions.
- `/pvprank reset <player>` — re-enable the player and move them to the bottom of the current rankings.
- `/pvprank list` — list stored rankings.
- `/pvprank resetall confirm` — clear every rank. Combat-Ranked will assign ranks again through its normal behavior.

All player arguments suggest ONLY players currently online. This naturally includes Java and Bedrock players that appear in the server's normal online player list.

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
An operator can switch the ranked-player nametag wording without rebuilding the mod:

- `/pvprank nametag toggle` — flip between compact and full style.
- `/pvprank nametag compact` — force `[#2]`.
- `/pvprank nametag full` — use Combat-Ranked's native `[Rank #2]`.
- `/pvprank nametag status` (or just `/pvprank nametag`) — show the current style.

The selected style is saved at `config/chillzone-pvprank-admin/settings.json` and survives server restarts. `Unranked` is unchanged in both modes. The default on first run is compact mode.

## 0.1.9-alpha clean OP-only build
This build intentionally returns to the 0.1.5 feature set and adds only the corrected Minecraft 26.2 operator permission gate. It does **not** include the add-on's experimental PvP ranking ON/OFF commands; use Combat-Ranked's own built-in enable/disable system instead. It also does not include the later chat/message filtering experiment. Restart persistence, normal Combat-Ranked kill-based rank changes, online-player autocomplete, `Unranked`, and the global compact/full nametag controls remain included.
