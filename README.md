# Chill Zone PvP Rank Admin

Owner-only administrative add-on for the exact Combat-Ranked Fabric 26.2 mod inspected from the server.

## Requires
- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API
- LuckPerms
- Combat-Ranked (`mod id: combat`)

## Permission
Grant only to the Owner group:

`/lp group owner permission set chillzonepvprank.admin true`

Optional explicit denies:

`/lp group admin permission set chillzonepvprank.admin false`
`/lp group mod permission set chillzonepvprank.admin false`
`/lp group creator permission set chillzonepvprank.admin false`
`/lp group member permission set chillzonepvprank.admin false`

Server console is always allowed.

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
