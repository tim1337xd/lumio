package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.LumioChat;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The waypoint keys in the world. A tap of the place key opens the card for the block the player looks at (or the block they stand on when none
 * is near); holding it records a route: every block the player mines, and every place they stand still at for a moment, becomes the next point,
 * until the key is let go. Only reads what the player does; nothing is sent.
 */
final class WaypointPlacing {
	static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Lumio.id("waypoints"));
	/** How long the key is held before it records instead of placing one point. */
	static final long HOLD_MILLIS = 400L;
	/** How far the player looks for a block to mark. */
	private static final double REACH_BLOCKS = 30.0;
	/** Standing still this long (in ticks) while recording marks the place. */
	private static final int STILL_TICKS = 30;
	/** Moving less than this (in blocks) counts as standing still. */
	private static final double STILL_BLOCKS = 0.3;
	/** A recorded point this close to the one before is not added again. */
	private static final double LEAST_GAP_BLOCKS = 2.0;

	private final WaypointsFeature feature;
	private KeyMapping placeKey;
	private KeyMapping nextKey;
	private KeyMapping previousKey;
	private KeyMapping restartKey;
	private KeyMapping mapKey;
	private boolean wasDown;
	private long pressedAt;
	private boolean recording;
	private WaypointGroup recordInto;
	private int recorded;
	private double stillX;
	private double stillY;
	private double stillZ;
	private int stillTicks;

	WaypointPlacing(WaypointsFeature feature) {
		this.feature = feature;
	}

	/**
	 * Registers the keys, all unbound at first: place, the next and the previous point of a route, and the big map. They are key fields in
	 * the feature's own settings (its Keys group) and in the list of all keys; both edit the same key.
	 */
	void registerKeys() {
		placeKey = register("waypoint_place", InputConstants.UNKNOWN.getValue());
		nextKey = register("waypoint_next", InputConstants.UNKNOWN.getValue());
		previousKey = register("waypoint_previous", InputConstants.UNKNOWN.getValue());
		mapKey = register("waypoint_map", InputConstants.UNKNOWN.getValue());
		restartKey = register("waypoint_restart", InputConstants.UNKNOWN.getValue());
	}

	private KeyMapping register(String name, int key) {
		KeyMapping mapping = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + Lumio.MOD_ID + "." + name, InputConstants.Type.KEYSYM, key, CATEGORY));
		feature.listKey(mapping);
		return mapping;
	}

	/** @return the key that places a point at the block looked at, or edits the point looked at */
	KeyMapping placeKey() {
		return placeKey;
	}

	/** @return the key that moves a route on to its next point */
	KeyMapping nextKey() {
		return nextKey;
	}

	/** @return the key that takes a route back to its point before */
	KeyMapping previousKey() {
		return previousKey;
	}

	/** @return the key that starts the route here at its first point again */
	KeyMapping restartKey() {
		return restartKey;
	}

	/** @return the key that shows the big map while held */
	KeyMapping mapKey() {
		return mapKey;
	}

	/** @return the name of the place key, as the keyboard shows it ("B"), or "" while it is not set */
	String placeKeyName() {
		return placeKey == null || placeKey.isUnbound() ? "" : dev.tim1337.lumio.features.misc.keybinds.KeyCombos.shown(placeKey);
	}

	boolean recording() {
		return recording;
	}

	int recorded() {
		return recorded;
	}

	/** Called every tick: a tap opens the card, a hold records. */
	void tick(Minecraft client) {
		if (placeKey == null) {
			return;
		}
		while (placeKey.consumeClick()) {
			// the presses are read from whether the key is down; the counted clicks are only emptied
		}
		LocalPlayer player = client.player;
		boolean down = placeKey.isDown() && player != null && client.gui.screen() == null;
		long now = System.currentTimeMillis();
		if (down && !wasDown) {
			pressedAt = now;
		}
		if (down && !recording && now - pressedAt >= HOLD_MILLIS) {
			startRecording(player);
		}
		if (!down && wasDown) {
			if (recording) {
				stopRecording();
			} else if (player != null && client.gui.screen() == null) {
				BlockPos target = target(client, player);
				// an add-on may take it, such as for a point of the dungeon room the player is in
				if (!dev.tim1337.lumio.api.experimental.waypoint.WaypointCards.claimed(target.getX(), target.getY(), target.getZ())) {
					client.gui.setScreen(new WaypointPlaceScreen(feature, target.getX(), target.getY(), target.getZ()));
				}
			}
		}
		wasDown = down;
		if (recording && player != null) {
			followStill(player);
		}
	}

	/** @return the block the player looks at within reach, or the block they stand on */
	private static BlockPos target(Minecraft client, LocalPlayer player) {
		HitResult hit = player.pick(REACH_BLOCKS, 1f, false);
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			return block.getBlockPos();
		}
		return player.blockPosition();
	}

	private void startRecording(LocalPlayer player) {
		recording = true;
		recorded = 0;
		recordInto = feature.groupForRecording();
		stillX = player.getX();
		stillY = player.getY();
		stillZ = player.getZ();
		stillTicks = 0;
	}

	private void stopRecording() {
		recording = false;
		if (recordInto != null && recorded > 0) {
			LumioChat.info(Component.literal("Recorded " + recorded + (recorded == 1 ? " point" : " points") + " into " + recordInto.name() + "."));
		}
		recordInto = null;
	}

	/** Standing still for a moment marks the place. */
	private void followStill(LocalPlayer player) {
		double dx = player.getX() - stillX;
		double dy = player.getY() - stillY;
		double dz = player.getZ() - stillZ;
		if (dx * dx + dy * dy + dz * dz > STILL_BLOCKS * STILL_BLOCKS) {
			stillX = player.getX();
			stillY = player.getY();
			stillZ = player.getZ();
			stillTicks = 0;
			return;
		}
		if (++stillTicks == STILL_TICKS) {
			BlockPos under = standingOn(player);
			if (under != null) {
				record(under);
			}
		}
	}

	/**
	 * @return the block the player stands on, as the game finds it (a slab, stairs or a carpet itself, else the solid block under the feet), or
	 *         null in the air or on nothing solid: a point is never a block of air
	 */
	private static BlockPos standingOn(LocalPlayer player) {
		if (!player.onGround()) {
			return null;
		}
		BlockPos pos = player.getOnPos();
		return player.level().getBlockState(pos).getCollisionShape(player.level(), pos).isEmpty() ? null : pos.immutable();
	}

	/** A block the player broke: while recording, it becomes the next point. */
	void blockBroken(BlockPos pos) {
		if (recording) {
			record(pos);
		}
	}

	private void record(BlockPos pos) {
		WaypointGroup group = recordInto;
		if (group == null) {
			return;
		}
		if (!group.points().isEmpty()) {
			Waypoint last = group.points().getLast();
			if (last.distanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < LEAST_GAP_BLOCKS * LEAST_GAP_BLOCKS) {
				return;
			}
		}
		if (group.add(new Waypoint(pos.getX(), pos.getY(), pos.getZ(), "Point " + (group.points().size() + 1)))) {
			recorded++;
			feature.store().changed(group);
		}
	}
}
