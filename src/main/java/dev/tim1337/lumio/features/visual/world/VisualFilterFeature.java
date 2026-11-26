package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.filter.VisualFilters;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaves out what only clutters the view: it changes what is drawn and never what is sent or how the game plays. Entities stay in the world (other
 * features read them: the lightning for the sky's flash, armor stands for the dungeon helpers); they are only not drawn, and pure clutter nothing
 * reads (experience orbs, falling blocks) is not ticked either. Screen overlays (fire, the Nether portal, underwater) came here from the Camera
 * module. The fog switches take out the fog of the open air only ({@link FogFilter}). Add-ons add rules of their own ({@link VisualFilters}). Rules of entities are asked once when an entity appears and again when its
 * equipment changes, never every frame.
 */
public class VisualFilterFeature extends Feature {
	/** How the burning overlay is drawn. */
	public enum Fire {
		NORMAL("Normal"), LOWER("Lower"), HIDE("Hide");

		private final String label;

		Fire(String label) {
			this.label = label;
		}

		public Component displayName() {
			return Component.literal(label);
		}
	}

	/** Whether an overlay is drawn as in the game or not at all. */
	public enum Overlay {
		NORMAL("Normal"), HIDE("Hide");

		private final String label;

		Overlay(String label) {
			this.label = label;
		}

		public Component displayName() {
			return Component.literal(label);
		}
	}

	private static final String WORLD = "In the world";
	private static final String SCREEN = "On the screen";
	private static final String FOG = "Fog";
	private static final int BLOCKS_PER_CHUNK = 16;
	/** A name tag of a dying mob stands this close to it, across and up, in blocks. */
	private static final double TAG_ACROSS = 1.5;
	private static final double TAG_UP = 3.5;
	private static final int MOST_CACHED = 4096;

	private final BooleanSetting fallingBlocks = add(new BooleanSetting("falling_blocks", Component.literal("Falling blocks"), true), WORLD,
		null);
	private final BooleanSetting lightning = add(new BooleanSetting("lightning", Component.literal("Lightning bolts"), true), WORLD,
		"The flash in the sky stays");
	private final BooleanSetting orbs = add(new BooleanSetting("experience_orbs", Component.literal("Experience orbs"), true), WORLD);
	private final BooleanSetting deaths = add(new BooleanSetting("death_animation", Component.literal("Death animation"), true), WORLD,
		"Dying mobs vanish instead of falling over");
	private final BooleanSetting deathTags = add(new BooleanSetting("death_tags", Component.literal("Name tags too"), false), WORLD,
		null);
	private final BooleanSetting explosions = add(new BooleanSetting("explosion_particles", Component.literal("Explosion particles"), false), WORLD);
	private final BooleanSetting breakingParticles = add(new BooleanSetting("block_breaking_particles", Component.literal("Block breaking particles"), false),
		WORLD, "The cracks on the block stay");
	private final EnumSetting<Fire> fire = add(new EnumSetting<>("fire", Component.literal("Fire overlay"), Fire.HIDE, Fire::displayName,
		EnumSetting.Style.SEGMENTED), SCREEN, null);
	private final BooleanSetting fireIcon = add(new BooleanSetting("fire_icon", Component.literal("Small fire icon"), true), SCREEN);
	private final EnumSetting<Overlay> portal = add(new EnumSetting<>("portal", Component.literal("Nether portal"), Overlay.NORMAL, Overlay::displayName,
		EnumSetting.Style.SEGMENTED), SCREEN, "The swirl and the wobble");
	private final EnumSetting<Overlay> underwater = add(new EnumSetting<>("underwater", Component.literal("Underwater"), Overlay.NORMAL,
		Overlay::displayName, EnumSetting.Style.SEGMENTED), SCREEN, "The water's texture over the screen");
	private final BooleanSetting noDistanceFog = add(new BooleanSetting("no_distance_fog", Component.literal("No distance fog"), false), FOG,
		"Far land still fades out at the edge");
	private final BooleanSetting noThickFog = add(new BooleanSetting("no_thick_fog", Component.literal("No thick fog"), false), FOG,
		"Nether and boss fights");
	private final BooleanSetting noSkyFog = add(new BooleanSetting("no_sky_fog", Component.literal("No sky fog"), false), FOG,
		"A thin, soft line stays at the horizon");

	/** The add-ons' rules, each with its switch. */
	private final List<VisualFilters.Rule> rules = new ArrayList<>();
	private final List<BooleanSetting> ruleSwitches = new ArrayList<>();
	/** What the add-ons' rules said of an entity, by its id, and the equipment they said it of: asked again only when that changes. */
	private final Int2IntOpenHashMap ruleSaid = new Int2IntOpenHashMap();
	private final Int2IntOpenHashMap ruleSaidFor = new Int2IntOpenHashMap();
	/** The name tags of the mobs dying now, by id, found once per tick. */
	private final IntOpenHashSet dyingTags = new IntOpenHashSet();
	private ClientLevel cachedFor;

	/** Whether the fog of this frame is the land's own (not water, lava, powder snow or an effect) with the edge to be kept clear. */
	private boolean clearEdge;

	/** What the Camera module had of the overlays before they came here, until this module has settings of its own. */
	private JsonObject fromCamera;
	private boolean ownLoaded;

	public VisualFilterFeature() {
		super("visual_filter", "Visual Filter", "Hides clutter. Nothing is sent.", false);
		iconRgb(SystemColors.TEAL_RGB);
		iconName("eye");
		summary("Less clutter in the world and on the screen");
		deathTags.visibleWhen(deaths::get);
		fireIcon.visibleWhen(() -> fire.get() == Fire.HIDE);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	/** An add-on's rule, with its switch in a section of its own. */
	public void addRule(String key, String label, String description, String section, boolean on, VisualFilters.Rule rule) {
		BooleanSetting toggle = add(new BooleanSetting(key, Component.literal(label), on), section, description);
		rules.add(rule);
		ruleSwitches.add(toggle);
	}

	/** @return whether an entity is not drawn now; cheap, as it is asked for every entity every frame */
	public boolean hides(Entity entity) {
		if (!isEnabled() || entity instanceof Player) {
			return false;
		}
		if (entity instanceof FallingBlockEntity) {
			return fallingBlocks.get();
		}
		if (entity instanceof LightningBolt) {
			return lightning.get();
		}
		if (entity instanceof ExperienceOrb) {
			return orbs.get();
		}
		if (entity instanceof LivingEntity living && !(entity instanceof ArmorStand) && living.isDeadOrDying()) {
			return deaths.get();
		}
		if (deathTags.get() && deaths.get() && !dyingTags.isEmpty() && dyingTags.contains(entity.getId())) {
			return true;
		}
		return !rules.isEmpty() && byRules(entity);
	}

	/** @return whether the client may skip ticking an entity: only pure clutter nothing reads, while it is not drawn */
	public boolean skipsTick(Entity entity) {
		return (entity instanceof ExperienceOrb || entity instanceof FallingBlockEntity) && hides(entity);
	}

	/** @return whether a particle is not made: the puffs and blasts of explosions, when that is on */
	public boolean hidesParticle(ParticleOptions options) {
		return isEnabled() && explosions.get() && (options.getType() == ParticleTypes.EXPLOSION || options.getType() == ParticleTypes.EXPLOSION_EMITTER);
	}

	/**
	 * @return whether the bits of a block being mined or broken are not made. Only those: a block particle the server sends, or one another
	 *         part of the game makes, is none of them
	 */
	public boolean hidesBreakingParticles() {
		return isEnabled() && breakingParticles.get();
	}

	private boolean byRules(Entity entity) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != cachedFor || ruleSaid.size() > MOST_CACHED) {
			cachedFor = level;
			ruleSaid.clear();
			ruleSaidFor.clear();
		}
		int id = entity.getId();
		int equipment = equipmentOf(entity) * 31 + switchesOn();
		if (ruleSaidFor.containsKey(id) && ruleSaidFor.get(id) == equipment) {
			return ruleSaid.get(id) != 0;
		}
		boolean hidden = false;
		for (int i = 0; i < rules.size() && !hidden; i++) {
			hidden = ruleSwitches.get(i).get() && rules.get(i).hides(entity);
		}
		ruleSaid.put(id, hidden ? 1 : 0);
		ruleSaidFor.put(id, equipment);
		return hidden;
	}

	/** @return which of the add-ons' rules are on, as bits: what a rule said is asked again when one is switched */
	private int switchesOn() {
		int on = 0;
		for (int i = 0; i < ruleSwitches.size(); i++) {
			on = on * 2 + (ruleSwitches.get(i).get() ? 1 : 0);
		}
		return on;
	}

	/** @return a number that changes when what the entity wears or holds changes (the game puts new stacks in then) */
	private static int equipmentOf(Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return 0;
		}
		return System.identityHashCode(living.getItemBySlot(EquipmentSlot.HEAD)) * 31 + System.identityHashCode(living.getItemBySlot(EquipmentSlot.MAINHAND));
	}

	/** Once per tick: the name tags standing over mobs that are dying, while those are left out. */
	public void tick(Minecraft client) {
		dyingTags.clear();
		if (!isEnabled() || !deaths.get() || !deathTags.get() || client.level == null) {
			return;
		}
		List<LivingEntity> dying = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof LivingEntity living && !(entity instanceof ArmorStand) && !(entity instanceof Player) && living.isDeadOrDying()) {
				dying.add(living);
			}
		}
		if (dying.isEmpty()) {
			return;
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof ArmorStand stand && stand.isInvisible() && stand.hasCustomName()) {
				for (LivingEntity mob : dying) {
					double up = stand.getY() - mob.getY();
					if (Math.abs(stand.getX() - mob.getX()) <= TAG_ACROSS && Math.abs(stand.getZ() - mob.getZ()) <= TAG_ACROSS && up >= 0 && up <= TAG_UP) {
						dyingTags.add(stand.getId());
						break;
					}
				}
			}
		}
	}

	/** @return how the burning overlay is drawn */
	public Fire fire() {
		return isEnabled() ? fire.get() : Fire.NORMAL;
	}

	/** @return whether a small fire icon stands in for the hidden burning overlay */
	public boolean fireIcon() {
		return fire() == Fire.HIDE && fireIcon.get();
	}

	public boolean hidePortal() {
		return isEnabled() && portal.get() == Overlay.HIDE;
	}

	public boolean hideUnderwater() {
		return isEnabled() && underwater.get() == Overlay.HIDE;
	}

	/**
	 * Takes the fog the switches leave out from the game's fog of the open air, right after the game set it up. It is only called for that fog:
	 * water, lava, powder snow, Blindness and Darkness hide things as part of the game and stay as they are. With every switch off nothing is
	 * touched, so another mod's fog stays its own.
	 */
	public void clearFog(FogData fog, Camera camera, float renderDistance, float partialTick) {
		clearEdge = false;
		if (!isEnabled() || !(noDistanceFog.get() || noThickFog.get() || noSkyFog.get())) {
			return;
		}
		var probe = camera.attributeProbe();
		float placeEnd = probe.getValue(EnvironmentAttributes.FOG_END_DISTANCE, partialTick);
		boolean bossFog = Minecraft.getInstance().gui.hud.getBossOverlay().shouldCreateWorldFog();
		boolean thick = FogFilter.thick(placeEnd, renderDistance, bossFog);
		clearEdge = noDistanceFog.get();
		if (thick ? noThickFog.get() : noDistanceFog.get()) {
			// rain draws the game's fog in; that share of it stays, a boss fight's fog has none
			float weather = thick ? 0f : FogFilter.weather(placeEnd, fog.environmentalEnd);
			fog.environmentalStart = FogFilter.cleared(fog.environmentalStart, FogFilter.edgeStart(renderDistance, clearEdge), weather);
			fog.environmentalEnd = FogFilter.cleared(fog.environmentalEnd, renderDistance, weather);
			if (bossFog) {
				// the boss fight's fog also drew the sky and the clouds in: back to the place's own
				fog.skyEnd = Math.min(renderDistance, probe.getValue(EnvironmentAttributes.SKY_FOG_END_DISTANCE, partialTick));
				fog.cloudEnd = Math.min(Minecraft.getInstance().options.cloudRange().get() * BLOCKS_PER_CHUNK,
					probe.getValue(EnvironmentAttributes.CLOUD_FOG_END_DISTANCE, partialTick));
			}
		}
		if (noSkyFog.get()) {
			fog.skyEnd = Math.max(fog.skyEnd, FogFilter.SKY_EDGE_BLOCKS);
		}
	}

	/** Once the game has set where the land fades out at the edge of the loaded world: with no distance fog only over its last blocks. */
	public void clearEdge(FogData fog) {
		if (clearEdge) {
			clearEdge = false;
			fog.renderDistanceStart = Math.max(fog.renderDistanceStart, FogFilter.edgeStart(fog.renderDistanceEnd, true));
		}
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		ownLoaded = json.has("enabled");
		if (!ownLoaded && fromCamera != null) {
			applyFromCamera();
		}
	}

	/**
	 * The overlays the Camera module had before they came here. Where it had any of them set, this module is turned on with just those: the overlay
	 * values as they were, the rest of the filter off and no icon, so nothing changes for the player. Only while this module has no settings of its own.
	 */
	public void takeOverFromCamera(JsonObject camera) {
		fromCamera = camera;
		if (!ownLoaded) {
			applyFromCamera();
		}
	}

	private void applyFromCamera() {
		JsonObject camera = fromCamera;
		fromCamera = null;
		if (!cameraHadOverlays(camera)) {
			return;
		}
		JsonObject moved = new JsonObject();
		for (String key : new String[]{"fire", "portal", "underwater"}) {
			JsonElement value = camera.get(key);
			moved.add(key, value != null ? value : new com.google.gson.JsonPrimitive("NORMAL"));
		}
		for (String key : new String[]{"falling_blocks", "lightning", "experience_orbs", "death_animation", "death_tags", "explosion_particles", "fire_icon"}) {
			moved.addProperty(key, false);
		}
		moved.addProperty("enabled", camera.get("enabled") == null || camera.get("enabled").getAsBoolean());
		super.load(moved);
	}

	/** @return whether a saved Camera module had any of the overlays set to something other than the game's own */
	static boolean cameraHadOverlays(JsonObject camera) {
		for (String key : new String[]{"fire", "portal", "underwater"}) {
			JsonElement value = camera.get(key);
			if (value != null && value.isJsonPrimitive() && !"NORMAL".equals(value.getAsString())) {
				return true;
			}
		}
		return false;
	}
}
