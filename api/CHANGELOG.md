# Lumio API changelog

The changes of the add-on API (mod id `lumio-api`), by API version. The **stable** part follows the version rules in API.md: adding
raises the minor version, changing or removing raises the major version. The **experimental** part (`api.experimental`) may change in any
version, without a new version; its changes are listed here too, under "Experimental", so add-ons that use it can follow them.

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
