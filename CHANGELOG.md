# Changelog

The in-game changelog (Settings → About) is generated from `src/main/resources/assets/lumio/changelog.txt`.

## 1.0.0-beta.2 (2026-11-25)

The second beta. Coming from Beta 1? Install this one by hand once: replace the old jar. From Beta 2 on, Lumio tells you about new versions itself.

### New

- **Waypoints**: a page of their own under Extras. Points in groups, each with the areas it shows in and a look of its own; routes that go on as you reach each point and can start over in a new lobby; a corner map in the HUD and a big map while a key is held. Coordinates players share in chat get a "+ Waypoint" button. Lists from Skyblocker, SkyHanni, ColeWeight and Skytils can be imported, and Lumio's, Skyblocker's or SkyHanni's written out. Nothing is ever sent.
- **Cut-out lyrics in the world**: a second look beside Classic. The words come one by one as they are sung, in a column like paper cut-outs, with a box on a line's key word.
- **Molten**: a new sky style of glowing lines that drift across a dark sky by day and night, in a color you choose.
- **Camera and Visual Filter**: two new World modules. Camera lifts dark places without night vision and has a perspective key that skips the front view; Visual Filter leaves out what you don't want to see, such as falling blocks, lightning, death animations, block breaking particles, the fog and the fire, portal and underwater overlays.
- **Updates**: Lumio looks for a new version of itself and of its add-on once per game start, sending nothing personal. Nothing is downloaded until you press Update; every jar must carry Lumio's release signature, and it is swapped in when you close the game, with the old one kept so you can go back.
- **What is new**: a window with what each version added, changed and fixed, and a link to each feature. A dot on Home shows when there is something new.

### Changed

- **Lyrics in the world**: placed by what you see, in open sky where there is some, at a size that fits the room there. A line stays where it was placed and turns to face you, the words are sharp at every size, and the colors follow what lies behind them.
- **Sky**: a page with a live preview; layered clouds that slowly change their shape, a new aurora, calmer stars and shooting stars, and a quality that picks itself for your graphics card.
- **Menu**: a top bar with the search, back buttons, and a history you go through with the mouse's side buttons. Settings have short names with help on hover, sit in named groups, and show only what applies. Menu settings are in tabs.
- **The game's own screens**: buttons, sliders, switches and text fields in Lumio's look.
- **HUD**: a layout keeps its look on another screen or GUI scale; the editor snaps to the center and a grid; panels dim, blur or hide behind an open menu, as you choose; the info bar no longer jumps when a number grows, and the tab list keeps its size and its columns.
- **Keys**: no key is set at first. Any key can be a combination of up to three keys and mouse buttons, and every feature's keys are also in its own settings.
- **Chat**: Lumio's own lines start with "[Lumio]", and a server's centered lines are centered in the chat. A chat wide enough to reach the hotbar keeps its place and width and is drawn over the hotbar, its bars and what lies beside it (only the lines shown cover it); "At the hotbar: Move up" keeps the old way.
- **A fresh install** starts with a new look and default settings; settings you already have stay as they are.
- **Better performance** for the menu's glass and the sky on weaker graphics cards.

### Fixed

- Lyrics in the world no longer disappear when you turn or walk or when the cinematic camera cuts to a new shot, sung lines leave instead of piling up, and no words are skipped when a line repeats or the song is fast.
- Songs with only line timings move word by word again, in the world, the HUD and the info bar.
- Bows from a server's resource pack are held like bows in third person.
- Rejoin and quick play start clean; the game's start screen no longer stays over the world.
- The sky no longer pops or vanishes under an overhang or on the Crimson Isle, and clouds show through doors and windows.
- Server icons, such as the location pin, are drawn at the size of the text.
- Paged menus of a server (the Wardrobe, the storage, the Auction House) no longer flicker when you turn a page.
- Smaller fixes for the HUD theme, chat, clouds, lyrics, the menu, the scoreboard and the loading screen.

## 1.0.0-beta.1 (2026-11-07)

The first public version. This is a beta: feedback is welcome, and things may still change.

- **World**: clouds, aurora and shooting stars in 3D that never jump, fireflies at night and rain with wet ground, trails behind arrows and ender pearls, light around the held item, its size and swing, walk effects, and a block overlay.
- **Interface**: four themes (Glass, Dark, Light and Colored, whose colors can follow the song), a hotbar with status bars, an info bar, and panels for boss bars, armor, inventory, scoreboard and the player list, all arranged in a visual editor.
- **Settings**: short groups in the settings pane and the rest under Advanced; a HUD theme all panels follow, and one Look row on every panel for those you want different.
- **Chat**: its own page in the sidebar; a calm panel you can move, as wide as its lines or as you set it, with smooth scrolling and a scroll bar you can drag, with the time, player heads, channel stripes, highlights, Copy and Reply, tabs you can order and your own filtered tabs. Messages stay as the game sends them.
- **Item tooltips**: a frame and size of their own, scrolling for long ones, and whatever add-ons add in one separate block. The game's text stays exactly as it is.
- **Menus**: a main menu with a world that follows the seasons and the time of day, a settings menu on movable windows, and one loading screen for joining, loading and saving.
- **Music**: what is playing from Cider, Spotify or the Windows media controls, with players, and live lyrics in the info bar, the players and the world.
- **Cinematic camera**: a calm music video of your surroundings while you are away.
- **Everyday things**: keybinds for commands, shared configs and two presets to start from, notifications, borderless fullscreen, and server resource packs that load only once.
- **Add-ons**: an API for add-ons, such as [Lumio: Hypixel SkyBlock](https://github.com/tim1337xd/Lumio-SkyBlock).
