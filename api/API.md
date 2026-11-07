# Lumio add-on API

The add-on API lets other mods add features to Lumio: pages in its menu, HUD panels in its look, info bar segments, status bar
values, tab list cards and more. Package `dev.tim1337.lumio.api`, a small Fabric mod of its own (mod id `lumio-api`,
the `api` subproject) that Lumio ships inside its jar. Current API version: **1.3.0** (`LumioApi.version()`).

The first add-on is *Lumio: Hypixel SkyBlock* (`lumio-skyblock`). It is a mod of its own (project
`Lumio-SkyBlock`) and uses nothing of Lumio but this API.

## Registering an add-on

An add-on is a Fabric mod with an entrypoint `lumio` whose class implements `LumioAddon`. Lumio finds it with
`FabricLoader.getEntrypointContainers("lumio", …)` while the game starts, after its own features exist and before the settings are
loaded, and calls `onInitialize(AddonContext)` once.

- Everything is registered in `onInitialize`. What it registers is kept only if it returns without an error: an add-on that fails halfway
  leaves nothing behind, and the Add-ons page shows it as "Could not start". Registering later is refused.
- An add-on's own Fabric events (HUD layers, ticks) cannot be taken back, so register them last.
- After the start, everything is turned into fixed lists: nothing is looked up or allocated while the game draws or ticks.

```json
"entrypoints": {
	"lumio": ["com.example.myaddon.MyAddon"]
},
"custom": {
	"lumio:api": ">=1.0.0 <2.0.0"
}
```

## Example: a feature with a HUD panel

This example uses only the stable API.

```java
public final class MyAddon implements LumioAddon {
	@Override
	public void onInitialize(AddonContext context) {
		CoinPanel coins = context.registerFeature(new CoinPanel());
		context.registerHud(coins, "My Add-on");
		context.registerPage(AddonPage.create("myaddon", "My Add-on", "What My Add-on adds.", 0xFFD60A, "coins")
			.category("Panels", coins));
		// last: the add-on's own Fabric registrations, which cannot be taken back
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath("myaddon", "coins"), coins::render);
	}
}

/** A panel in the HUD's glass look: the player moves, sizes and styles it in the HUD editor like the main mod's panels. */
final class CoinPanel extends AddonPanel {
	private static final int WIDTH = 120;
	private static final int HEIGHT = 22;
	private static final float RADIUS = 8f;
	private static final int GOLD = 0xFFFFD60A;
	private static final int WHITE = 0xFFFFFFFF;

	private final Toggle showIcon = add(settings().toggle("show_icon", Component.literal("Icon"), true), "General");
	private long coins;

	CoinPanel() {
		super("coin_panel", "Coins", "The coins you carry.", true, Anchor.LEFT, false, 12f, 120f);
		offerNoBackground();
	}

	@Override
	public int panelWidth() {
		return WIDTH;
	}

	@Override
	public int panelHeight() {
		return HEIGHT;
	}

	@Override
	public void drawPanel(Canvas canvas, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(canvas, WIDTH, HEIGHT, RADIUS);
		if (showIcon.get()) {
			canvas.icon("coins", 6, 6, 10, GOLD);
		}
		canvas.textAtScaledBold(Long.toString(coins), 20, 7, 1f, WHITE);
	}
}
```

Its name, version, description and API range come from its fabric.mod.json (`custom` › `lumio:api` for the range); an add-on can
also return them from `info()`.

## What an add-on can register (`AddonContext`)

| Method | What it adds |
|---|---|
| `settings()` | The factory of settings (also `LumioApi.settings()` and, inside a feature, `settings()`) |
| `registerFeature` | A feature: an `AddonFeature` (switch, settings, its page) or an `AddonPanel` (a HUD panel) |
| `registerPage` | A sidebar page made with `AddonPage.create`: categories, category pages, a master switch |
| `registerHud(panel, group)` | A panel in the HUD editor's element list, in a group |
| `registerSimulator` | Activities the HUD editor's Simulate menu can play, with their moments |
| `registerInfoValues` | Info bar segments (`InfoSegment`: id, name, icon, width, where it is listed, its settings) and their values |
| `registerStatusValues` | Health and max, mana, defense, absorption, overflow for the status bars, read from the action bar, with a priority |
| `registerTabCards` | Info cards of the tab list |
| `registerLocation` | Where the player is: the current place, its group, the known places (keybinds "Where it works", the Home card) |
| `registerPlaceFeatures` | The features the Home "You are in" card offers for a place |
| `registerChatListener` | Server messages, chat lines and the action bar, from the main mod's own mixins |
| `registerHighlighter` | Entity outlines (only while the entity is visible to the player) |
| `registerSuggestions` | Command suggestions |
| `registerScreenHooks` | Drawing and input on container screens, or taking a screen over (storage overlay) |
| `registerFeatureSets`, `registerSetup` | Feature sets on the Configs page; the "How you play" step of the setup |
| `registerScoreboard`, `registerInventoryColumn`, `registerBlockTint` | Scoreboard lines, a column next to the inventory (drawn on the inventory's canvas), a block overlay tint |
| `registerDiagnostics` | Lines for the `/lumio` debug output |

## Stable and experimental

The API has two parts. **Third-party add-ons should use the stable part**: it follows the version rules below, so an add-on built for 1.x
keeps working with every later 1.x. The **experimental** part (package `api.experimental`) is what Lumio shares with its own
add-ons: it may change or go in any version, without a new API version, and its changes are listed in the API changelog
(`api/CHANGELOG.md`). Lumio's own add-ons, such as the SkyBlock one, may use both.

The stable part:

- **Entrypoint and registration**: `LumioAddon`, `AddonInfo`, `LumioApi` (the API version, the settings factory, save
  soon), `AddonContext` and every provider it takes (with their records, such as `InfoSegment`, `TabCard`, `Simulation`).
- **Features** (`feature/`): `AddonFeature` (id, name, switch, settings with `add`, icon, child features, keys, saving), `AddonPanel` (size,
  `drawPanel`, glass, header and settings card shared by all panels, modes, parts the editor can hide, Simulate, `frameSeconds()`),
  `AddonPage`, `FeatureView` (a feature of either part, as providers see it), `SettingGroup`, `PanelPicture`, `KeyPlace`.
- **Settings** (`setting/`): `Toggle`, `Choice` (segmented, radio, dropdown), `Slider` (with value field; linear or logarithmic, with snap values, as a percentage or an amount of coins), `ColorChoice`, `TextInput`,
  `Action` (a button), `MultiToggle`, `OrderedChoice` (a list to switch on and order), `InfoLine`, made by `SettingFactory`.
- **Main settings, read-only**: `MainSettings` (animations, bounce, accent, the restyled inventory, the info bar's place).
- **Drawing**: `Canvas` (`draw/`): UI fonts, rounded shapes, rings and arcs, icons, the accent.

The experimental part (`api.experimental`):

- `draw`: `PanelKit` (the panels' sizes, rows, bars, rings, tags, number formats), `HudTheme` (panel text colors and glass), `SystemColors`,
  `Radius`, `TextScale`, `Draw` (window fade, color math, a canvas for drawing outside a panel), `WorldDraw` and `BlockStyle` (block
  highlights in the block overlay's look), `SlideText`, `TextSelection`.
- `motion`: `Spring` (with `follow(target, seconds)` for a panel's `frameSeconds()`), `Easing`, `AnimClock`.
- `ui`: `Toasts`, `UiSounds`; `hud`: `HudEditor`.
- `util`: `Logs` (the home folder hidden in messages), `Debug`, `ModFiles` (the mod's config folder, atomic writes), `HttpLimits`, `Guard`,
  `Sight` (in view and in line of sight), `CommandText`, `TextColors`, `ServerText`, `BreakingProgress` (how far the mined block is along, from an add-on
  that knows the server's timing: the block overlay's breaking fill asks it first and falls back to the game's progress; 1 means the next
  block can be started, then the fill holds full).

`api.spi` is the internal bridge to the running Lumio: not part of the API promise, and never used by add-ons. Nothing of the
stable part uses the experimental part or the bridge in its signatures (`ApiSnapshotTest` checks it).

Rules every add-on follows:

- Places both parts touch (action bar, chat, tab list, entity outlines, screens) are owned by the main mod's mixins; use the callbacks, never
  mix into the same method.
- Providers and panels are called from render and tick code: return quickly, allocate nothing per frame, keep texts until their source changes.
- An add-on's settings are saved in `config/lumio/addons/<addon id>.json` and travel with shared configs; private values and
  keybinds follow the main mod's import rules.
- Lumio's server rules apply: visual and informational only, no packets or automation, nothing the vanilla client hides.

## Versions

The API has its own version, separate from the mod version: MAJOR.MINOR.PATCH. The rules cover the stable part; the experimental part
changes without a new version (its changes are listed in `api/CHANGELOG.md`, as are the stable ones).

- **Adding** something (a class, a method, a default method on an interface) raises MINOR: 1.0.0 → 1.1.0.
- **Changing or removing** something raises MAJOR and is avoided: mark it `@Deprecated` first, remove it in the next major version.
- Fixes without a signature change raise PATCH.
- New methods on interfaces that add-ons implement always get a default, so older add-ons keep working.
- An add-on declares the range it works with, usually `>=1.<minor it needs>.0 <2.0.0`.

`api-snapshot.txt` lists the whole stable API. `ApiSnapshotTest` fails the build when the API changed without the file updated, when
something was changed or removed without a new major version, and when something was added without at least a new minor version. After
raising `LumioApi.VERSION`, write the file anew with `./gradlew test -PupdateApiSnapshot`. Until the first release the API stays
1.0.0 and the file is written anew with each change.

Release order: the main mod (with the API) first, then the add-ons built against it.

## Building an add-on

Lumio publishes the API and itself to the local Maven repository: `./gradlew publishToMavenLocal` in its project gives
`dev.tim1337:lumio-api:<API version>` (to compile against) and `dev.tim1337:lumio:<mod version>-SNAPSHOT` (to run with).
An add-on compiles against the API only and never bundles it or Lumio:

```groovy
repositories {
	mavenLocal()
}

dependencies {
	compileOnly "dev.tim1337:lumio-api:1.3.0"
	// the game in development, with Lumio and its API nested inside, as in a real install
	localRuntime("dev.tim1337:lumio:1.0.0-SNAPSHOT") {
		transitive = false
	}
}
```

Its `fabric.mod.json` depends on the API range it works with, which Fabric checks before the game starts:

```json
"depends": {
	"lumio-api": ">=1.0.0 <2.0.0",
	"minecraft": "~26.2",
	"fabric-api": "*"
}
```

Lumio's `fabric.mod.json` has a `"breaks"` section (empty for now) for add-on versions that are known to be broken. An add-on's
`assets/<add-on id>/changelog.txt` (in Lumio's changelog format) is shown in Lumio's changelog after its own, and
`assets/<add-on id>/credits.txt` (a line `## Section`, then `Name | what was taken from it` per credit) on its Credits screen.
