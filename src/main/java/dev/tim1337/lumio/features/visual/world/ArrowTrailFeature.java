package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.render.OutlineRenderTypes;
import dev.tim1337.lumio.render.EchoLines;

import org.joml.Vector3fc;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Trails behind flying arrows and ender pearls, and the settings of the trail behind the swung item ({@link SwingTrail}): one look for all of them, with a look of their own under Advanced.
 * <p>
 * Built to be cheap, since a bow can fire many arrows at once. There are no particles: each arrow only keeps its recent positions (one per tick),
 * and all trails of a frame are drawn as camera-facing ribbons in a single call, with the vanilla lightning render type, which adds its light to
 * what is behind. Distant arrows, arrows stuck in the ground, and arrows beyond the configured limit get no trail.
 */
public class ArrowTrailFeature extends Feature {
	/** Positions kept per arrow, one per tick. */
	private static final int HISTORY = 20;
	/** Maximum points of one trail after subdivision. */
	private static final int MAX_POINTS = 72;

	private static final String SHOW = "Show on";
	private static final String LOOK = "Look";
	private static final String ARROWS = "Arrows";
	private static final String PEARLS = "Ender pearls";
	private static final String ALL = "All trails";

	private final BooleanSetting onArrows = add(new BooleanSetting("on_arrows", Component.literal("Arrows"), true), SHOW);
	private final BooleanSetting onPearls = add(new BooleanSetting("on_pearls", Component.literal("Ender pearls"), true), SHOW);
	private final BooleanSetting others = add(new BooleanSetting("others", Component.literal("Other players' arrows"), false), SHOW);
	private final BooleanSetting impactBurst = add(new BooleanSetting("impact_burst", Component.literal("Impact burst"), true), SHOW, "A quick ring and a few sparks where an arrow lands");
	private final BooleanSetting pearlRift = add(new BooleanSetting("pearl_rift", Component.literal("Pearl rift"), true), SHOW, "A dark tear behind a pearl, and a small portal where you land");

	private final EnumSetting<TrailLook.Kind> style = add(new EnumSetting<>("style", Component.literal("Style"), TrailLook.Kind.SILK, TrailLook.Kind::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final EnumSetting<TrailLook.TrailColors> colors = add(new EnumSetting<>("color_source", Component.literal("Color"), TrailLook.TrailColors.ACCENT, TrailLook.TrailColors::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final ColorSetting color = add(new ColorSetting("color", Component.literal("Custom color"), 0xFF7A18).withOpacity(1f), LOOK);
	private final FloatSetting length = add(new FloatSetting("length", Component.literal("Length"), 7f, 2f, 20f, 1), LOOK);
	private final FloatSetting width = add(new FloatSetting("width", Component.literal("Width"), 1f, 0.4f, 2.5f, 2), LOOK);
	private final EnumSetting<TrailLook.OwnKind> arrowStyle = add(new EnumSetting<>("arrow_style", Component.literal("Style"), TrailLook.OwnKind.SAME, TrailLook.OwnKind::displayName), ARROWS);
	private final EnumSetting<TrailLook.OwnColors> arrowColors = add(new EnumSetting<>("arrow_colors", Component.literal("Color"), TrailLook.OwnColors.SAME, TrailLook.OwnColors::displayName), ARROWS);
	private final ColorSetting arrowColor = add(new ColorSetting("arrow_color", Component.literal("Custom color"), 0xFF7A18).withOpacity(1f), ARROWS);
	private final EnumSetting<TrailLook.OwnKind> pearlStyle = add(new EnumSetting<>("pearl_style", Component.literal("Style"), TrailLook.OwnKind.SAME, TrailLook.OwnKind::displayName), PEARLS);
	private final EnumSetting<TrailLook.OwnColors> pearlColors = add(new EnumSetting<>("pearl_colors", Component.literal("Color"), TrailLook.OwnColors.SAME, TrailLook.OwnColors::displayName), PEARLS);
	private final ColorSetting pearlColor = add(new ColorSetting("pearl_color", Component.literal("Custom color"), 0xA050FF).withOpacity(1f), PEARLS);
	private final FloatSetting fadeTime = add(new FloatSetting("fade_time", Component.literal("Fade time"), 250f, 0f, 800f, 0), ALL, "In milliseconds");
	private final FloatSetting range = add(new FloatSetting("range", Component.literal("Max distance"), 48f, 16f, 128f, 0), ALL);
	private final FloatSetting maxArrows = add(new FloatSetting("max_arrows", Component.literal("Max trails at once"), 40f, 5f, 150f, 0), ALL);

	private static final int BURSTS = 16;
	private static final long BURST_NANOS = 400_000_000L;
	private static final long PORTAL_NANOS = 900_000_000L;
	private static final int RING_PIECES = 18;
	private static final int BURST_SPARKS = 7;
	private static final float BURST_RADIUS = 0.7f;
	private static final float PORTAL_RADIUS = 0.65f;
	private static final int RIFT_RGB = 0x0A0614;
	private final double[][] burstAt = new double[BURSTS][3];
	private final long[] burstStart = new long[BURSTS];
	private final boolean[] burstPortal = new boolean[BURSTS];
	private int nextBurst;

	private static final float HELIX_TURNS = 16f;
	private static final float HELIX_SPEED = 9f;
	private static final float HELIX_RADIUS = 0.13f;
	/** How wide the faint sheet along the path of an Echo is, in blocks, and how many copies sit between two points of the path. */
	private static final float ECHO_SHEET_WIDTH = 0.1f;
	private static final int ECHO_COPIES_PER_STEP = 10;
	/** The outline of an arrow, tip first, from its head back. */
	private static final float[][] ECHO_ARROW = {{0f, 0f}, {-0.1f, 0.06f}, {-0.1f, 0.016f}, {-0.48f, 0.016f}, {-0.56f, 0.06f}, {-0.64f, 0.06f}, {-0.58f, 0f}, {-0.64f, -0.06f}, {-0.56f, -0.06f}, {-0.48f, -0.016f}, {-0.1f, -0.016f}, {-0.1f, -0.06f}};
	private static final float[][] ECHO_PEARL = {{0.13f, 0f}, {0.1f, 0.085f}, {0.04f, 0.125f}, {-0.04f, 0.125f}, {-0.1f, 0.085f}, {-0.13f, 0f}, {-0.1f, -0.085f}, {-0.04f, -0.125f}, {0.04f, -0.125f}, {0.1f, -0.085f}};

	private static final class Trail {
		final int id;
		Entity entity;
		/** An ender pearl, whose fire trail uses the ender colors. */
		final boolean pearl;
		/** Whether what flies has come to its end, for the burst or the portal. */
		boolean ended;
		final double[] history = new double[HISTORY * 3];
		int count;
		/** The tick the arrow was last seen flying. */
		long seen;

		Trail(Entity entity) {
			this.id = entity.getId();
			this.entity = entity;
			this.pearl = entity instanceof ThrownEnderpearl;
		}

		void push(double x, double y, double z, int keep) {
			while (count >= keep) {
				System.arraycopy(history, 3, history, 0, (count - 1) * 3);
				count--;
			}
			history[count * 3] = x;
			history[count * 3 + 1] = y;
			history[count * 3 + 2] = z;
			count++;
		}

		/** Drops the oldest position, so a stopped arrow's trail retracts from its end. */
		void retract() {
			if (count > 0) {
				System.arraycopy(history, 3, history, 0, (count - 1) * 3);
				count--;
			}
		}
	}

	private final Map<Integer, Trail> trails = new HashMap<>();
	private long tickCount;

	// per-frame work buffers, reused
	private final float[] px = new float[MAX_POINTS];
	private final float[] py = new float[MAX_POINTS];
	private final float[] pz = new float[MAX_POINTS];
	private final float[] pu = new float[MAX_POINTS];
	private final float[] sx = new float[MAX_POINTS];
	private final float[] sy = new float[MAX_POINTS];
	private final float[] sz = new float[MAX_POINTS];

	public ArrowTrailFeature() {
		super("arrow_trails", "Trails", "Arrows and pearls leave a trail.", true);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("arrow");
		summary("Trails behind arrows and ender pearls");
		color.under(colors, () -> colors.get() == TrailLook.TrailColors.CUSTOM);
		arrowColor.under(arrowColors, () -> arrowColors.get() == TrailLook.OwnColors.CUSTOM);
		pearlColor.under(pearlColors, () -> pearlColors.get() == TrailLook.OwnColors.CUSTOM);
		addResetButton();
	}

	// ------------------------------------------------------------------------------------------------------ tick

	/** Called every tick: flying arrows record another position, and stopped arrows' trails shrink. */
	public void tick(Minecraft client) {
		tickCount++;
		if (!isEnabled() || client.level == null || client.player == null) {
			trails.clear();
			return;
		}
		if (client.isPaused()) {
			return;
		}
		if (trails.isEmpty() && !onArrows.get() && !onPearls.get()) {
			return;
		}
		int budget = Math.round(maxArrows.get());
		double reach = range.get();
		double reachSquared = reach * reach;
		Vec3 me = client.player.position();
		int keep = Mth.clamp((int) Math.ceil(length.get() / 1.2f) + 2, 4, HISTORY);
		for (Entity entity : client.level.entitiesForRendering()) {
			boolean shot = (onArrows.get() && entity instanceof AbstractArrow) || (onPearls.get() && entity instanceof ThrownEnderpearl);
			if (!shot || entity.isRemoved()) {
				continue;
			}
			Projectile arrow = (Projectile) entity;
			// an arrow stuck in the ground does not move
			if (arrow.getDeltaMovement().lengthSqr() < 0.04) {
				continue;
			}
			if (arrow.distanceToSqr(me) > reachSquared) {
				continue;
			}
			Trail trail = trails.get(arrow.getId());
			if (trail == null) {
				if (!others.get() && !isOwn(arrow, client.player)) {
					continue;
				}
				if (trails.size() >= budget) {
					continue;
				}
				trail = new Trail(arrow);
				trails.put(arrow.getId(), trail);
				trail.push(arrow.xo, arrow.yo, arrow.zo, keep);
			}
			trail.entity = arrow;
			trail.seen = tickCount;
			trail.push(arrow.getX(), arrow.getY(), arrow.getZ(), keep);
		}
		// a trail whose arrow stopped goes within the fade time
		boolean giveUp = tickCount % TrailLook.retractEvery(fadeTime.get(), keep) == 0L;
		Iterator<Trail> it = trails.values().iterator();
		while (it.hasNext()) {
			Trail trail = it.next();
			if (trail.seen != tickCount) {
				if (!trail.ended) {
					trail.ended = true;
					landed(trail);
				}
				if (giveUp) {
					trail.retract();
				}
				// a trail that has not been seen for a long time, or has nothing left, is removed
				if (trail.count < 2 || tickCount - trail.seen > HISTORY) {
					it.remove();
				}
			}
		}
	}

	/** What flew has landed: an arrow bursts, a pearl leaves a small portal. */
	private void landed(Trail trail) {
		if (trail.entity == null || (trail.pearl ? !pearlRift.get() : !impactBurst.get())) {
			return;
		}
		int i = nextBurst;
		nextBurst = (nextBurst + 1) % BURSTS;
		burstAt[i][0] = trail.entity.getX();
		burstAt[i][1] = trail.entity.getY();
		burstAt[i][2] = trail.entity.getZ();
		burstStart[i] = System.nanoTime();
		burstPortal[i] = trail.pearl;
	}

	private boolean anyBurst(long now) {
		for (int i = 0; i < BURSTS; i++) {
			if (burstStart[i] != 0L && now - burstStart[i] < (burstPortal[i] ? PORTAL_NANOS : BURST_NANOS)) {
				return true;
			}
		}
		return false;
	}

	/** The bursts and the portals: dark first, in the plain pass, and light on top of it. */
	private void drawBursts(VertexConsumer consumer, PoseStack.Pose pose, Vec3 cam, Vector3fc left, Vector3fc up, boolean additive) {
		long now = System.nanoTime();
		float scale = width.get();
		for (int i = 0; i < BURSTS; i++) {
			if (burstStart[i] == 0L) {
				continue;
			}
			int rgb = rgbOf(burstPortal[i]);
			float age = (now - burstStart[i]) / (float) (burstPortal[i] ? PORTAL_NANOS : BURST_NANOS);
			if (age >= 1f) {
				continue;
			}
			float x = (float) (burstAt[i][0] - cam.x);
			float y = (float) (burstAt[i][1] - cam.y);
			float z = (float) (burstAt[i][2] - cam.z);
			px[0] = x;
			py[0] = y;
			pz[0] = z;
			float fade = 1f - age;
			if (burstPortal[i]) {
				// it opens quickly and closes slowly
				float open = Math.min(1f, age * 6f) * fade;
				if (additive) {
					ring(consumer, pose, x, y, z, left, up, PORTAL_RADIUS * open, 0.02f * scale, argb(rgb, fade));
				} else {
					billboard(consumer, pose, PORTAL_RADIUS * 2.2f * open, left, up, RIFT_RGB, 0.9f * fade, 0);
				}
			} else if (additive) {
				float eased = 1f - fade * fade;
				ring(consumer, pose, x, y, z, left, up, BURST_RADIUS * eased, 0.018f * scale, argb(rgb, fade));
				billboard(consumer, pose, 0.7f * scale * fade, left, up, mix(rgb, 0xFFFFFF, 0.5f), 0.7f * fade, 0);
				for (int spark = 0; spark < BURST_SPARKS; spark++) {
					float angle = spark * Mth.TWO_PI / BURST_SPARKS + noise(i, spark, 3) * 0.7f;
					float from = BURST_RADIUS * 0.5f * eased * (0.7f + noise(i, spark, 5) * 0.6f);
					float to = from + 0.16f * fade;
					float cos = Mth.cos(angle);
					float sin = Mth.sin(angle);
					float ax = -left.x() * cos + up.x() * sin;
					float ay = -left.y() * cos + up.y() * sin;
					float az = -left.z() * cos + up.z() * sin;
					EchoLines.strip(consumer, pose, x + ax * from, y + ay * from, z + az * from, x + ax * to, y + ay * to, z + az * to, 0.012f * scale, argb(mix(rgb, 0xFFFFFF, 0.4f), fade), false);
				}
			}
		}
	}

	/** A thin ring facing the camera. */
	private static void ring(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, Vector3fc left, Vector3fc up, float radius, float half, int argb) {
		float lastX = -left.x() * radius;
		float lastY = -left.y() * radius;
		float lastZ = -left.z() * radius;
		for (int piece = 1; piece <= RING_PIECES; piece++) {
			float angle = piece * Mth.TWO_PI / RING_PIECES;
			float cos = Mth.cos(angle) * radius;
			float sin = Mth.sin(angle) * radius;
			float nx = -left.x() * cos + up.x() * sin;
			float ny = -left.y() * cos + up.y() * sin;
			float nz = -left.z() * cos + up.z() * sin;
			EchoLines.strip(consumer, pose, x + lastX, y + lastY, z + lastZ, x + nx, y + ny, z + nz, half, argb, false);
			lastX = nx;
			lastY = ny;
			lastZ = nz;
		}
	}

	/**
	 * @return whether a projectile is the player's own. Servers can send arrows without an owner, which SkyBlock does for the side arrows of the
	 *         Terminator: they spawn at the player's position in their first ticks and belong to the player. An ownerless arrow seen later or far
	 *         from the player does not.
	 */
	private static boolean isOwn(Projectile shot, Player player) {
		Entity owner = shot.getOwner();
		if (owner == player) {
			return true;
		}
		if (owner instanceof Player) {
			return false; // another player's
		}
		// No owner, or a non-player owner: it is the player's if it started at the player. It has flown a few ticks when first seen, and Terminator
		// arrows are fast (three blocks per tick), so its origin is reconstructed by tracing back along its path.
		if (shot.tickCount > 8) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 velocity = shot.getDeltaMovement();
		Vec3 start = shot.position().subtract(velocity.scale(Math.max(1, shot.tickCount)));
		if (start.distanceToSqr(eye) < 16.0 || shot.position().distanceToSqr(eye) < 16.0 || start.distanceToSqr(player.position()) < 16.0) {
			return true;
		}
		// it may have been seen late and be far away: it is the player's if it flies straight away from the player's eye, as if it started there
		Vec3 away = shot.position().subtract(eye);
		double speed = velocity.length();
		double distance = away.length();
		return speed > 0.3 && distance < 40.0 && away.dot(velocity) / (distance * speed) > 0.96;
	}

	/** Submits all trails of the frame in a single call. */
	public void collect(LevelRenderContext context) {
		boolean bursts = isEnabled() && anyBurst(System.nanoTime());
		if ((trails.isEmpty() && !bursts) || !isEnabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		Camera camera = client.gameRenderer.mainCamera();
		Vec3 cam = camera.position();
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float time = (client.level.getGameTime() + partial) / 20f;
		PoseStack pose = context.poseStack();
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		// the tails that keep their colors against a bright sky, the tear of a pearl and all else that is not light are blended normally; additive light on a bright sky turns white
		boolean plain = bursts;
		boolean echoes = false;
		for (Trail trail : trails.values()) {
			plain |= kindOf(trail) == TrailLook.Kind.COMET || trail.pearl && pearlRift.get();
			echoes |= kindOf(trail) == TrailLook.Kind.ECHO;
		}
		if (plain) {
			context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (last, consumer) -> {
				for (Trail trail : trails.values()) {
					draw(consumer, last, trail, cam, partial, time, left, up, false);
				}
				drawBursts(consumer, last, cam, left, up, false);
			});
		}
		// bright parts, such as the hot core of flames, are blended additively
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.lightning(), (last, consumer) -> {
			for (Trail trail : trails.values()) {
				draw(consumer, last, trail, cam, partial, time, left, up, true);
			}
			drawBursts(consumer, last, cam, left, up, true);
		});
		// Echos: lines of light as Radiance draws them, with their own renderer
		if (echoes && OutlineRenderTypes.available()) {
			float pixelAngle = EchoLines.pixelAngle(client.options.fov().get());
			context.submitNodeCollector().submitCustomGeometry(pose, EchoLines.type(), (last, consumer) -> {
				for (Trail trail : trails.values()) {
					if (kindOf(trail) == TrailLook.Kind.ECHO) {
						drawEcho(consumer, last, trail, cam, partial, up, pixelAngle);
					}
				}
			});
		}
	}


	private void draw(VertexConsumer consumer, PoseStack.Pose pose, Trail trail, Vec3 cam, float partial, float time, Vector3fc left, Vector3fc up, boolean additive) {
		boolean flying = trail.seen == tickCount && !trail.entity.isRemoved();
		int n = polyline(trail, flying, cam, partial);
		if (n < 2) {
			return;
		}
		// trails far from or behind the camera are skipped
		float distance = (float) Math.sqrt(px[0] * px[0] + py[0] * py[0] + pz[0] * pz[0]);
		if (distance > range.get() + 8f) {
			return;
		}
		TrailLook.Kind kind = kindOf(trail);
		sides(n, up);
		float scale = width.get();
		int rgb = rgbOf(trail.pearl);
		float strength = (flying ? 1f : Mth.clamp(trail.count / 6f, 0f, 1f)) * color.opacity();
		if (!additive && trail.pearl && pearlRift.get()) {
			// the dark tear along a pearl's path
			ribbon(consumer, pose, n, trail.id, time, 0.26f * scale, 0.04f * scale, RIFT_RGB, RIFT_RGB, RIFT_RGB, 0.75f * strength, 1.2f, 0.25f, 5);
		}
		if (!additive && kind != TrailLook.Kind.COMET) {
			return;
		}
		switch (kind) {
			case SILK -> {
				// a soft band that twists along the path, its colors flowing into each other, with a thin bright core
				wind(n, time, 0.1f * scale, 0f, trail.id);
				ribbon(consumer, pose, n, trail.id, time, 0.3f * scale, 0.05f * scale, rgb, mix(rgb, 0xFFFFFF, 0.25f), mix(rgb, 0x000000, 0.2f), 0.28f * strength, 1.1f, 0f, 1);
				unwind(n);
				wind(n, time, 0.1f * scale, Mth.PI, trail.id);
				ribbon(consumer, pose, n, trail.id, time, 0.22f * scale, 0.03f * scale, mix(rgb, 0xFFFFFF, 0.2f), rgb, mix(rgb, 0xFFFFFF, 0.3f), 0.22f * strength, 1.1f, 0f, 2);
				unwind(n);
				ribbon(consumer, pose, n, trail.id, time, 0.05f * scale, 0.012f * scale, mix(rgb, 0xFFFFFF, 0.75f), mix(rgb, 0xFFFFFF, 0.4f), rgb, 0.95f * strength, 0.9f, 0f, 3);
				if (flying) {
					billboard(consumer, pose, 0.3f * scale, left, up, rgb, 0.4f * strength, 0);
				}
			}
			case COMET -> {
				if (!additive) {
					// a tail that cools along the way, and keeps its color against a bright sky
					ribbon(consumer, pose, n, trail.id, time, 0.42f * scale, 0.02f * scale, mix(rgb, 0xFFFFFF, 0.4f), rgb, mix(rgb, 0x000000, 0.45f), 0.6f * strength, 1.6f, 0f, 3);
				} else {
					ribbon(consumer, pose, n, trail.id, time, 0.12f * scale, 0.01f * scale, 0xFFFFFF, mix(rgb, 0xFFFFFF, 0.5f), rgb, 0.7f * strength, 1.8f, 0f, 4);
					if (flying) {
						billboard(consumer, pose, 0.85f * scale, left, up, rgb, 0.55f * strength, 0);
						billboard(consumer, pose, 0.34f * scale, left, up, 0xFFFFFF, 0.95f * strength, 0);
					}
					// a few embers falling off
					for (int i = 1; i < n; i += 2) {
						float ember = noise(trail.id, i, 11);
						if (ember > 0.6f) {
							float drop = pu[i] * 0.5f + (float) Math.sin(time * 6f + i) * 0.03f;
							glintAt(consumer, pose, i, 0.09f * scale, left, up, mix(rgb, 0xFFFFFF, 0.3f), (1f - pu[i]) * strength, drop);
						}
					}
				}
			}
			case STARDUST -> {
				// twinkling dust and small four-point stars that drift down and wink out
				int frame = (int) Math.floor(time * 8f);
				for (int i = 0; i < n; i++) {
					float twinkle = noise(trail.id, i, frame);
					if (twinkle < 0.4f) {
						continue;
					}
					float fade = (1f - pu[i]) * twinkle * strength;
					float drop = pu[i] * 0.45f;
					int shade = mix(rgb, 0xFFFFFF, 0.4f * twinkle);
					glintAt(consumer, pose, i, 0.11f * scale, left, up, shade, fade, drop);
					if (noise(trail.id, i, 17) > 0.72f) {
						float size = 0.11f * scale * twinkle;
						float x = px[i];
						float y = py[i] - drop;
						float z = pz[i];
						EchoLines.strip(consumer, pose, x + left.x() * size, y + left.y() * size, z + left.z() * size, x - left.x() * size, y - left.y() * size, z - left.z() * size, 0.008f * scale, argb(shade, fade), false);
						EchoLines.strip(consumer, pose, x + up.x() * size, y + up.y() * size, z + up.z() * size, x - up.x() * size, y - up.y() * size, z - up.z() * size, 0.008f * scale, argb(shade, fade), false);
					}
				}
			}
			case LIGHTNING -> {
				// a crackling bolt along the path that forks anew all the time, with short side branches and a flash
				int fork = (int) Math.floor(time * 24f);
				System.arraycopy(px, 0, windX, 0, n);
				System.arraycopy(py, 0, windY, 0, n);
				System.arraycopy(pz, 0, windZ, 0, n);
				displace(n, trail.id + fork, time, 0.32f * scale, 0f, true);
				ribbon(consumer, pose, n, trail.id, time, 0.17f * scale, 0.05f * scale, rgb, rgb, rgb, 0.3f * strength, 0.8f, 0f, 6);
				ribbon(consumer, pose, n, trail.id, time, 0.035f * scale, 0.018f * scale, 0xFFFFFF, mix(rgb, 0xFFFFFF, 0.7f), mix(rgb, 0xFFFFFF, 0.4f), 0.98f * strength, 0.7f, 0f, 7);
				for (int i = 2; i < n; i += 3) {
					float chance = noise(trail.id + fork, i, 23);
					if (chance > 0.55f) {
						float reach = (chance - 0.45f) * 0.8f * scale * (chance > 0.78f ? 1f : -1f);
						EchoLines.strip(consumer, pose, px[i], py[i], pz[i], px[i] + sx[i] * reach, py[i] + sy[i] * reach - 0.08f, pz[i] + sz[i] * reach, 0.012f * scale, argb(mix(rgb, 0xFFFFFF, 0.6f), (1f - pu[i]) * 0.8f * strength), false);
					}
				}
				unwind(n);
				if (flying && noise(trail.id, fork, 31) > 0.45f) {
					billboard(consumer, pose, 0.75f * scale, left, up, mix(rgb, 0xFFFFFF, 0.4f), 0.5f * strength, 0);
				}
			}
			case HELIX -> {
				// two strands around the path: where one is near it is bright and thick, far it is dim, which makes it look round
				for (int strand = 0; strand < 2; strand++) {
					for (int i = 0; i + 1 < n; i++) {
						float a0 = pu[i] * HELIX_TURNS - time * HELIX_SPEED + strand * Mth.PI;
						float a1 = pu[i + 1] * HELIX_TURNS - time * HELIX_SPEED + strand * Mth.PI;
						float near = 0.5f + 0.5f * Mth.sin(a0);
						float r0 = HELIX_RADIUS * scale * Mth.cos(a0);
						float r1 = HELIX_RADIUS * scale * Mth.cos(a1);
						float light = (0.25f + 0.75f * near) * (1f - pu[i]) * strength;
						EchoLines.strip(consumer, pose, px[i] + sx[i] * r0, py[i] + sy[i] * r0, pz[i] + sz[i] * r0, px[i + 1] + sx[i + 1] * r1, py[i + 1] + sy[i + 1] * r1, pz[i + 1] + sz[i + 1] * r1,
							(0.008f + 0.016f * near) * scale, argb(mix(rgb, 0xFFFFFF, 0.35f * near), light), false);
					}
				}
				if (flying) {
					billboard(consumer, pose, 0.3f * scale, left, up, rgb, 0.45f * strength, 0);
				}
			}
			case ECHO -> {
				// drawn on its own, see drawEcho
			}
		}
	}

	/**
	 * Echo: the thin rim of what flies, a dense row of its outlines along the path, the newest brightest and the older ones fading to nothing and
	 * getting thinner, and a faint sheet of the color between them. Drawn as Radiance's Echo is, by {@link EchoLines}.
	 */
	private void drawEcho(VertexConsumer consumer, PoseStack.Pose pose, Trail trail, Vec3 cam, float partial, Vector3fc up, float pixelAngle) {
		boolean flying = trail.seen == tickCount && !trail.entity.isRemoved();
		int n = polyline(trail, flying, cam, partial);
		if (n < 2) {
			return;
		}
		float distance = (float) Math.sqrt(px[0] * px[0] + py[0] * py[0] + pz[0] * pz[0]);
		if (distance > range.get() + 8f) {
			return;
		}
		sides(n, up);
		float scale = width.get();
		int rgb = rgbOf(trail.pearl);
		float strength = (flying ? 1f : Mth.clamp(trail.count / 6f, 0f, 1f)) * color.opacity();
		float[][] shape = trail.pearl ? ECHO_PEARL : ECHO_ARROW;
		float half = ECHO_SHEET_WIDTH * 0.5f;
		for (int i = 0; i + 1 < n; i++) {
			EchoLines.sheet(consumer, pose, px[i] - sx[i] * half, py[i] - sy[i] * half, pz[i] - sz[i] * half, px[i] + sx[i] * half, py[i] + sy[i] * half, pz[i] + sz[i] * half,
				px[i + 1] + sx[i + 1] * half, py[i + 1] + sy[i + 1] * half, pz[i + 1] + sz[i + 1] * half, px[i + 1] - sx[i + 1] * half, py[i + 1] - sy[i + 1] * half,
				pz[i + 1] - sz[i + 1] * half, rgb, strength * EchoLines.ageLight(pu[i]), strength * EchoLines.ageLight(pu[i + 1]));
			for (int part = 0; part < ECHO_COPIES_PER_STEP; part++) {
				if (i == 0 && part == 0) {
					continue;
				}
				float t = part / (float) ECHO_COPIES_PER_STEP;
				float age = Mth.lerp(t, pu[i], pu[i + 1]);
				outline(consumer, pose, shape, i, t, pixelAngle, EchoLines.ageThickness(age) * scale, rgb, strength * EchoLines.ageLight(age));
			}
		}
		if (flying) {
			outline(consumer, pose, shape, 0, 0f, pixelAngle, scale, rgb, strength);
		}
	}

	/** The outline of a shape, as pairs of along and across, at a place between two points of the line, lying along it and facing the camera. */
	private void outline(VertexConsumer consumer, PoseStack.Pose pose, float[][] shape, int i, float t, float pixelAngle, float thickness, int rgb, float light) {
		float x = Mth.lerp(t, px[i], px[i + 1]);
		float y = Mth.lerp(t, py[i], py[i + 1]);
		float z = Mth.lerp(t, pz[i], pz[i + 1]);
		float dx = px[i] - px[i + 1];
		float dy = py[i] - py[i + 1];
		float dz = pz[i] - pz[i + 1];
		float length = Math.max(1e-4f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
		dx /= length;
		dy /= length;
		dz /= length;
		float[] last = shape[shape.length - 1];
		for (float[] point : shape) {
			float ax = x + dx * last[0] + sx[i] * last[1];
			float ay = y + dy * last[0] + sy[i] * last[1];
			float az = z + dz * last[0] + sz[i] * last[1];
			float bx = x + dx * point[0] + sx[i] * point[1];
			float by = y + dy * point[0] + sy[i] * point[1];
			float bz = z + dz * point[0] + sz[i] * point[1];
			EchoLines.line(consumer, pose, ax, ay, az, bx, by, bz, pixelAngle, thickness, rgb, light);
			last = point;
		}
	}

	/** A soft light at a point of the line. */
	private void glintAt(VertexConsumer consumer, PoseStack.Pose pose, int point, float size, Vector3fc left, Vector3fc up, int rgb, float alpha, float drop) {
		float hx = px[0];
		float hy = py[0];
		float hz = pz[0];
		px[0] = px[point];
		py[0] = py[point] - drop;
		pz[0] = pz[point];
		billboard(consumer, pose, size, left, up, rgb, alpha, 0);
		px[0] = hx;
		py[0] = hy;
		pz[0] = hz;
	}

	/** @return how a trail is drawn */
	private TrailLook.Kind kindOf(Trail trail) {
		return (trail.pearl ? pearlStyle : arrowStyle).get().or(style.get());
	}

	/** @return the color of a pearl's or an arrow's trail: its own if it has one, or the page's */
	private int rgbOf(boolean pearl) {
		return switch ((pearl ? pearlColors : arrowColors).get()) {
			case ACCENT -> MenuFeature.accentRgb();
			case CUSTOM -> (pearl ? pearlColor : arrowColor).get() & 0xFFFFFF;
			case SAME -> colors.get() == TrailLook.TrailColors.ACCENT ? MenuFeature.accentRgb() : color.get() & 0xFFFFFF;
		};
	}

	/**
	 * Builds the trail's polyline from the head backwards, subdivided finely enough to bend smoothly. Points are relative to the camera, and each
	 * stores its position along the trail, from 0 at the head to 1 at the end.
	 *
	 * @return the number of points
	 */
	private int polyline(Trail trail, boolean flying, Vec3 cam, float partial) {
		float total = length.get();
		// the player's trail curves with their movement and is cut into small segments, smoothed below; arrows fly almost straight
		float step = 0.55f;
		int n = 0;
		double hx;
		double hy;
		double hz;
		int from;
		if (flying) {
			Entity arrow = trail.entity;
			hx = Mth.lerp(partial, arrow.xo, arrow.getX());
			hy = Mth.lerp(partial, arrow.yo, arrow.getY());
			hz = Mth.lerp(partial, arrow.zo, arrow.getZ());
			from = trail.count - 2;
		} else {
			if (trail.count < 1) {
				return 0;
			}
			hx = trail.history[(trail.count - 1) * 3];
			hy = trail.history[(trail.count - 1) * 3 + 1];
			hz = trail.history[(trail.count - 1) * 3 + 2];
			from = trail.count - 2;
		}
		px[0] = (float) (hx - cam.x);
		py[0] = (float) (hy - cam.y);
		pz[0] = (float) (hz - cam.z);
		pu[0] = 0f;
		n = 1;
		float along = 0f;
		double ax = hx;
		double ay = hy;
		double az = hz;
		for (int i = from; i >= 0 && n < MAX_POINTS; i--) {
			double bx = trail.history[i * 3];
			double by = trail.history[i * 3 + 1];
			double bz = trail.history[i * 3 + 2];
			double dx = bx - ax;
			double dy = by - ay;
			double dz = bz - az;
			float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d < 1e-4f) {
				continue;
			}
			int pieces = Math.max(1, (int) Math.ceil(d / step));
			for (int k = 1; k <= pieces && n < MAX_POINTS; k++) {
				float piece = d / pieces;
				if (along + piece >= total) {
					// the trail ends within this segment
					float part = (total - along) / piece;
					float f = (k - 1 + part) / pieces;
					px[n] = (float) (ax + dx * f - cam.x);
					py[n] = (float) (ay + dy * f - cam.y);
					pz[n] = (float) (az + dz * f - cam.z);
					pu[n] = 1f;
					return n + 1;
				}
				along += piece;
				float f = k / (float) pieces;
				px[n] = (float) (ax + dx * f - cam.x);
				py[n] = (float) (ay + dy * f - cam.y);
				pz[n] = (float) (az + dz * f - cam.z);
				pu[n] = along / total;
				n++;
			}
			ax = bx;
			ay = by;
			az = bz;
		}
		// the history is shorter than the trail: it ends where the history does
		if (n >= 2) {
			float last = pu[n - 1];
			if (last > 0f) {
				for (int i = 0; i < n; i++) {
					pu[i] /= last;
				}
			}
		}
		return n;
	}

	/** Computes the across-trail direction at every point as seen from the camera, which gives the ribbon its width. */
	private void sides(int n, Vector3fc up) {
		for (int i = 0; i < n; i++) {
			int a = Math.max(0, i - 1);
			int b = Math.min(n - 1, i + 1);
			float tx = px[b] - px[a];
			float ty = py[b] - py[a];
			float tz = pz[b] - pz[a];
			// tangent x view
			float vx = px[i];
			float vy = py[i];
			float vz = pz[i];
			float cx = ty * vz - tz * vy;
			float cy = tz * vx - tx * vz;
			float cz = tx * vy - ty * vx;
			float length = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
			if (length < 1e-5f) {
				// the arrow flies straight towards or away from the camera: use the screen horizontal as the width direction
				cx = up.x();
				cy = up.y();
				cz = up.z();
				length = 1f;
			}
			sx[i] = cx / length;
			sy[i] = cy / length;
			sz[i] = cz / length;
		}
	}

	private static float noise(int a, int b, int c) {
		int h = a * 374761393 + b * 668265263 + c * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535f;
	}

	/**
	 * Offsets the points sideways by a seeded random amount, making the line jagged (lightning) or wobbly (fire). The head stays fixed.
	 */
	private void displace(int n, int seed, float time, float amount, float wobbleSpeed, boolean sharp) {
		for (int i = 1; i < n; i++) {
			float grow = 0.35f + 0.65f * pu[i];
			float r = sharp ? noise(seed, i, 7) * 2f - 1f : (float) Math.sin(time * wobbleSpeed + i * 1.7f + seed) ;
			float push = amount * grow * r;
			px[i] += sx[i] * push;
			py[i] += sy[i] * push;
			pz[i] += sz[i] * push;
		}
	}

	private float[] windX = new float[MAX_POINTS];
	private float[] windY = new float[MAX_POINTS];
	private float[] windZ = new float[MAX_POINTS];

	/** Offsets the points along a wave across the trail, remembering their original positions. */
	private void wind(int n, float time, float amount, float phase, int seed) {
		for (int i = 0; i < n; i++) {
			windX[i] = px[i];
			windY[i] = py[i];
			windZ[i] = pz[i];
			float wave = (float) Math.sin(pu[i] * 14f - time * 22f + phase + seed);
			float push = amount * wave * (0.25f + 0.75f * Math.min(1f, pu[i] * 3f));
			px[i] += sx[i] * push;
			py[i] += sy[i] * push;
			pz[i] += sz[i] * push;
		}
	}

	private void unwind(int n) {
		System.arraycopy(windX, 0, px, 0, n);
		System.arraycopy(windY, 0, py, 0, n);
		System.arraycopy(windZ, 0, pz, 0, n);
	}

	private static int mix(int a, int b, float amount) {
		int r = Math.round(Mth.lerp(amount, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(amount, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = Math.round(Mth.lerp(amount, a & 0xFF, b & 0xFF));
		return (r << 16) | (g << 8) | bl;
	}

	private static int argb(int rgb, float alpha) {
		return (Math.round(Mth.clamp(alpha, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
	}

	/**
	 * Emits a ribbon along the line: wide at the head, narrow at the end, with three colors from head to end, fading out. It faces the camera and is
	 * double-sided.
	 *
	 * @param flicker how much the width varies between points and over time, as a fraction
	 */
	private void ribbon(VertexConsumer consumer, PoseStack.Pose pose, int n, int id, float time, float headWidth, float tailWidth,
						int c0, int c1, int c2, float alpha, float falloff, float flicker, int salt) {
		int frame = (int) Math.floor(time * 20f);
		for (int i = 0; i < n - 1; i++) {
			float ua = pu[i];
			float ub = pu[i + 1];
			float wa = Mth.lerp((float) Math.pow(ua, 0.8), headWidth, tailWidth) * 0.5f * (1f + flicker * (noise(id + salt, i, frame) * 2f - 1f));
			float wb = Mth.lerp((float) Math.pow(ub, 0.8), headWidth, tailWidth) * 0.5f * (1f + flicker * (noise(id + salt, i + 1, frame) * 2f - 1f));
			int ca = argb(gradient(c0, c1, c2, ua), alpha * (float) Math.pow(1f - ua, falloff));
			int cb = argb(gradient(c0, c1, c2, ub), alpha * (float) Math.pow(1f - ub, falloff));
			float ax = px[i] - sx[i] * wa, ay = py[i] - sy[i] * wa, az = pz[i] - sz[i] * wa;
			float bx = px[i] + sx[i] * wa, by = py[i] + sy[i] * wa, bz = pz[i] + sz[i] * wa;
			float cx = px[i + 1] + sx[i + 1] * wb, cy = py[i + 1] + sy[i + 1] * wb, cz = pz[i + 1] + sz[i + 1] * wb;
			float dx = px[i + 1] - sx[i + 1] * wb, dy = py[i + 1] - sy[i + 1] * wb, dz = pz[i + 1] - sz[i + 1] * wb;
			consumer.addVertex(pose, ax, ay, az).setColor(ca);
			consumer.addVertex(pose, bx, by, bz).setColor(ca);
			consumer.addVertex(pose, cx, cy, cz).setColor(cb);
			consumer.addVertex(pose, dx, dy, dz).setColor(cb);
			consumer.addVertex(pose, dx, dy, dz).setColor(cb);
			consumer.addVertex(pose, cx, cy, cz).setColor(cb);
			consumer.addVertex(pose, bx, by, bz).setColor(ca);
			consumer.addVertex(pose, ax, ay, az).setColor(ca);
		}
	}

	private static int gradient(int c0, int c1, int c2, float u) {
		return u < 0.4f ? mix(c0, c1, u / 0.4f) : mix(c1, c2, (u - 0.4f) / 0.6f);
	}

	/** Emits a soft camera-facing square of light at the trail's head. */
	private void billboard(VertexConsumer consumer, PoseStack.Pose pose, float size, Vector3fc left, Vector3fc up, int rgb, float alpha, int salt) {
		float half = size * 0.5f;
		int center = argb(rgb, alpha);
		int edge = argb(rgb, 0f);
		// a diamond with a bright center and transparent corners: soft lighting with few vertices
		float hx = px[0];
		float hy = py[0];
		float hz = pz[0];
		float lx = -left.x() * half, ly = -left.y() * half, lz = -left.z() * half;
		float ux = up.x() * half, uy = up.y() * half, uz = up.z() * half;
		consumer.addVertex(pose, hx + lx, hy + ly, hz + lz).setColor(edge);
		consumer.addVertex(pose, hx + ux, hy + uy, hz + uz).setColor(edge);
		consumer.addVertex(pose, hx, hy, hz).setColor(center);
		consumer.addVertex(pose, hx, hy, hz).setColor(center);
		consumer.addVertex(pose, hx + ux, hy + uy, hz + uz).setColor(edge);
		consumer.addVertex(pose, hx - lx, hy - ly, hz - lz).setColor(edge);
		consumer.addVertex(pose, hx, hy, hz).setColor(center);
		consumer.addVertex(pose, hx, hy, hz).setColor(center);
		consumer.addVertex(pose, hx - lx, hy - ly, hz - lz).setColor(edge);
		consumer.addVertex(pose, hx - ux, hy - uy, hz - uz).setColor(edge);
		consumer.addVertex(pose, hx, hy, hz).setColor(center);
		consumer.addVertex(pose, hx, hy, hz).setColor(center);
		consumer.addVertex(pose, hx - ux, hy - uy, hz - uz).setColor(edge);
		consumer.addVertex(pose, hx + lx, hy + ly, hz + lz).setColor(edge);
		consumer.addVertex(pose, hx, hy, hz).setColor(center);
		consumer.addVertex(pose, hx, hy, hz).setColor(center);
	}
}
