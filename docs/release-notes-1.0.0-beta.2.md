<img src="https://github.com/tim1337xd/Lumio/raw/v1.0.0-beta.2/docs/readme/release.png" alt="Lumio 1.0.0 Beta 2 · Minecraft 26.2 · 26.3 · Fabric · Java 25" width="100%">

Second beta: waypoints, a new way to show lyrics in the world, the Molten sky and verified updates.

> [!IMPORTANT]
> Coming from Beta 1? Install this one by hand once: replace the old jar. From Beta 2 on, Lumio tells you about new versions itself.

## Download

| Minecraft | File | Needs |
|---|---|---|
| 26.3 | [lumio-1.0.0-beta.2-26.3.jar](https://github.com/tim1337xd/Lumio/releases/download/v1.0.0-beta.2/lumio-1.0.0-beta.2-26.3.jar) | Fabric API 0.165.0+26.3 |
| 26.2 | [lumio-1.0.0-beta.2-26.2.jar](https://github.com/tim1337xd/Lumio/releases/download/v1.0.0-beta.2/lumio-1.0.0-beta.2-26.2.jar) | Fabric API 0.161.0+26.2 |

Fabric Loader 0.20.1 or newer and Java 25. Playing SkyBlock? Get the [Lumio: Hypixel SkyBlock](https://github.com/tim1337xd/Lumio-SkyBlock/releases/tag/v1.0.0-beta.2) add-on too.

## Highlights

- **Waypoints**: mark places, plan routes and share lists.
- **Cut-out lyrics in the world**: word by word, like paper cut-outs.
- **Molten**, a new sky style with glowing lines by day and night.
- Updates are checked at every start and verified with a signature; nothing downloads without your click.

## All changes

<details>
<summary><b>Added</b> 6</summary>

- Waypoints: mark places, plan routes and share lists.
- Cut-out lyrics in the world: word by word, like paper cut-outs.
- Molten, a new sky style with glowing lines by day and night.
- Camera and Visual Filter: full bright, less clutter and fog.
- Updates are checked at every start and verified with a signature; nothing downloads without your click.
- What is new: a window of what changed; a dot on Home shows when something is new.

</details>

<details>
<summary><b>Changed</b> 8</summary>

- Lyrics in the world stay in place, turn to face you and fit their size to the room.
- Cut-out: a box only on a line's key word, and a glow you can turn off.
- Sky: layered clouds, a new aurora and calmer stars.
- Menu: a top bar, back buttons and history; settings show only what applies.
- Chat: a wide chat keeps its place over the hotbar instead of moving up; moving up is still a choice.
- The game's own screens get Lumio's boxes, sliders and switches.
- A fresh install starts with a new look and default settings; yours stay as they are.
- Better performance for the menu and its glass on weaker graphics cards.

</details>

<details>
<summary><b>Fixed</b> 7</summary>

- Lyrics in the world no longer disappear when you turn, walk or the cinematic camera cuts, and sung lines leave instead of piling up.
- Lyrics in the world no longer skip words when a line repeats.
- Songs with only line timings move word by word again.
- Bows are held like bows; Rejoin and quick play start clean.
- Paged menus (Wardrobe, storage, Auction House) no longer flicker when you turn a page.
- The sky no longer pops or vanishes under an overhang or on the Crimson Isle; server icons fit the text.
- Smaller fixes for the HUD theme, chat, clouds, lyrics, menu, scoreboard and loading screen.

</details>

## Good to know

<details>
<summary><b>Minecraft 26.3 crashes while starting</b> · a Java option fixes it</summary>

Minecraft 26.3 can crash while starting on some PCs, also without Lumio: add `-XX:StackShadowPages=32` to the Java arguments in your launcher.

</details>

<details>
<summary><b>Check your download</b> · SHA-256 and signature</summary>

| File | SHA-256 |
|---|---|
| `lumio-1.0.0-beta.2-26.3.jar` | `58719fc9bfc63202529849ca7bf787c35bd0e2cea9c3093c3484119a3c85f141` |
| `lumio-1.0.0-beta.2-26.2.jar` | `b6eb2180b6d1c1672f1969cb1d0ccb4415a412fa6d7c0e2b5f875f4fb2c7f12b` |

Each jar has its `.sig` beside it: the jar's Ed25519 signature, which Lumio's updater checks against the key built into Lumio before it installs an update.

</details>

**Full changelog:** [v1.0.0-beta.1...v1.0.0-beta.2](https://github.com/tim1337xd/Lumio/compare/v1.0.0-beta.1%2Bmc26.3...v1.0.0-beta.2)
