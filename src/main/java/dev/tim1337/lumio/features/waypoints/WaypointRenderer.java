package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.WorldLabel;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointStyle;
import dev.tim1337.lumio.render.EchoLines;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Toasts;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3fc;

import java.util.List;

/**
 * Draws the waypoints in the world: once a tick it picks the points that show (of the groups that are on and where the player is, by each group's
 * Show), moves routes on to their next point, and checks which labels a wall hides; every frame it draws their beams, glows and lines with the
 * world, and their labels with the HUD. Groups that are off or elsewhere cost nothing.
 */
final class WaypointRenderer {
	/** The most points drawn at once; the nearest are kept when more would show. */
	private static final int MOST_SHOWN = 256;
	/** Where the player is measured from, above the feet, in blocks. */
	private static final double PLAYER_MIDDLE_BLOCKS = 0.5;
	/** How long the toast of the next and previous keys shows, in seconds. */
	private static final float KEY_TOAST_SECONDS = 1.5f;
	/** A mined block this close to the next point counts as the point's. */
	private static final double MINED_BLOCKS = 1.5;
	/** How many labels are checked against walls per tick: a few, in turn, so many points cost little. */
	private static final int SIGHT_CHECKS_PER_TICK = 6;
	/** Labels dim from this distance on (their size follows WaypointLook.labelSize). */
	private static final double DIM_FROM_BLOCKS = 64.0;
	private static final double DIM_TO_BLOCKS = 256.0;
	private static final float LEAST_FAR_OPACITY = 0.45f;
	/** How far above the block's top the label stands, in blocks, so it never lies over the block. */
	private static final double LABEL_ABOVE_BLOCKS = 0.45;
	/** Later points of a route are this much softer than the next one. */
	private static final float LATER_OPACITY = 0.6f;
	private static final float LINE_OPACITY = 0.75f;
	/** How far ahead of the player the line to the next point starts, in blocks. */
	private static final float LINE_START_BLOCKS = 1.5f;
	/** The game's see-through text quads with a white texture: drawn over everything, the same on every version and backend. */
	private static final RenderType SEE_THROUGH = RenderTypes.textSeeThrough(Lumio.id("textures/white.png"));
	/** The box on a point's block: its fill, the next point's fill pulsing between two opacities, and through walls a softer fill. */
	private static final float FILL_OPACITY = 0.28f;
	private static final float PULSE_LOW = 0.28f;
	private static final float PULSE_HIGH = 0.40f;
	private static final long PULSE_MILLIS = 1600;
	private static final float THROUGH_WALLS_FILL = 0.5f;
	/** The box's outline: its width on the screen, how much brighter than the point's color, and its opacity, low for dimmed points. */
	private static final float OUTLINE_PIXELS = 2f;
	private static final float OUTLINE_BRIGHTER = 0.35f;
	private static final float OUTLINE_OPACITY = 0.9f;
	/** A soft glow around the outline: wider and faint. */
	private static final float GLOW_PIXELS = 7f;
	private static final float GLOW_OPACITY = 0.14f;
	/** The next point's ring on the ground: it grows from the near radius to the far one and fades, then starts again. */
	private static final long RING_MILLIS = 2400;
	private static final float RING_FROM_BLOCKS = 0.8f;
	private static final float RING_TO_BLOCKS = 2.4f;
	private static final float RING_OPACITY = 0.6f;

	/** One point that shows: its group, its place in it, how it is drawn, and what was worked out about it. */
	private static final class Shown {
		WaypointGroup group;
		Waypoint point;
		int index;
		WaypointLook.State state;
		double distance;
		boolean hidden;
		long shownMeters = -1;
		String distanceText = "";
		/** Where the next point's ring lies above the block's bottom: on top of a solid block, on the floor of an empty one. */
		float ringLift;
		/** The point after this one the line to next goes to, or -1. */
		int lineTo = -1;
		final PointShape shape = new PointShape();
	}

	private final WaypointsFeature feature;
	private final Shown[] shown = new Shown[MOST_SHOWN];
	private int shownCount;
	private int sightTurn;
	private final float[] place = new float[WorldLabel.PLACE_VALUES];
	/** The nearest points of a group, while they are chosen: indices and distances. */
	private final int[] nearest = new int[WaypointGroup.MOST_SHOWN];
	private final double[] nearestDistance = new double[WaypointGroup.MOST_SHOWN];
	private final BlockPos.MutableBlockPos blockAt = new BlockPos.MutableBlockPos();
	/** Worked out once a frame for every point drawn: the size of a pixel at one block, the next point's fill and its ring. */
	private float pixelAngle;
	private float nextFill;
	private float ringRadius;
	private float ringOpacity;
	/** The look set for all waypoints, taken once a tick. */
	private WaypointStyle style = WaypointStyle.DEFAULT;

	WaypointRenderer(WaypointsFeature feature) {
		this.feature = feature;
		for (int i = 0; i < MOST_SHOWN; i++) {
			shown[i] = new Shown();
		}
	}

	/** Picks what shows, moves routes on and checks sight; called every tick. */
	void tick(Minecraft client) {
		shownCount = 0;
		feature.look().refresh();
		style = feature.look().style();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return;
		}
		double px = player.getX();
		double py = player.getY() + PLAYER_MIDDLE_BLOCKS;
		double pz = player.getZ();
		List<WaypointGroup> groups = feature.store().groups();
		int keyStep = presses(feature.placing().nextKey()) - presses(feature.placing().previousKey());
		WaypointGroup keyGroup = keyStep == 0 ? null : keyTarget(groups, px, py, pz);
		if (presses(feature.placing().restartKey()) > 0) {
			// the route here starts at its first point again
			WaypointGroup restarted = keyTarget(groups, px, py, pz);
			if (restarted != null) {
				restarted.restart();
				feature.store().changed(restarted);
				Toasts.brief(restarted.icon(), restarted.name(), "Restarted at 1/" + restarted.points().size(), KEY_TOAST_SECONDS);
			}
		}
		for (int g = 0; g < groups.size(); g++) {
			WaypointGroup group = groups.get(g);
			if (!group.enabled() || group.points().isEmpty() || !WaypointAreas.here(group)) {
				continue;
			}
			switch (group.show()) {
				case ALL -> {
					int count = group.points().size();
					for (int i = 0; i < count; i++) {
						if (group.drawn(i)) {
							Shown entry = add(group, i, WaypointLook.State.SHOWN, px, py, pz);
							if (entry != null && i + 1 < count && group.drawn(i + 1)) {
								entry.lineTo = i + 1;
							}
						}
					}
				}
				case NEAREST -> pickNearest(group, px, py, pz);
				case NEXT -> route(client, group, group == keyGroup ? Integer.signum(keyStep) : 0, px, py, pz);
			}
		}
		// the temporary waypoints from chat, through their own group (never stored): softer near the end of their time
		WaypointGroup temporary = feature.temporaryGroup();
		for (int i = 0; i < temporary.points().size(); i++) {
			add(temporary, i, feature.fading(i) ? WaypointLook.State.LATER : WaypointLook.State.SHOWN, px, py, pz);
		}
		for (int i = 0; i < shownCount; i++) {
			Shown entry = shown[i];
			blockAt.set(entry.point.x(), entry.point.y(), entry.point.z());
			if (entry.group.has(WaypointGroup.Look.BLOCK) && client.level.isLoaded(blockAt)) {
				entry.shape.update(client.level, blockAt, entry.point);
			} else {
				entry.shape.reset();
			}
		}
		checkSight(client, player);
	}

	/** @return how often a key was pressed since the last tick */
	private static int presses(KeyMapping key) {
		int count = 0;
		while (key != null && key.consumeClick()) {
			count++;
		}
		return count;
	}

	private void route(Minecraft client, WaypointGroup group, int keyStep, double px, double py, double pz) {
		List<Waypoint> points = group.points();
		RouteProgress progress = group.progress();
		int next = group.next();
		if (keyStep != 0) {
			// chosen by hand: the route waits there until the player arrives anew
			moveTo(group, keyStep > 0 ? RouteProgress.after(next, points.size()) : RouteProgress.before(next, points.size()));
			progress.hold();
			next = group.next();
			Toasts.brief(group.icon(), group.name(), (next + 1) + "/" + points.size() + "  ·  " + labelName(points.get(next), next), KEY_TOAST_SECONDS);
		}
		int stepped = progress.step(points, next, px, py, pz, group.reach(), group.advance() == WaypointGroup.Advance.REACHED);
		if (group.advance() == WaypointGroup.Advance.MINED && !progress.held()) {
			Waypoint point = points.get(next);
			blockAt.set(point.x(), point.y(), point.z());
			Object state = client.level.isLoaded(blockAt) ? client.level.getBlockState(blockAt) : null;
			if (progress.mined(next, state, RouteProgress.nearForMined(point, px, py, pz))) {
				stepped = RouteProgress.after(next, points.size());
			}
		}
		if (stepped != next) {
			moveTo(group, stepped);
		}
		// only the next few points show, the first at full strength; the line to next joins them
		int size = points.size();
		Shown before = null;
		int taken = 0;
		for (int step = 0; step < size && taken < group.nextCount(); step++) {
			int i = (group.next() + step) % size;
			if (!group.drawn(i)) {
				continue;
			}
			Shown entry = add(group, i, taken == 0 ? WaypointLook.State.NEXT : WaypointLook.State.LATER, px, py, pz);
			taken++;
			if (entry == null) {
				break;
			}
			if (before != null) {
				before.lineTo = i;
			}
			before = entry;
			if (taken == 1) {
				Waypoint point = points.get(i);
				blockAt.set(point.x(), point.y(), point.z());
				entry.ringLift = client.level.isLoaded(blockAt) && !client.level.getBlockState(blockAt).isAir() ? 1f : 0f;
			}
		}
	}

	/** A route goes on to another point, with a soft tick. */
	private void moveTo(WaypointGroup group, int index) {
		group.next(index);
		feature.store().changed(group);
		UiSounds.tick(0.8f);
	}

	/** @return the route group a key press moves: of those that show here, the one whose next point is nearest, or null */
	private static WaypointGroup keyTarget(List<WaypointGroup> groups, double px, double py, double pz) {
		WaypointGroup nearest = null;
		double nearestSq = Double.MAX_VALUE;
		for (int g = 0; g < groups.size(); g++) {
			WaypointGroup group = groups.get(g);
			if (group.enabled() && group.show() == WaypointGroup.Show.NEXT && !group.points().isEmpty() && WaypointAreas.here(group)) {
				double distanceSq = group.points().get(group.next()).distanceSq(px, py, pz);
				if (distanceSq < nearestSq) {
					nearestSq = distanceSq;
					nearest = group;
				}
			}
		}
		return nearest;
	}

	/** A block the player broke: a route that goes on when its point is mined goes on. */
	void blockBroken(BlockPos pos) {
		for (WaypointGroup group : feature.store().groups()) {
			if (group.enabled() && group.show() == WaypointGroup.Show.NEXT && group.advance() == WaypointGroup.Advance.MINED && !group.points().isEmpty()
				&& WaypointAreas.here(group)) {
				Waypoint next = group.points().get(group.next());
				if (next.distanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MINED_BLOCKS * MINED_BLOCKS) {
					moveTo(group, RouteProgress.after(group.next(), group.points().size()));
				}
			}
		}
	}

	private void pickNearest(WaypointGroup group, double px, double py, double pz) {
		int found = 0;
		for (int i = 0; i < group.points().size(); i++) {
			if (!group.drawn(i)) {
				continue;
			}
			double distance = group.points().get(i).distanceSq(px, py, pz);
			// kept sorted, nearest first
			int at = Math.min(found, group.nextCount());
			while (at > 0 && nearestDistance[at - 1] > distance) {
				if (at < group.nextCount()) {
					nearest[at] = nearest[at - 1];
					nearestDistance[at] = nearestDistance[at - 1];
				}
				at--;
			}
			if (at < group.nextCount()) {
				nearest[at] = i;
				nearestDistance[at] = distance;
				found = Math.min(group.nextCount(), found + 1);
			}
		}
		for (int i = 0; i < found; i++) {
			add(group, nearest[i], WaypointLook.State.SHOWN, px, py, pz);
		}
	}

	/** @return the entry the point got, or null when the nearer ones fill the list */
	private Shown add(WaypointGroup group, int index, WaypointLook.State state, double px, double py, double pz) {
		Waypoint point = group.points().get(index);
		double distance = Math.sqrt(point.distanceSq(px, py, pz));
		if (!style.inReach(distance)) {
			return null;
		}
		Shown entry;
		if (shownCount < MOST_SHOWN) {
			entry = shown[shownCount++];
		} else {
			// full: the farthest makes room for a nearer one
			int farthest = 0;
			for (int i = 1; i < MOST_SHOWN; i++) {
				if (shown[i].distance > shown[farthest].distance) {
					farthest = i;
				}
			}
			if (shown[farthest].distance <= distance) {
				return null;
			}
			entry = shown[farthest];
		}
		if (entry.point != point) {
			entry.hidden = false;
			entry.shownMeters = -1;
		}
		entry.group = group;
		entry.point = point;
		entry.index = index;
		entry.state = state;
		entry.distance = distance;
		entry.ringLift = 0f;
		entry.lineTo = -1;
		return entry;
	}

	/** Checks a few labels a tick against walls, in turn; a label of a group that shows through walls is never hidden. */
	private void checkSight(Minecraft client, LocalPlayer player) {
		if (shownCount == 0) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		for (int n = 0; n < Math.min(SIGHT_CHECKS_PER_TICK, shownCount); n++) {
			sightTurn = (sightTurn + 1) % shownCount;
			Shown entry = shown[sightTurn];
			if (entry.group.has(WaypointGroup.Look.THROUGH_WALLS)) {
				entry.hidden = false;
				continue;
			}
			Vec3 target = new Vec3(entry.point.x() + 0.5, entry.point.y() + 1.0 + LABEL_ABOVE_BLOCKS / 2, entry.point.z() + 0.5);
			HitResult hit = client.level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
			// the point's own block does not hide it
			entry.hidden = hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(target) > 2.25;
		}
	}

	// ------------------------------------------------------------------------------------------------------ drawing

	/** Beams, glows and route lines, with the world: through walls in the game's see-through quads, otherwise in its translucent ones. */
	void collect(LevelRenderContext context) {
		if (shownCount == 0) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		Camera camera = client.gameRenderer.mainCamera();
		Vec3 cam = camera.position();
		PoseStack pose = context.poseStack();
		boolean anyThrough = false;
		boolean anyNormal = false;
		for (int i = 0; i < shownCount; i++) {
			boolean through = shown[i].group.has(WaypointGroup.Look.THROUGH_WALLS);
			anyThrough |= through;
			anyNormal |= !through;
		}
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 feet = player.getPosition(partial);
		frame(client);
		if (anyNormal) {
			context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (last, consumer) -> drawAll(last, consumer, cam, feet, left, up,
				false));
		}
		if (anyThrough) {
			context.submitNodeCollector().submitCustomGeometry(pose, SEE_THROUGH, (last, consumer) -> drawAll(last, consumer, cam,
				feet, left, up, true));
		}
	}

	private void drawAll(PoseStack.Pose pose, VertexConsumer consumer, Vec3 cam, Vec3 feet, Vector3fc left, Vector3fc up,
		boolean through) {
		for (int i = 0; i < shownCount; i++) {
			Shown entry = shown[i];
			WaypointGroup group = entry.group;
			if (group.has(WaypointGroup.Look.THROUGH_WALLS) != through) {
				continue;
			}
			Waypoint point = entry.point;
			int rgb = group.rgbOf(point);
			float x = (float) (point.x() + 0.5 - cam.x);
			float y = (float) (point.y() - cam.y);
			float z = (float) (point.z() + 0.5 - cam.z);
			float opacity = entry.state == WaypointLook.State.NEXT || entry.state == WaypointLook.State.SHOWN ? 1f : LATER_OPACITY;
			if (style.beamOn(group.has(WaypointGroup.Look.BEAM))) {
				WaypointLook.beam(pose, consumer, x, y, z, rgb, opacity * style.beamStrength(), style.beamHeight());
			}
			if (group.has(WaypointGroup.Look.BLOCK) && style.boxed()) {
				drawBlock(pose, consumer, entry, x - 0.5f, y, z - 0.5f, rgb, opacity, through);
			}
			if (entry.state == WaypointLook.State.NEXT) {
				// the way from the player's feet to the next point
				float fromX = (float) (feet.x - cam.x);
				float fromY = (float) (feet.y + 0.1 - cam.y);
				float fromZ = (float) (feet.z - cam.z);
				float toY = y + 1f;
				float length = (float) Math.sqrt((x - fromX) * (x - fromX) + (toY - fromY) * (toY - fromY) + (z - fromZ) * (z - fromZ));
				if (length > LINE_START_BLOCKS) {
					// it starts a little ahead, where its dots are not large at the camera
					float t = LINE_START_BLOCKS / length;
					WaypointLook.dottedLine(pose, consumer, fromX + (x - fromX) * t, fromY + (toY - fromY) * t, fromZ + (z - fromZ) * t, x, toY, z, left, up, rgb,
						LINE_OPACITY);
				}
			}
			if (entry.lineTo >= 0 && group.has(WaypointGroup.Look.LINE) && entry.lineTo < group.points().size()) {
				Waypoint after = group.points().get(entry.lineTo);
				WaypointLook.dottedLine(pose, consumer, x, y + 1f, z, (float) (after.x() + 0.5 - cam.x), (float) (after.y() + 1 - cam.y),
					(float) (after.z() + 0.5 - cam.z), left, up, rgb, LINE_OPACITY * opacity * 0.6f);
			}
		}
	}

	/** Works out what moves with time: the pulse of the next point's fill and its growing ring, and how large a pixel is now. */
	private void frame(Minecraft client) {
		pixelAngle = EchoLines.pixelAngle(client.options.fov().get());
		long now = Util.getMillis();
		float pulse = (float) (0.5 - 0.5 * Math.cos(Math.PI * 2 * (now % PULSE_MILLIS) / PULSE_MILLIS));
		nextFill = PULSE_LOW + (PULSE_HIGH - PULSE_LOW) * pulse;
		float grown = (now % RING_MILLIS) / (float) RING_MILLIS;
		ringRadius = RING_FROM_BLOCKS + (RING_TO_BLOCKS - RING_FROM_BLOCKS) * grown;
		// in quickly, then out as it grows
		ringOpacity = RING_OPACITY * Math.min(1f, grown * 8f) * (1f - grown);
	}

	/**
	 * The marked block, in the shape the game outlines it with: a filled box in the point's color, a crisp brighter outline and a soft glow around
	 * it; the next point's box pulses and a ring grows on the ground around it, later points are softer. Through walls the box is softer.
	 *
	 * @param x the block's low corner, relative to the camera, and y and z likewise
	 */
	private void drawBlock(PoseStack.Pose pose, VertexConsumer consumer, Shown entry, float x, float y, float z, int rgb, float opacity,
		boolean through) {
		int bright = Draw.mix(0xFF000000 | rgb, 0xFFFFFFFF, OUTLINE_BRIGHTER) & 0xFFFFFF;
		boolean next = entry.state == WaypointLook.State.NEXT;
		float fill = style.fillOpacity((next ? nextFill : FILL_OPACITY) * opacity * (through ? THROUGH_WALLS_FILL : 1f));
		float outlinePixels = style.lineWidth();
		float glowPixels = GLOW_PIXELS * outlinePixels / OUTLINE_PIXELS;
		PointShape shape = entry.shape;
		if (shape.full) {
			WaypointLook.boxAt(pose, consumer, x, y, z, x + 1f, y + 1f, z + 1f, rgb, fill);
			WaypointLook.outlineAt(pose, consumer, x, y, z, x + 1f, y + 1f, z + 1f, rgb, GLOW_OPACITY * opacity, pixelAngle, glowPixels);
			WaypointLook.outlineAt(pose, consumer, x, y, z, x + 1f, y + 1f, z + 1f, bright, OUTLINE_OPACITY * opacity, pixelAngle, outlinePixels);
		} else {
			float[] boxes = shape.boxes;
			for (int b = 0; b < shape.boxCount; b++) {
				int at = b * 6;
				WaypointLook.boxAt(pose, consumer, x + boxes[at], y + boxes[at + 1], z + boxes[at + 2], x + boxes[at + 3], y + boxes[at + 4],
					z + boxes[at + 5], rgb, fill);
			}
			float[] edges = shape.edges;
			for (int pass = 0; pass < 2; pass++) {
				int color = pass == 0 ? rgb : bright;
				float lineOpacity = (pass == 0 ? GLOW_OPACITY : OUTLINE_OPACITY) * opacity;
				float width = pass == 0 ? glowPixels : outlinePixels;
				for (int e = 0; e < shape.edgeCount; e++) {
					int at = e * 6;
					WaypointLook.line(pose, consumer, x + edges[at], y + edges[at + 1], z + edges[at + 2], x + edges[at + 3], y + edges[at + 4],
						z + edges[at + 5], color, lineOpacity, pixelAngle, width);
				}
			}
		}
		if (next && ringOpacity > 0.01f) {
			WaypointLook.groundRing(pose, consumer, x + 0.5f, y + entry.ringLift, z + 0.5f, ringRadius, rgb, ringOpacity);
		}
	}

	/** The labels, with the HUD: at their places on the screen, smaller and dimmer far away, gone where a wall hides them. */
	void drawLabels(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		if (shownCount == 0) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		Ui ui = null;
		// the next points of routes last, so they lie over the others
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < shownCount; i++) {
				if (shown[i].state == WaypointLook.State.NEXT == (pass == 0)) {
					continue;
				}
				ui = drawLabel(graphics, client, ui, shown[i]);
			}
		}
	}

	private Ui drawLabel(GuiGraphicsExtractor graphics, Minecraft client, Ui ui, Shown entry) {
		WaypointGroup group = entry.group;
		WaypointGroup.Label mode = group.label();
		Waypoint point = entry.point;
		if (entry.hidden || mode == WaypointGroup.Label.OFF || point.noLabel()) {
			return ui;
		}
		// standing on a place above the block's top, so the label never lies over the block
		double ahead = WorldLabel.place(client, point.x() + 0.5, point.y() + 1.0 + LABEL_ABOVE_BLOCKS, point.z() + 0.5, place);
		if (ahead < WorldLabel.NEAR) {
			return ui;
		}
		float opacity = labelOpacity(entry);
		if (opacity <= 0.01f) {
			return ui;
		}
		if (ui == null) {
			ui = new Ui(graphics, client.font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), 0f, false);
		}
		String name = style.showName() ? labelName(point, entry.index) : "";
		String distance = mode == WaypointGroup.Label.NAME_DISTANCE && style.showDistance() ? distanceText(entry) : "";
		boolean iconLabel = style.iconLabel();
		if (name.isEmpty() && distance.isEmpty() && !iconLabel) {
			return ui;
		}
		place[WorldLabel.SIZE] = style.labelScale(entry.distance) * group.labelSize();
		place[WorldLabel.FADE] = 1f;
		Ui canvas = ui;
		if (iconLabel) {
			float iconSize = style.iconSize();
			boolean shadow = style.textShadow();
			WorldLabel.draw(graphics, place, WaypointLook.iconLabelWidth(ui, name, distance, iconSize), WaypointLook.iconLabelHeight(iconSize), opacity,
				() -> WaypointLook.iconLabel(canvas, group.icon(), name, distance, group.rgbOf(point), entry.state, shadow, iconSize));
		} else if (group.labelBackground() || style.textBackground()) {
			WorldLabel.draw(graphics, place, WaypointLook.labelWidth(ui, name, distance), WaypointLook.labelHeight(), opacity,
				() -> WaypointLook.label(canvas, entry.index + 1, name, distance, group.rgbOf(point), entry.state));
		} else {
			WorldLabel.draw(graphics, place, WaypointLook.plainLabelWidth(ui, name, distance), WaypointLook.plainLabelHeight(), opacity,
				() -> WaypointLook.plainLabel(canvas, name, distance, entry.state, style.textShadow()));
		}
		return ui;
	}

	private static String labelName(Waypoint point, int index) {
		return point.name().isEmpty() ? "Point " + (index + 1) : point.name();
	}

	/** @return the distance as the label shows it, made again only when the whole meters change */
	private static String distanceText(Shown entry) {
		long meters = Math.round(entry.distance);
		if (meters != entry.shownMeters) {
			entry.shownMeters = meters;
			entry.distanceText = WaypointLook.distance(entry.distance);
		}
		return entry.distanceText;
	}

	/** @return how much of a label shows: dim far away and for later points, fading out where the player stands at it */
	private float labelOpacity(Shown entry) {
		double far = Math.clamp((entry.distance - DIM_FROM_BLOCKS) / (DIM_TO_BLOCKS - DIM_FROM_BLOCKS), 0.0, 1.0);
		float opacity = (float) (1.0 - far * (1.0 - LEAST_FAR_OPACITY));
		if (entry.state == WaypointLook.State.NEXT) {
			opacity = Math.max(opacity, 0.85f);
		}
		return opacity * style.nearOpacity(entry.distance);
	}

	/** @return how many points show now, for the map and tests */
	int shownCount() {
		return shownCount;
	}
}
