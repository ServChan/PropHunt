# PropHunt — User Guide

Plugin version: 1.0.2 · Paper and Purpur 26.3 · Java 25

[Русская версия](USER_GUIDE_RU.md)

## Contents

1. [What it is](#what-it-is)
2. [Installation](#installation)
3. [How to play](#how-to-play)
4. [Items](#items)
5. [Restrictions during a game](#restrictions-during-a-game)
6. [Setting up an arena step by step](#setting-up-an-arena-step-by-step)
7. [Commands](#commands)
8. [Permissions](#permissions)
9. [Settings](#settings)
10. [Files](#files)
11. [How it works](#how-it-works)
12. [FAQ](#faq)
13. [Building from source](#building-from-source)

## What it is

PropHunt is a hide-and-seek minigame for your server. Some players (props) turn into blocks from a set chosen for the map: barrels, bookshelves, pumpkins, hay. One to three hunters look for them and hit them with a sword. Props win if they last until time runs out, hunters win if they find everyone.

- Runs on Paper and Purpur 26.3 with no other plugins: no LibsDisguises, no ProtocolLib.
- The world never changes: disguises are a display entity plus "fake" blocks that only players in the game see.
- A player's items, position, game mode, health, experience and effects are saved when they join a game and returned when they leave, even after a server crash.
- Client mods do not help hunters: hits are worked out on the server.

## Installation

1. You need a [Paper](https://papermc.io/downloads/paper) or [Purpur](https://purpurmc.org/) server for Minecraft 26.3 and Java 25.
2. Put `PropHunt-1.0.2.jar` into `plugins/` (see [Building from source](#building-from-source)).
3. Start the server. `config.yml`, `messages_ru.yml` and `messages_en.yml` appear in `plugins/PropHunt/`.
4. Create an arena (see [Setting up an arena](#setting-up-an-arena-step-by-step)).

The message language is set by `language` in `config.yml`: `ru` or `en`.

## How to play

### Joining and waiting

- `/ph join` joins any open arena that has room and no round running. `/ph join <arena>` joins a specific one.
- `/ph list` lists the arenas.
- Your items and state are saved and you go to the arena lobby.
- Once `min-players` (2 by default) are in, the `lobby-countdown` (30 s) starts.

### Roles

- There are `ceil(players / players-per-hunter)` hunters, at least `min-hunters` and at most `max-hunters` (1 to 3 by default, one hunter per 4 players).
- At least one player always stays a prop.
- Players who were hunters recently are picked less often.

### Hiding phase

Lasts `hide-seconds` (30 s).

- Hunters wait at their spawn, blinded.
- Props appear at their spawns, get a random block from the map's set and shrink (`prop-scale` 0.55) so they fit through one-block gaps.
- The prop itself turns invisible; everyone sees a block following it instead.
- **Locking.** A prop that stands still for `lock-seconds` (2 s) "locks": everyone else sees a real block in that spot, snapped to the grid and turned the way the prop faces (stairs, furnaces, chests). Moving makes it a moving block again.

Tip for props: stand next to blocks of the same kind and face the same way they do.

### Seeking phase

Lasts `seek-seconds` (300 s).

- Hunters swing their sword. A hit counts against the prop's visible block within `hunter-reach` (4 blocks); not through walls.
- A hit takes `hunter-damage` from the prop (5 = 2.5 hearts), heals the hunter by `hunter-heal-on-hit` (2) and knocks the prop back.
- Hitting a real block costs the hunter `miss-damage` (1). A hunter who runs out of health goes back to the hunter spawn.
- There is a `hit-cooldown-ticks` (8 ticks) pause between hits.

### End of the round

- A found prop becomes a hunter (`found-become-hunters: true`) or a spectator if that setting is off.
- Hunters win when every prop is found. Props win when time runs out.
- After `end-seconds` (8 s) of results everyone returns to the arena lobby and waits for the next round.

### Leaving

- `/ph leave`, or the bed in the 9th hotbar slot.
- Leaving the server during a round also takes you out of the game. Your items, position and state come back right away.

## Items

| Item | Who | How to use it |
|---|---|---|
| Disguise | prop | right-click a map block from the set to become it, with its state (facing, type). Right-click the air for a list. During seeking at most once per `change-cooldown` (20 s) |
| Taunt | prop | plays a sound at the prop. The round gets `taunt-time-bonus` (5 s) shorter, but never shorter than `taunt-floor` (30 s). Cooldown `taunt-cooldown` (20 s). Does not shorten the round during hiding |
| Hunter's sword | hunter | hit suspicious blocks. Hitting a real block costs health |
| Whistle | hunter | right-click: every prop gives itself away with a sound. Once per `whistle-cooldown` (45 s) |
| Bed | everyone | leave the game |

Props also give themselves away with a sound every `auto-taunt` (60 s). `auto-taunt: 0` turns that off.

## Restrictions during a game

Players in a game cannot:

- break or place blocks, drop or pick up items;
- eat or regenerate health normally (only a hunter's hit heals);
- hit mobs, break item frames, paintings, armor stands or minecarts;
- take damage from the world (falling, suffocation and so on).

Only `/ph` and the commands in `allowed-commands` (`msg`, `tell`, `w`, `r` by default) work. The `prophunt.bypass.commands` permission lifts that limit.

## Setting up an arena step by step

Every command below needs `prophunt.admin` (operators by default). Arena names are 1 to 32 characters: lowercase Latin letters, digits, dashes and underscores.

1. Stand in the world of the future map and run `/ph create house`.
2. Stand where hunters wait during hiding: `/ph sethunterspawn house`.
3. Walk to every place props should appear and run `/ph addspawn house` at each. At least one is required; more is better.
4. Optional: a waiting lobby, `/ph setlobby house`. Without one, players wait at the hunter spawn.
5. Optional: map borders. Stand in one corner and run `/ph pos1 house`, then in the opposite corner `/ph pos2 house`. Players who leave the area are pulled back. Without an area there are no borders.
6. Collect disguise blocks (up to 54):
   - `/ph addblock house barrel` by name;
   - `/ph addblock house` while looking at a block, for that block;
   - `/ph addblock house` with a block in hand, for that block.
7. Check the set with `/ph blocks house` and remove extras with `/ph removeblock house <block>`.
8. If needed, override settings for this arena only: `/ph set house seek-seconds 240`.
9. Open the arena: `/ph enable house`. The command checks that everything required is set.
10. Test it: `/ph start house` starts a round in 5 seconds (at least 2 players).

A good map is a room or street with many identical blocks from the set. Only add blocks that actually occur on the map: a lone pumpkin in a stone hall gives the prop away at once.

## Commands

The main command is `/prophunt`, the short one is `/ph`.

### For players (`prophunt.play`, everyone has it)

| Command | What it does |
|---|---|
| `/ph join [arena]` | join a game |
| `/ph leave` | leave the game |
| `/ph list` | arenas and their state |
| `/ph help` | help |

### For admins (`prophunt.admin`)

| Command | What it does |
|---|---|
| `/ph create <arena>` | create an arena in the current world |
| `/ph delete <arena>` | delete an arena |
| `/ph sethunterspawn <arena>` | hunter spawn (required) |
| `/ph addspawn <arena>` | add a prop spawn |
| `/ph clearspawns <arena>` | remove all prop spawns |
| `/ph setlobby <arena>` | waiting lobby |
| `/ph pos1 <arena>`, `/ph pos2 <arena>` | corners of the map area |
| `/ph clearregion <arena>` | remove the area; no borders |
| `/ph addblock <arena> [block]` | add a block to the set |
| `/ph removeblock <arena> <block>` | remove a block from the set |
| `/ph blocks <arena>` | show the set |
| `/ph set <arena> <key> [value]` | arena override; without a value, back to the global value |
| `/ph enable <arena>` / `/ph disable <arena>` | open or close the arena |
| `/ph start <arena>` / `/ph stop <arena>` | start a round in 5 s / stop it |
| `/ph info <arena>` | arena details |
| `/ph reload` | reload the config, messages and arenas |

An arena cannot be edited while a round is running on it: wait for the end or run `/ph stop`.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `prophunt.play` | everyone | playing |
| `prophunt.admin` | operators | arena setup and round control |
| `prophunt.bypass.commands` | operators | any command during a game |

## Settings

`config.yml`:

- `language` is `ru` or `en`;
- `defaults` holds the global values; any of them can be overridden per arena with `/ph set`;
- `taunt-sounds` lists taunt sounds (Minecraft sound keys such as `entity.cat.ambient`);
- `allowed-commands` lists commands players may use in a game.

| Key | Default | Meaning |
|---|---|---|
| `min-players` | 2 | players needed to start |
| `max-players` | 16 | arena slots, late joiners included |
| `min-hunters` | 1 | fewest hunters |
| `max-hunters` | 3 | most hunters |
| `players-per-hunter` | 4 | one hunter per this many players |
| `lobby-countdown` | 30 | lobby countdown, s |
| `hide-seconds` | 30 | hiding phase, s |
| `seek-seconds` | 300 | seeking phase, s |
| `end-seconds` | 8 | results screen, s |
| `lock-seconds` | 2.0 | time standing still before locking, s |
| `prop-scale` | 0.55 | prop size (`scale` attribute) |
| `hunter-damage` | 5.0 | damage to a prop (2 = one heart) |
| `miss-damage` | 1.0 | damage to the hunter for hitting a real block |
| `hunter-heal-on-hit` | 2.0 | hunter healing per hit |
| `hunter-reach` | 4.0 | hit distance, blocks |
| `hit-cooldown-ticks` | 8 | pause between hits, ticks |
| `found-become-hunters` | true | a found prop becomes a hunter |
| `taunt-cooldown` | 20 | taunt cooldown, s |
| `taunt-time-bonus` | 5 | how much a taunt shortens the round, s |
| `taunt-floor` | 30 | the round never gets shorter than this, s |
| `auto-taunt` | 60 | automatic prop sound, s; 0 turns it off |
| `whistle-cooldown` | 45 | whistle cooldown, s |
| `change-cooldown` | 20 | block change during seeking, s |

## Files

| Path | Contents |
|---|---|
| `plugins/PropHunt/config.yml` | global settings |
| `plugins/PropHunt/messages_ru.yml`, `messages_en.yml` | all texts (MiniMessage); edit them freely |
| `plugins/PropHunt/arenas/<arena>.yml` | an arena: world, spawns, area, block set, overrides |
| `plugins/PropHunt/snapshots/` | player state snapshots while they are in a game |

A player's snapshot is written when they join a game and removed after they leave. If the server crashes mid-round, the player gets everything back the next time they join.

## How it works

- A disguise is a `BlockDisplay` entity moved to the prop every tick. The player is hidden with `hidePlayer`.
- A locked prop is a fake block (`sendBlockChange`) for every player in the game, with the display hidden from them. Fake blocks are re-sent once a second and replaced by the real ones when the prop moves and when the round ends.
- Hits are worked out on the server: on each arm swing a ray from the hunter's eyes is tested against prop blocks and real blocks.
- Players in a game are allowed to "fly" (`allowFlight`; actual flight is cancelled), so the server does not kick them for standing on a fake block.

## FAQ

**`/ph enable` says the arena is not ready.** It lacks a hunter spawn, at least one prop spawn or at least one block in the set. See `/ph info <arena>`.

**The round does not start.** Fewer than `min-players` are on the arena, or the arena is closed. `/ph start` also needs at least 2 players.

**A prop gives itself away by the block's facing.** Disguising by right-clicking a map block copies its state (facing, type), so copy a block next to you instead of picking from the list.

**A player was kicked for flying.** Make sure your anti-cheat does not check flight for players in a game. PropHunt turns `allowFlight` on for them on purpose.

**A player has no items after a server crash.** They come back on the player's next join: the snapshot is in `plugins/PropHunt/snapshots/`. Do not delete that folder by hand.

**Does it run on Folia?** No, the plugin targets Paper and Purpur.

## Building from source

JDK 25 is required.

```powershell
git clone https://github.com/ServChan/PropHunt.git
cd PropHunt
.\gradlew.bat build
```

On Linux and macOS use `./gradlew`. The jar is `build/libs/PropHunt-1.0.2.jar`. `.\gradlew.bat runServer` starts a Paper 26.3 test server.
