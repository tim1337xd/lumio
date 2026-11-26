# Lumio API changelog

The changes of the add-on API (mod id `lumio-api`), by API version. The **stable** part follows the version rules in API.md: adding
raises the minor version, changing or removing raises the major version. The **experimental** part (`api.experimental`) may change in any
version, without a new version; its changes are listed here too, under "Experimental", so add-ons that use it can follow them.

## 1.31.0

`Changelog`: the format of a mod's `assets/<mod id>/changelog.txt` (versions with dates, Added, Changed and Fixed entries with an optional link to a feature), with `parse` and `path`. Lumio shows the changelog of every add-on that ships one in its What is new window, one tab per mod; an add-on needs no code for it.

Experimental: `AddonWaypoints.show(key, name, x, y, z)` / `remove(key)`: a temporary waypoint the add-on keeps up itself (moved by showing it again
under the same key), drawn and listed as the chat's temporary waypoints, without their time limit, count or "remove when reached"; gone on a
world change. `AddonWaypoints.show(key, name, x, y, z, rgb, label)`: the same in a color of the add-on's own, with or without its label.

## 1.30.0

Island skies are removed from the main mod: `LocationProvider.has` is no longer asked for the "sky:..." properties (1.9.0 to 1.29.0). No signature changed; an add-on that still answers them is simply not asked.

Experimental: `Threads.daemonExecutor(name)`: the one daemon single-thread executor the mods share.

Experimental: `PanelKit.segmented(ui, x, y, width, labels, ...)`: a segmented control of a given width, its parts even and its labels centered.

Experimental: `DebugHooks.setUpdateTestKey(publicKey)` / `updateTestKey()`: the key a local update test server signs with; taken only by a jar that is no public release, while a test server is set.

Experimental: `Draw.argb(rgb, alpha)`: a color from its RGB and an alpha from 0 to 1.

## 1.29.0

Every color offers "Menu theme" now. `ColorChoice.ownColorOnly()`: takes that away from a color that carries a meaning.

Experimental: removed what no add-on used: `PanelKit.ORANGE_TEXT`, `PanelKit.TILE_PURPLE`, `PanelKit.TILE_GRAY`, `SystemColors.SUCCESS`, `SystemColors.DANGER_TEXT` and `SystemColors.WARNING` (use `GREEN`, `RED_TEXT` and `ORANGE`).

## 1.28.0

`AddonSetting.setTooltip(text)`: a longer explanation shown while the pointer rests on the setting's name, which then has a small mark. For what its name and one-line description cannot say. Page outlines (`assets/<id>/page_outlines.txt`) take it as an optional last column.

Experimental: `TooltipRoom.keepClear(x, y, width, height)`: something that must stay usable under an item's tooltip (a search bar) names its box each frame, and the tooltip moves away from it.

Experimental: `WaypointStyle.TextScaling.SIGN` is called "Like a sign".

Experimental: `HudEditor.drawOnTop(drawing)`: a panel draws something (its tooltip) over the HUD editor's selection outline and handles, which are drawn after the panels.

Experimental: `ServerParticles.hide(Hider)`: an add-on can hide some of the server's particles from view (the player's choice, such as a hotspot's particles under its drawn circle); `received` returns whether a packet is hidden.

Experimental: `ScreenInput.BACK` and `ScreenInput.FORWARD`: the mouse's back and forward buttons (the fourth and fifth), as `ScreenInput.button` gives them on every Minecraft version.

## 1.27.0

`Slider.unit(suffix)`, `Slider.offAtZero()` and `Slider.offAtZero(shown)`: a slider shows its unit in the value (" s", " blocks") instead of the label, and its lowest value, 0, as "Off", "Auto" or "Any". Only the display; the stored value stays the number.


## 1.26.0

`AddonPanel.usedInMenus()`: a panel says that it is meant to be used while an inventory or another menu with slots is open (it takes clicks there). It then stays fully visible behind that menu, whatever the player set for "While a menu is open" (HUD), unless the player hides that panel in menus.
Experimental: `WaypointStyle.SMALLER_FAR` is labelled "Smaller far away" (a shorter label, so it fits with the Minecraft font).
Experimental: `ExtraStatusBar.standsFor(panel, asBar)`, `panel()`, `asBar()`: the extra bar and an add-on panel are one thing in two styles (the arrows); the HUD editor lists them as one entry, which selects the bar while it is the style used.
Experimental: `HudCover.addAreas(source)`: an add-on names the places its own menu overlays take on the screen (buttons around the inventory, a panel beside a chest); a HUD element behind a menu fades or hides where it lies under one of them.

## 1.25.0

`ScreenHooks.ownsWorldKey(key)`: an add-on says that it uses a key itself in the world right now, with no screen open (a key that stands for an action of the game while one of its features is active); Lumio's command keybinds leave that key alone meanwhile. The counterpart of `ownsKey(screen, key)` for menus.

## 1.24.0

`AddonFeature.searchWords(words)`: words the menu's search also finds a feature by, besides its name (what it was called before a rename, such as "toast"). Never shown.

Wording: the small box that slides in at a screen edge is a "pop-up" in every text a player reads, no longer a "toast"; class names (`Toasts`) and saved values stay.

## 1.23.0

Page groups: `AddonFeature.pageGroup(name)` names the group a feature is listed under on its page. A page (a category made a page of its own) with eight or more features shows them as named groups one under the other on the same scrolling page: the group's name in small capitals with a thin line to its right, its rows in one box, then space. A group stands where its first feature stands in the page's list; a feature without a group goes under "Other". Shorter pages and pages shown as tabs stay as they are.

## 1.22.0

Work in progress: `AddonFeature.workInProgress()` flags a feature, `AddonPage.workInProgress(category)` a whole category of a page. A flagged feature gets a small "Work in progress" tag after its name in the feature lists ("WIP" where the row is narrow, and in the search results). A flagged category also gets the tag on its tab, and at its top a note ("These features are still being tested on Hypixel. Some may be wrong or unfinished. Please report what doesn't work.") with a "Report a problem" button that opens Lumio's issues page after the player confirms. Only tags and the note: nothing about the features changes, and taking the call out takes them away.

## 1.21.0

Experimental: `ServerPing` (`api.experimental.util`) is the ping Lumio measures with the game's own ping request, the value its info bar shows: `millis(now, mostAgeMs)` gives it or `NONE`; `ask()` asks for one measurement while the info bar does not measure (one request, never sooner than four seconds after the last). For the SkyBlock add-on's party command "!ping".

## 1.20.0

Experimental: `WaypointStyle` (`api.experimental.waypoint`) is the look the player set for all waypoints (Waypoints › Look): `WaypointStyle.general()`, `pick(useGeneral, general, own)` for a kind of waypoint with a look of its own, and what it means for drawing (`boxed`, `fillOpacity`, `beamOn`, `inReach`, `labelScale`, `nearOpacity`, `iconLabel`). `WaypointLook` has a beam of any height, a plain label without its shadow, and `iconLabel` (the point's icon on a small box, then the text). `SettingRows.Question` has no `heading` any more: a question starts with its title.

`TabCard` has a `slot`: the card's fixed place among the tab list's info cards by its kind (from 0 on; -1 for none, as the two-argument constructor gives). The list gives every card its column by its slot (slot modulo the columns) and orders a column by it, so cards never jump between columns when others appear, disappear, grow or shrink; cards without a slot come after, in the order first seen, kept for the stay in a world.

The tab list reads only entries the server gave a display name as lines of the info columns: an NPC listed for a moment under its random name is no line any more.

## 1.19.0

`AddonFeature.addMenuKey(mapping, menu)`: a key that acts only while one menu is open (a key per slot of that menu), with the menu's name ("Loadouts"). In its menu it is used instead of any other key on the same key, so the Keybinds page shows no conflict for it (only with another key of the same menu), and a quiet note while its field has the focus. `KeyPlace.TYPING`: a key that acts only while a text field has the keyboard.

Key fields show a hint only for a real conflict, two keys on the same key that both act in the same situation: "Also triggers: <name>", each name once, at most two, then "and N more".

Experimental: `hud.GridSnap` and `HudEditor.gridSnap()`: snapping with the HUD editor's grid and logic (the grid's lines and the screen's center lines, no edges, no other elements) for panels an add-on lets the player move or pull on a screen; `HudEditor.drawGrid(canvas, width, height)` draws the editor's grid as it shows while dragging.

## 1.18.0

`ScreenHooks.ownsKey(screen, key)` (default false): an add-on that uses a key or mouse button itself in an open menu (such as a key per slot) says so, and Lumio's command keybinds leave that key alone there.

## Experimental changes after 1.17.0 (no new API version)

Icons: `pencil` joins Lumio's icon set (for `Canvas.icon`), for edit buttons.

Experimental: `util.ChatCoords.inChat(message)`, the block coordinates a player shared in chat ("x: 12, y: 70, z: -30" or "12 70 -30", with the place name before them), the same reader as Lumio's "+ Waypoint" buttons; `nameBefore(text)` for the name alone.

Experimental: `ui.SettingRows`: a row with several buttons (highlighted, with a choice in it, buttons that may change), colors for chip switches, and a question a settings sheet asks before something changes (what was found, a choice, Cancel and a confirming button). `waypoint.WaypointCards.placeKey()`: the waypoints' place key, for an add-on's own page.

Experimental: `util.KeyNames.of(mapping)`, a key mapping's keys as shown, also a key combination ("Tab + R"). Add-on key mappings take combinations without any change: players set them on the Keybinds page, and the mapping acts only when its whole combination is pressed.

Experimental: `hud.HudAnchors`, the HUD's anchor math (`start`, `offset`, `nearest` for the start, middle and end of the screen), for an add-on's panels kept relative to the screen like the HUD's.

Experimental: `api.experimental.debug`, the debug hooks: `DebugHooks` (probes `log`, `emit`, `event`, `passBegin`/`passEnd`/`frameBegin`,
`frameMark`, read-only `view`s, the guard `ACTIVE` and `wants(category)`; sinks, the log file's owners, the updater's local test server),
`DebugSink`, `DebugLevel`, `DebugCategory`, `DebugPass`, `FrameMark`.

Experimental: `Debug` (`util`) is removed: its `on()` is `DebugHooks.ACTIVE`, its `log(...)` is `DebugHooks.log(category, ...)` behind that guard.
Its lines no longer go to the game's log but to the Debug log's file and to a debug tool. The internal `Services.debugOn()` is gone with it.

## 1.17.0 (with Lumio 1.0.0 Beta 2)

`AddonFeature.badge`: a short badge after a feature's name in the feature lists, such as the floor or phase it is for ("F4", "P3", "F7 · M7").

Experimental: `Radius.box(height)`, the small corners of labels, chips, badges and panels (boxes, not pills).

Experimental: `ServerTicks.ping(id)` counts a tick ping once; the same ping twice is one tick. A ping with id 0 is no tick. `ServerTicks.serverGameTime()`: the world's game time the server last sent, a cross-check for the count. With Debug on it logs how the ids of the pings step.

Experimental: `ServerParticles.listen(listener)`: the particles the server sends, once per packet on the game's thread (type, position, count), for marks the server draws with particles.

Experimental: `AddonUpdates.offer(modId, owner, repository, jarName)`: an add-on takes part in Lumio's update check (Lumio takes only repositories it knows).

Experimental: `VisualFilters.add(...)`, an add-on's rule in the Visual Filter: a switch in a section of its own; an entity it matches is not drawn. Asked once per entity and again when its equipment changes.

Experimental: `HiddenEntities.set(ids)` and `clear()`: entities an add-on asks not to draw right now, by id (players too), followed at once; visual only.

Experimental: `Menus.openKeybinds()`: opens the menu's Keybinds page, for an add-on's "Set a key".

Experimental: `SlotLayer.addBehind(painter)`: drawing on a menu behind its items (over the background and the slot frames), for boxes that mark slots.

Experimental: `WaypointLook.labelSize(distance)`, how large a waypoint label is at a distance, for labels that look like Lumio's waypoints.

## 1.16.0 (with Lumio 1.0.0 Beta 2)

`EntityHighlighter.outlineWidth` and `glowWidth` (with defaults): Lumio's own outline instead of the game's glow, as wide as asked and with a soft glow, drawn only where the entity is seen, never through walls. In a frame where any glowing entity asks for it, all outlines of that frame are drawn this way.

## 1.15.0 (with Lumio 1.0.0 Beta 2)

`AddonPage.here` shows a "You are in ..." chip in a page's header while the player is where its features are used (the Dungeons page in a run).

## 1.14.0 (with Lumio 1.0.0 Beta 2)

`AddonPage.tabbed` shows a page's categories as tabs; `AddonPage.under` lists a page of its own under another one in the sidebar (the SkyBlock add-on's Dungeons page under SkyBlock). Pages of cards name their cards in a line above them.

Internal: `Services.chatNameColors()` (with a default) gives `LumioChat.prefix()` its two colors; the name in "[Lumio]" lines is now a soft gradient
between them.

## 1.13.0 (with Lumio 1.0.0 Beta 2)

`AddonFeature.cardSettings`: the one or two settings shown on a feature's card where its page shows features as cards (a group whose features all chose card settings is shown as a grid of cards, as the SkyBlock add-on's Dungeons page).

## 1.12.0 (with Lumio 1.0.0 Beta 2)

Experimental: `ServerTicks` (in `api.experimental.util`): real server ticks, counted from the ping the server sends every tick, with a listener for each. `WaypointCards.claim`: an add-on takes the place key's tap for a block (a point of the dungeon room the player is in).

`LocationProvider.placeAt`: the place a block is at by its coordinates alone, or "", for where an imported list of waypoints belongs. A
place's aliases may hold the server's own id of it as "id:<id>" (such as "id:mining_3"), which lists from other mods name it by.

Experimental: `WaypointCards`: the place card of the waypoints for an add-on's own points (such as points relative to a dungeon room), which gives the name
and color back instead of keeping the point in a group. `WaypointLook`: the glow on the block gave way to a filled box with a crisp outline
(`box`, `outline`, the same width on the screen at any distance), and `groundRing` for a ring on the ground around a point; `boxAt`, `outlineAt` and `line` for the parts of a block's shape (a slab, stairs), and `plainLabel` for a label without a box.

## 1.11.0 (with Lumio 1.0.0 Beta 2)

`LocationProvider.profileId`: an id of the player's current profile in the add-on's world, "" while unknown (the default). Waypoints are kept per
profile by it.

Experimental: `api.experimental.waypoint` with `WaypointColors`, the colors of waypoints in the order the Waypoints page shows them, and
`WaypointLook`: the beam, the glow on the block, the dotted line and the label pill every waypoint is drawn with. `WorldLabel` (in
`api.experimental.draw`, from the SkyBlock add-on): labels at a place in the world, drawn with the HUD; `place` finds a place on the screen
without a label's size, for marks with a size of their own.

## 1.10.0 (with Lumio 1.0.0 Beta 2)

`LumioChat`: the chat lines of Lumio and its add-ons, all with the same "[Lumio] " prefix (grey brackets, the name in the menu's accent
color). `line`, `info` and `alert` post a line; `prefixed` returns one, for a command's feedback.

## 1.9.0 (with Lumio 1.0.0 Beta 2)

`LocationProvider.has` takes the sky moods of the Sky page's Island skies: "sky:ember", "sky:void", "sky:dream", "sky:winter",
"sky:golden" and "sky:mist". A location that answers one of them gives the place that sky; the main mod blends its colors, the clouds' tint and the
sunset towards it and fades between places. No signature changed.

## 1.8.0 (with Lumio 1.0.0 Beta)

Experimental: `BreakingProgress` documents what 1 means: the moment the player can move on to the next block (with SkyBlock's Tick glide,
when its counter turns green, ping included), not necessarily the break. The block overlay fills to it exactly, without its own smoothing,
never goes back, holds full, and gives one short brighter cue when full. Without a source, or where it answers negative, the game's progress
is shown as before.

## 1.7.1 (with Lumio 1.0.0 Beta)

`FeatureSetProvider`: the Configs page no longer shows feature sets; it offers two whole presets instead (SkyBlock, Vanilla). An add-on's part of the SkyBlock preset is its file `assets/<add-on id>/presets/skyblock.json`. `sets()` and `switchOn()` are no longer called; `advanced()` still decides what the first-start setup turns on. No signature changed.

Experimental: `ModFiles.writeAtomically` writes through a temporary file of its own for every write, so two games writing the same file no longer share one.

Experimental: `BreakingProgress`, how far the block being mined is broken where the server breaks blocks itself.

Experimental: `HudTheme.blurBehind`, a blur of all that lies behind a full-screen overlay.

`LocationProvider.has`: new properties the ambience's Auto asks for: `ambience:snow`, `ambience:fireflies`, `ambience:mist`, `ambience:no_leaves`, `ambience:autumn`, `mood:ash`, `mood:sparks`, `mood:dust`, `mood:webs`. An add-on that does not know them answers false.

## 1.7.0 (with Lumio 1.0.0 Beta)

`AddonPanel.offerHeader`: the switches of the family header. `AddonFeature.hasPreview`, `drawPreview` and `previewNote` stay, without effect for now: the menu shows a feature's settings in its pane, without a preview. Deprecated: `AddonPanel.familyCard` and `PanelPicture`, since pages are laid out by their outlines and no card is drawn; `familyCard` only offers the header now.

Experimental: `StyledFeature.cardContext` is gone with the card. `Menus.openSettings`: opens the menu directly on a feature's settings, over the screen now open; going back returns to it.

## 1.6.0 (with Lumio 1.0.0 Beta)

`LocationProvider.zoneName` and `knownArea`: the smaller area, and which names are areas.

## 1.5.0 (with Lumio 1.0.0 Beta)

`AddonFeature.hasPreview`, `drawPreview` and `previewNote`: a live preview beside a feature's own page, which then gets the full-page layout.

Experimental: `SpeedStatusBar`, the value of the Speed status bar, for an add-on that knows it; `BlockPreview`, a block with a highlight as a picture; `SlotLayer`, drawing on a menu's slots under its tooltip.

## 1.4.0 (with Lumio 1.0.0 Beta)

`SettingFactory.derivedChoice`: a choice that saves nothing itself, read from and written to other settings. A mod's or add-on's `assets/<id>/page_outlines.txt` lays out its features' pages: groups, what is under Advanced, and the names of the rows; the saved keys stay.

## 1.3.0 (first public version, with Lumio 1.0.0 Beta)

Experimental: `TabSections`, which sections of a player list list names; `ShortNumber`, big numbers written short (100M, 12.3M, 1.25B); `HudTheme.line`, the color of a thin line on a panel; `ScoreLines.LINE`, a line among a scoreboard's lines; `ExtraStatusBar`, one more status bar from an add-on; `ScoreLines.locationStart`, `nameStart`, `padding` and `describe`, for a line's location symbol.

The API's first public state: mod id `lumio-api`, package `dev.tim1337.lumio.api`, `LumioAddon` and `LumioApi`, the entrypoint key `lumio`, and the `custom` key `lumio:api`.

### Experimental

- `util.CommandHints`: quiet text beside an entry of the game's command suggestions, and after the cursor while there is nothing to suggest
- `util.InventoryEffects`: asks the game's list of potion effects to step aside; `hud.HudEditor.shownBoxes`: where the HUD elements are, for what has to stay clear of them
- `draw.PanelLook`, `HudTheme.withLook` and `HudTheme.preview`: a theme of its own and a live preview for what is not a HUD panel, such as a label in the world
- `hud.StyledFeature`: gives such a feature the panels' settings card and a part in "Copy style to other panels"
- `util.ServerText.clean`: a dark color with a hue keeps it (dark red stays as it is sent, dark blue is lightened with its hue); only black and dark gray take the secondary color
- `util.WorldLabels`: what stands at a place in the world but is drawn with the HUD, under all of the HUD's elements
- `util.CameraTurn`: how much of the mouse's movement turns the camera, for a feature that locks or slows it while the player wants that.

### Stable

- `ChatTabProvider` and `AddonContext.registerChatTab`: a tab of the chat that picks which messages it shows
- `TooltipLineProvider`, `TooltipLine` and `AddonContext.registerTooltipLines`: lines for the tooltips of items, in the one separate block Lumio adds to what the game sends
- `TabCard.Row`: a bar can have a color of its own (`barRgb`); the constructor without it stays
- `AddonPanel.inEditorWhenHidden`: no signature change, but the HUD editor no longer shows such a panel by itself, only when asked for in its list.
- `Slider`: `logarithmic()` for a range over several orders of size, `snapTo(values)` for values the knob rests on, and `coins()` to show
  and type amounts of coins ("750k", "2.5M")
- The entrypoint (`LumioAddon`, `AddonInfo`, `LumioApi` with the API version) and `AddonContext` with every provider:
  locations, chat, outlines, suggestions, screens, info bar segments (`InfoSegment`) and values, status values, tab cards, Simulate, feature
  sets, the setup, the Home card, the scoreboard, the inventory column, block tints and diagnostics
- Features: `AddonFeature`, `AddonPanel` (with `frameSeconds()` for its animations), `AddonPage`, `FeatureView`, `SettingGroup`,
  `PanelPicture`, `KeyPlace`
- Settings: the setting types (`Toggle`, `Choice`, `Slider`, `ColorChoice`, `TextInput`, `Action`, `MultiToggle`, `OrderedChoice`,
  `InfoLine`) and their `SettingFactory`
- `MainSettings` (read-only main settings) and `Canvas` (the basic drawing). `MainSettings.celebrations()` is deprecated and always true: that
  setting now belongs to the add-on that has such moments

### Experimental

- `util/HudCover`: screens under which the HUD is not drawn at all.
- `util/ScreenInput`: `wantText()`, for a text field drawn by an add-on, so that typing reaches it on every version.
- `draw/Radius.WINDOW` added; `TextScale` reduced to `SMALL`, `BODY`, `HEADING`, `TITLE`; `Spring` presets reduced to `SNAPPY`, `DEFAULT`, `GENTLE` and `SMOOTH`.
- `PanelKit.stat`: a label that does not fit is set a little smaller instead of being cut.
- `PanelKit.tag`: a dark backing under the tint, so a dark rarity color stays readable over any panel.
- `util/ScreenInput`: mouse buttons, modifier keys and the letters of shortcuts in one meaning on every Minecraft version, since the game's numbers for them changed in 26.3
- The look, motion and helpers Lumio shares with its add-ons, in `api.experimental`: `draw` (`PanelKit`, `HudTheme`, `Draw`,
  `WorldDraw`, `BlockStyle`, `SystemColors`, `Radius`, `TextScale`, `SlideText`, `TextSelection`), `motion` (`Spring`, `Easing`,
  `AnimClock`), `util` (`Logs`, `Debug`, `ModFiles`, `HttpLimits`, `Guard`, `Sight`, `CommandText`, `TextColors`,
  `ServerText`), `ui` (`Toasts`, `UiSounds`) and `hud` (`HudEditor`)
- `Spring.follow(target, seconds)`: follows a target by a panel's frame time, and snaps there while the menu's animations are off
- `ModFiles`: without `migrate()`, as there are no older files to move
