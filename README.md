# PropHunt

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.3-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur-blue?style=flat-square)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Plugin Version](https://img.shields.io/badge/Version-1.0.2-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

Prop hunt minigame for Paper and Purpur 26.3: props hide as blocks from a set chosen for each map, and 1–3 hunters search for them with a sword.

**Руководство / User guide:** [Русский](docs/USER_GUIDE_RU.md) · [English](docs/USER_GUIDE_EN.md)

## Русский

### Что это

Мини-игра «Прятки» (prop hunt) для сервера. Пропы превращаются в блоки из набора карты, охотники ищут их и бьют мечом. Пропы побеждают, если продержались до конца времени, охотники — если нашли всех.

### Возможности

- Свой набор блоков и свои настройки для каждой карты.
- Замирание: проп, простоявший 2 секунды, становится настоящим на вид блоком, выровненным по сетке и повёрнутым по взгляду.
- Предметы: маскировка, дразнилка, свисток охотника, выход по кровати.
- Попадания считает сервер, клиентские моды охотнику не помогают.
- Мир не меняется: маскировка — сущность-дисплей и фейковые блоки только для участников.
- Вещи, позиция и состояние игрока сохраняются на диск и возвращаются после игры, даже после падения сервера.
- Тексты на русском и английском, все правятся в файлах сообщений.

### Быстрый старт

1. Положите `PropHunt-1.0.2.jar` в `plugins/` сервера Paper или Purpur 26.3 и запустите его.
2. Создайте арену: `/ph create dom`, `/ph sethunterspawn dom`, несколько раз `/ph addspawn dom`, несколько раз `/ph addblock dom <блок>`.
3. Откройте её: `/ph enable dom`.
4. Игроки входят командой `/ph join`.

Все команды, права и настройки — в [руководстве](docs/USER_GUIDE_RU.md).

### Требования

Paper или Purpur 26.3, Java 25. Других плагинов не нужно. Folia не поддерживается.

### Сборка

```powershell
.\gradlew.bat build
```

Готовый файл: `build/libs/PropHunt-1.0.2.jar`. Тестовый сервер: `.\gradlew.bat runServer`.

---

## English

### What It Is

A hide-and-seek (prop hunt) minigame for your server. Props turn into blocks from the map's set, and hunters look for them and hit them with a sword. Props win if they last until time runs out, hunters win if they find everyone.

### Features

- Its own block set and settings for every map.
- Locking: a prop that stands still for 2 seconds becomes a real-looking block, snapped to the grid and facing the way the prop looks.
- Items: disguise, taunt, hunter's whistle, leave bed.
- Hits are worked out on the server, so client mods do not help hunters.
- The world never changes: disguises are a display entity and fake blocks shown only to players in the game.
- Items, position and state are saved to disk and returned after the game, even after a server crash.
- Russian and English texts, all editable in the message files.

### Quick Start

1. Put `PropHunt-1.0.2.jar` into `plugins/` on a Paper or Purpur 26.3 server and start it.
2. Create an arena: `/ph create house`, `/ph sethunterspawn house`, `/ph addspawn house` a few times, `/ph addblock house <block>` a few times.
3. Open it: `/ph enable house`.
4. Players join with `/ph join`.

Every command, permission and setting is in the [user guide](docs/USER_GUIDE_EN.md).

### Requirements

Paper or Purpur 26.3, Java 25. No other plugins needed. Folia is not supported.

### Building

```powershell
.\gradlew.bat build
```

Output: `build/libs/PropHunt-1.0.2.jar`. Test server: `.\gradlew.bat runServer`.

## Лицензия / License

[MIT](LICENSE).
