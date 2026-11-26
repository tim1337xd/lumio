<p align="center"><img src="docs/readme/banner.webp" alt="Lumio: the Molten sky at night, Cut-out lyrics in the sky, the menu in Glass and Colored, Lumio's HUD, and the cinematic camera" width="100%"></p>

# Lumio

A calm, good-looking Minecraft: sky, music with lyrics, menus and HUD in one client-side mod.

![Minecraft 26.2 · 26.3](https://img.shields.io/badge/Minecraft-26.2_·_26.3-6d28d9?style=flat-square)
![Loader Fabric](https://img.shields.io/badge/Loader-Fabric-6d28d9?style=flat-square)
![Version 1.0.0 Beta 2](https://img.shields.io/badge/Version-1.0.0_Beta_2-6d28d9?style=flat-square)
![License GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-6d28d9?style=flat-square)

**[Download from GitHub Releases](https://github.com/tim1337xd/Lumio/releases)** · [SkyBlock add-on](https://github.com/tim1337xd/Lumio-SkyBlock) · [Changelog](CHANGELOG.md)

<p align="center"><picture><source media="(prefers-color-scheme: light)" srcset="docs/readme/divider-light.png"><img src="docs/readme/divider.png" alt="divider" width="100%"></picture></p>

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://github.com/FabricMC/fabric-api/releases) for Minecraft 26.2 or 26.3 (Java 25).
   On Minecraft 26.3, also add `-XX:StackShadowPages=32` to the Java arguments in your launcher: Minecraft 26.3 itself can crash while starting on some PCs without it, also without Lumio.
2. Put the Lumio jar for your Minecraft version into your `mods` folder.
3. Start the game: a short setup opens. Later, open Lumio with `/lumio`.

Coming from Beta 1? Replace the old jar by hand once; from Beta 2 on, Lumio tells you about new versions itself.

<p align="center"><picture><source media="(prefers-color-scheme: light)" srcset="docs/readme/divider-light.png"><img src="docs/readme/divider.png" alt="divider" width="100%"></picture></p>

## Features

<details>
<summary><b>Sky</b> · styles, clouds, aurora, stars</summary>

- Sky styles, among them Molten: thin glowing lines that flow slowly across a dark sky.
- 3D clouds in three layers, lit by the sun and turning stormy with the weather.
- Aurora, calm stars and shooting stars at night.
- Auto quality picks what your graphics card handles, so weak laptops stay smooth.

<img src="docs/readme/sky.webp" alt="Aurora and stars over a plain at night" width="100%">
</details>

<details>
<summary><b>Music and lyrics</b> · now playing, lyrics in the world</summary>

- The song with its cover in the info bar, a small player or a window (Cider, Spotify, Windows media).
- Live lyrics word by word, in the info bar and in the world.
- In the world as a glowing line or as cut-out words, placed in open sky where you look.
- The sung word glows, or the whole text; ad-libs stand beside the line, never over it.

<img src="docs/readme/lyrics.webp" alt="Cut-out lyrics standing in the sky, the sung word on a tile" width="100%">
</details>

<details>
<summary><b>Menu and themes</b> · Glass, Dark, Light, Colored</summary>

- One settings window with search, a top bar and a way back.
- Themes Glass, Dark, Light and Colored, which can follow the song's colors.
- The game's own screens in the same look: buttons, sliders and lists.

<img src="docs/readme/menu.webp" alt="The settings window in the Glass and the Colored theme" width="100%">
</details>

<details>
<summary><b>HUD</b> · editor, status bars, info bar</summary>

- Hotbar, status bars, boss bars, armor, scoreboard and player list in one style.
- An info bar with time, frames, ping, place and the song.
- A HUD editor: move, resize and snap everything by drag and drop.
- Elements step back while a menu is open.

<img src="docs/readme/hud.webp" alt="Lumio's HUD: the info bar on top, the status bars over the hotbar, armor, inventory and a scoreboard" width="100%">
</details>

<details>
<summary><b>Cinematic camera</b> · a music video of your world</summary>

- Calm camera shots of your world while you are away from the keys.
- The lyrics of the song placed in each shot.
- Letterbox, slow moves and soft cuts.

<img src="docs/readme/film.webp" alt="The cinematic camera: the player under the aurora, with film bars" width="100%">
</details>

<details>
<summary><b>World effects</b> · trails, glow, held item, camera</summary>

- Trails behind arrows and ender pearls, and a glow around the held item.
- Size, place and swing of the item in your hand.
- An outline or glow on the block you look at, fireflies and wet ground in the rain.
- Full bright and less fog, fire and clutter on screen.

<img src="docs/readme/effects.webp" alt="A trail behind an arrow and a glow around the held item" width="100%">
</details>

<details>
<summary><b>Chat, waypoints and keys</b> · tabs, routes, one key per command</summary>

- A movable chat with tabs, Copy and Reply; the game's messages stay as they are.
- Waypoints in groups and routes, with beams, labels and a map; imports from Skyblocker, SkyHanni, ColeWeight and Skytils.
- Keys for chat commands and key combinations of up to three keys.
- Configs to share, and presets to start from.

<img src="docs/readme/chat.webp" alt="The chat panel with its tabs" width="100%">
</details>

<p align="center"><picture><source media="(prefers-color-scheme: light)" srcset="docs/readme/divider-light.png"><img src="docs/readme/divider.png" alt="divider" width="100%"></picture></p>

## Fair play

Lumio only changes how the game looks and shows what you can already see. It never plays for you: no automatic clicks, movement, item
use or commands. It sends nothing on its own; a command key or a chat tab sends only the one command you would type, and the info bar
asks for your ping with the game's own request. Nothing hidden is shown, such as players or blocks behind walls.

<p align="center"><picture><source media="(prefers-color-scheme: light)" srcset="docs/readme/divider-light.png"><img src="docs/readme/divider.png" alt="divider" width="100%"></picture></p>

## Questions

<details>
<summary><b>Does it work with Sodium and Iris?</b></summary>

Sodium: yes, Lumio is played with Sodium every day, and its video settings open from Lumio's options. Iris has not been tested yet; tell us
how it goes.
</details>

<details>
<summary><b>Is it allowed on Hypixel?</b></summary>

Lumio keeps to Hypixel's rules on allowed modifications: client-side, visual, no automation. Hypixel
does not approve any mod, so you use every modification at your own risk.
</details>

<details>
<summary><b>Minecraft 26.3 crashes while starting</b></summary>

That is the game, not Lumio (`EXCEPTION_ACCESS_VIOLATION`). The Java option `-XX:StackShadowPages=32` avoids it; some launchers, such as
Prism, already add it.
</details>

**Updates:** Lumio asks GitHub once per start whether a newer version exists; nothing is downloaded without a click.

<p align="center"><picture><source media="(prefers-color-scheme: light)" srcset="docs/readme/divider-light.png"><img src="docs/readme/divider.png" alt="divider" width="100%"></picture></p>

[Credits](CREDITS.md) · [GPL-3.0](LICENSE) · [Report a bug](https://github.com/tim1337xd/Lumio/issues) · Not affiliated with Mojang, Microsoft or Hypixel.
