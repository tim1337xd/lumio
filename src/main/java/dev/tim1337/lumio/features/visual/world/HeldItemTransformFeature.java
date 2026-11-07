package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.render.OutlineItem;
import dev.tim1337.lumio.ui.EditableHud;
import dev.tim1337.lumio.ui.HudEditScreen;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/** Changes the size and position of the item the local player holds in first person, and the speed of the first person swing. All of it is the first person hand's only: the player model and other views stay the game's. */
public class HeldItemTransformFeature extends Feature implements EditableHud {
	/** How the first person swing looks. */
	public enum SwingLook {
		GAME("Game"), FANCY("Fancy");

		private final String label;

		SwingLook(String label) {
			this.label = label;
		}

		Component label() {
			return Component.literal(label);
		}
	}

	private final FloatSetting size = add(new FloatSetting("size", Component.literal("Item size"), 1f, 0.1f, 3f, 2).percent(), "Size", "Scales the item at your grip");
	private final FloatSetting sizeX = add(new FloatSetting("size_x", Component.literal("Width (X)"), 1f, 0.25f, 2f, 2).percent(), "Size");
	private final FloatSetting sizeY = add(new FloatSetting("size_y", Component.literal("Height (Y)"), 1f, 0.25f, 2f, 2).percent(), "Size");
	private final FloatSetting sizeZ = add(new FloatSetting("size_z", Component.literal("Depth (Z)"), 1f, 0.25f, 2f, 2).percent(), "Size");
	private final EnumSetting<SwingLook> swingLook = add(new EnumSetting<>("swing_look", Component.literal("Swing"), SwingLook.GAME, SwingLook::label, EnumSetting.Style.SEGMENTED), "Swing");
	private final BooleanSetting equipAnimation = add(new BooleanSetting("equip_animation", Component.literal("Equip animation"), true), "Swing");
	private final FloatSetting x = add(new FloatSetting("x", Component.literal("Horizontal"), 0f, -1f, 1f, 2), "Position");
	private final FloatSetting y = add(new FloatSetting("y", Component.literal("Vertical"), 0f, -1f, 1f, 2), "Position");
	/** The swing's speed, as an exponent: see {@link SwingSpeed}. A new key, since the old "swing_speed" was a plain factor: see ConfigUpgrade. */
	private final FloatSetting swingRate = add(new FloatSetting("swing_rate", Component.literal("Swing speed"), SwingSpeed.GAME, SwingSpeed.SLOWEST, SwingSpeed.FASTEST, 2), "Swing", "0 is the game's speed");
	private final BooleanSetting ignoreHaste = add(new BooleanSetting("ignore_haste", Component.literal("Ignore Haste"), false), "Swing", "Haste and Mining Fatigue don't change the speed");

	private final BooleanSetting showHand = add(new BooleanSetting("show_hand", Component.literal("Show hand"), false), "Hand");

	private final Vector3f offset = new Vector3f();
	/** How far the game lowers the hand for the whole equip movement, as its arm transform does: the drop is this times the movement. */
	private static final float EQUIP_DROP_BLOCKS = 0.6f;
	/** How near to the eye an item in a use pose may come. */
	private static final float NEAREST_BLOCKS = 0.25f;
	/** How many kinds of item per hand keep the place they are held at. */
	private static final int MOST_KINDS = 16;

	/**
	 * Where an item of a kind is held at rest, without the equip movement: the place it was given, the middle of its model along the camera's
	 * directions, and its pose with the camera's turn taken out.
	 *
	 * @param settled whether it was seen at rest, and not worked out from a moment of the equip movement
	 */
	private record Held(float place, Vector3f middle, Matrix4f pose, boolean settled) {
	}

	// per side of the screen: where each kind of item is held, and how far the equip movement lowers the hand now
	@SuppressWarnings({"unchecked", "rawtypes"})
	private final Map<Item, Held>[] held = new Map[]{new LinkedHashMap<Item, Held>(), new LinkedHashMap<Item, Held>()};
	private final float[] equipDrop = new float[2];
	private final Vector3f middle = new Vector3f();
	private final Vector3f middleLow = new Vector3f();
	private final Vector3f middleHigh = new Vector3f();
	private final org.joml.Quaternionf cameraTurn = new org.joml.Quaternionf();
	private Camera middleCamera;
	/** Kept, as it is called for every point of a model while a use pose lasts. */
	private final Consumer<Vector3f> middlePoint = point -> {
		float left = point.dot(middleCamera.leftVector());
		float up = point.dot(middleCamera.upVector());
		float forward = point.dot(middleCamera.forwardVector());
		middleLow.set(Math.min(middleLow.x, left), Math.min(middleLow.y, up), Math.min(middleLow.z, forward));
		middleHigh.set(Math.max(middleHigh.x, left), Math.max(middleHigh.y, up), Math.max(middleHigh.z, forward));
	};
	private final Matrix4f inverse = new Matrix4f();

	public HeldItemTransformFeature() {
		super("held_item_transform", "Held item", "How the item in your hand looks and moves. Move it in Edit HUD layout.", true);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("hand");
		summary("How the item in your hand looks and moves");
		addResetButton();
		// set by dragging the item in the HUD editor
		x.visibleWhen(() -> false);
		y.visibleWhen(() -> false);
	}

	/**
	 * Transforms an item's pose if it is held by the local player in first person. The position is camera-relative, so its directions match the
	 * screen.
	 *
	 * @return whether the pose was changed, which must be undone with {@link PoseStack#popPose()}
	 */
	public boolean apply(PoseStack pose, ItemDisplayContext context, OutlineItem item) {
		// with every setting the game's, the hand is the game's, also in a use pose
		if (!isEnabled() || !HeldItem.isFirstPerson(context) || neutral()) {
			return false;
		}
		boolean moved = x.get() != 0f || y.get() != 0f;
		boolean inUsePose = inUsePose(context);
		if (!inUsePose && !moved && size.get() == 1f && sizeX.get() == 1f && sizeY.get() == 1f && sizeZ.get() == 1f) {
			rememberHolding(pose, context, item);
			return false;
		}

		pose.pushPose();
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		if (moved) {
			offset.set(camera.leftVector()).mul(-x.get())
				.fma(y.get(), camera.upVector());
			// the pose is world-oriented (the camera rotation is applied separately) and may rotate and scale, so transform the offset into its local
			// space
			pose.last().pose().invert(inverse).transformDirection(offset);
			pose.translate(offset.x, offset.y, offset.z);
		}
		pose.scale(size.get() * sizeX.get(), size.get() * sizeY.get(), size.get() * sizeZ.get());
		if (inUsePose) {
			stayWhereHeld(pose, context, item, camera);
		} else {
			rememberHolding(pose, context, item);
		}
		return true;
	}

	/** @return whether the game has this hand's item in a pose of its own: it is being used (a bow drawn, food eaten, a shield up), or it is a loaded crossbow */
	private static boolean inUsePose(ItemDisplayContext context) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		InteractionHand hand = (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) == (player.getMainArm() == HumanoidArm.RIGHT) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		if (player.isUsingItem()) {
			return player.getUsedItemHand() == hand;
		}
		ItemStack held = player.getItemInHand(hand);
		return held.is(Items.CROSSBOW) && CrossbowItem.isCharged(held);
	}

	/** @return the hash of the place and size the item is given, so a held place is noted again when they change */
	private float placeHash() {
		return 31f * (31f * (31f * (31f * (31f * x.get() + y.get()) + size.get()) + sizeX.get()) + sizeY.get()) + sizeZ.get();
	}

	/**
	 * Notes where the middle of the item is while it is simply held, in the camera's directions, for each kind of item, without the equip
	 * movement: so it is known also when the item is used right after it was changed, before it came to rest. Noted again only while it was not yet
	 * seen at rest, or when its place changes; never in a frame that swings it.
	 */
	private void rememberHolding(PoseStack pose, ItemDisplayContext context, OutlineItem item) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int side = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? 0 : 1;
		Item kind = player.getItemInHand((context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) == (player.getMainArm() == HumanoidArm.RIGHT) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND).getItem();
		float place = placeHash();
		float drop = equipDrop[side];
		Held known = held[side].get(kind);
		if (known != null && known.place() == place && (known.settled() || drop > 0f) || swingRunning() || !middleOf(pose, item, Minecraft.getInstance().gameRenderer.mainCamera())) {
			return;
		}
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		camera.rotation().conjugate(cameraTurn);
		// the equip movement lowered the hand along the camera's up: that is taken back out, so this is the place at rest
		org.joml.Vector3fc up = camera.upVector();
		Matrix4f restPose = new Matrix4f(pose.last().pose()).translateLocal(up.x() * drop, up.y() * drop, up.z() * drop).rotateLocal(cameraTurn);
		held[side].remove(kind);
		held[side].put(kind, new Held(place, new Vector3f(middle.x, middle.y + drop, middle.z), restPose, drop <= 0f));
		while (held[side].size() > MOST_KINDS) {
			held[side].remove(held[side].keySet().iterator().next());
		}
	}

	/**
	 * @return where the item in a use pose is held at rest: its own kind's place, else the place last noted with the same settings, the nearest
	 *         guess for a kind never seen at rest yet; null if none is known
	 */
	private Held heldPlace(int side, Item kind) {
		float place = placeHash();
		Held known = held[side].get(kind);
		if (known != null && known.place() == place) {
			return known;
		}
		Held latest = null;
		for (Held other : held[side].values()) {
			if (other.place() == place) {
				latest = other;
			}
		}
		return latest;
	}

	/**
	 * Moves an item in a use pose so that its middle is where it is while held, lowered by the equip movement of now, as any item is: so a bow drawn
	 * while it is still coming up rises with it and ends at its place, without a jump. The pose still turns it, and the size stays what it was anyway.
	 * Only across the screen: its depth is the pose's, as an item turned toward the camera would come too near.
	 */
	private void stayWhereHeld(PoseStack pose, ItemDisplayContext context, OutlineItem item, Camera camera) {
		int side = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? 0 : 1;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		InteractionHand hand = (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) == (player.getMainArm() == HumanoidArm.RIGHT) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		Item kind = player.isUsingItem() && player.getUsedItemHand() == hand ? player.getUseItem().getItem() : player.getItemInHand(hand).getItem();
		Held known = heldPlace(side, kind);
		if (known == null || !middleOf(pose, item, camera)) {
			return;
		}
		float drop = equipDrop[side];
		org.joml.Vector3fc up = camera.upVector();
		if (kind == Items.TRIDENT) {
			// its use pose runs the long shaft past the camera, and no place makes that look right: it stays as it is held
			pose.last().pose().set(known.pose()).rotateLocal(camera.rotation()).translateLocal(-up.x() * drop, -up.y() * drop, -up.z() * drop);
			pose.last().normal().set(pose.last().pose()).invert().transpose();
			return;
		}
		offset.set(camera.leftVector()).mul(known.middle().x - middle.x)
			.fma(known.middle().y - drop - middle.y, up)
			// and nothing of it reaches back past the camera, where it would be cut open
			.fma(Math.max(0f, NEAREST_BLOCKS - middleLow.z), camera.forwardVector());
		pose.last().pose().invert(inverse).transformDirection(offset);
		pose.translate(offset.x, offset.y, offset.z);
	}

	/** Works out the middle of the item's model, along the camera's left, up and forward. @return whether it has one */
	private boolean middleOf(PoseStack pose, OutlineItem item, Camera camera) {
		middleCamera = camera;
		middleLow.set(Float.MAX_VALUE);
		middleHigh.set(-Float.MAX_VALUE);
		for (int i = 0; i < item.lumio$layerCount(); i++) {
			item.lumio$layer(i).lumio$points(pose, middlePoint);
		}
		if (middleLow.x > middleHigh.x) {
			return false;
		}
		middle.set(middleLow).add(middleHigh).mul(0.5f);
		return true;
	}

	/** The game's equip movement of a hand being drawn, from its arm transform: how far it lowers the hand, after the share this feature gives it. */
	public void noteEquip(HumanoidArm arm, float equip) {
		SwingTrace.equip(equip);
		equipDrop[arm == HumanoidArm.RIGHT ? 0 : 1] = EQUIP_DROP_BLOCKS * equip * equipShare(arm);
	}

	// ------------------------------------------------------------------------------------------------ HUD editor

	/** The item's default on-screen center, as fractions of the screen width and height. */
	private static final float HOME_X = 0.85f;
	private static final float HOME_Y = 0.80f;
	/** The hand is always rendered with this field of view, whatever the game setting is. */
	private static final double HAND_FOV = Math.toRadians(70.0);

	/** The item's on-screen bounds from the last time it was drawn: x, y, width and height in GUI pixels. */
	private int[] seenBox;
	private long seenAt;
	/** The item's distance in front of the eye in blocks, which determines how many pixels one block of offset is. */
	private float seenDepth = 0.7f;
	private final Vector3f corner = new Vector3f();

	/**
	 * Records where the held item is on screen by projecting its corners with the same projection the game uses for the hand. This is the HUD editor
	 * box, so the box matches the item.
	 */
	public void recordBounds(PoseStack pose, ItemDisplayContext context, OutlineItem item) {
		var client = Minecraft.getInstance();
		if (!HudEditScreen.isOpen() || client.player == null
			|| (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) != (client.player.getMainArm() == HumanoidArm.RIGHT)) {
			return;
		}
		Camera camera = client.gameRenderer.mainCamera();
		var window = client.getWindow();
		int guiWidth = window.getGuiScaledWidth();
		int guiHeight = window.getGuiScaledHeight();
		double tan = Math.tan(HAND_FOV / 2.0);
		float aspect = (float) guiWidth / guiHeight;
		float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		boolean[] behind = {false};
		// project every corner of the item model as placed in the hand, as the game does
		Consumer<Vector3f> project = point -> {
			float forward = point.dot(camera.forwardVector());
			if (forward < 0.05f) {
				behind[0] = true;
				return;
			}
			float nx = -point.dot(camera.leftVector()) / (float) (forward * tan * aspect);
			float ny = point.dot(camera.upVector()) / (float) (forward * tan);
			float px = (nx * 0.5f + 0.5f) * guiWidth;
			float py = (0.5f - ny * 0.5f) * guiHeight;
			box[0] = Math.min(box[0], px);
			box[1] = Math.min(box[1], py);
			box[2] = Math.max(box[2], px);
			box[3] = Math.max(box[3], py);
		};
		for (int i = 0; i < item.lumio$layerCount(); i++) {
			item.lumio$layer(i).lumio$points(pose, project);
		}
		if (behind[0] || box[0] > box[2]) {
			return;
		}
		pose.last().pose().transformPosition(0f, 0f, 0f, corner);
		seenDepth = Math.max(0.1f, corner.dot(camera.forwardVector()));
		seenBox = new int[]{Math.round(box[0]), Math.round(box[1]), Math.max(8, Math.round(box[2] - box[0])), Math.max(8, Math.round(box[3] - box[1]))};
		seenAt = System.nanoTime();
	}
	/** @return screen pixels per position unit (one block) at the item's depth */
	private float pixelsPerUnit(int guiHeight) {
		return (float) (guiHeight / (2.0 * seenDepth * Math.tan(HAND_FOV / 2.0)));
	}

	@Override
	public String hudName() {
		return "Held item";
	}

	@Override
	public boolean hudShown() {
		return isEnabled() && Minecraft.getInstance().player != null;
	}

	@Override
	public int[] hudBounds() {
		// where the item was seen if it was drawn recently: the actual position, whatever the item and however it is held
		if (seenBox != null && System.nanoTime() - seenAt < 1_500_000_000L) {
			// the item can extend past the screen edge, which would put its corner (the resize handle) out of reach
			var shown = Minecraft.getInstance().getWindow();
			int right = Math.min(seenBox[0] + seenBox[2], shown.getGuiScaledWidth() - 6);
			int bottom = Math.min(seenBox[1] + seenBox[3], shown.getGuiScaledHeight() - 6);
			int left = Math.min(seenBox[0], right - 24);
			int top = Math.min(seenBox[1], bottom - 24);
			return new int[]{left, top, right - left, bottom - top};
		}
		var window = Minecraft.getInstance().getWindow();
		int width = window.getGuiScaledWidth();
		int height = window.getGuiScaledHeight();
		float side = height * 0.30f * size.get();
		float unit = pixelsPerUnit(height);
		float centerX = width * HOME_X + x.get() * unit;
		float centerY = height * HOME_Y - y.get() * unit;
		return new int[]{Math.round(centerX - side / 2f), Math.round(centerY - side / 2f), Math.round(side), Math.round(side)};
	}

	@Override
	public void hudMove(int dx, int dy) {
		float unit = pixelsPerUnit(Minecraft.getInstance().getWindow().getGuiScaledHeight());
		x.set(Mth.clamp(x.get() + dx / unit, x.min(), x.max()));
		y.set(Mth.clamp(y.get() - dy / unit, y.min(), y.max()));
	}

	/** The item can be made as small as its size setting allows. */
	@Override
	public float hudMinScale() {
		return size.min();
	}

	@Override
	public float hudMaxScale() {
		return size.max();
	}

	@Override
	public float hudScale() {
		return size.get();
	}

	@Override
	public void hudSetScale(float scale) {
		size.set(Mth.clamp(scale, size.min(), size.max()));
	}

	/** Scales from its center. */
	@Override
	public float hudAnchorX(int[] box) {
		return box[0] + box[2] / 2f;
	}

	@Override
	public float hudRadius() {
		return 6f;
	}

	@Override
	public void hudReset() {
		size.reset();
		sizeX.reset();
		sizeY.reset();
		sizeZ.reset();
		x.reset();
		y.reset();
	}

	/**
	 * @return whether every setting is the game's: the item's size and place, the swing's look and speed, the equip movement and no hand; nothing
	 * of the hand's look is changed then
	 */
	private boolean neutral() {
		return x.get() == 0f && y.get() == 0f && size.get() == 1f && sizeX.get() == 1f && sizeY.get() == 1f && sizeZ.get() == 1f
			&& swingLook.get() == SwingLook.GAME && SwingSpeed.isGame(swingRate.get()) && !ignoreHaste.get() && equipAnimation.get() && !showHand.get();
	}

	/**
	 * @return whether anything of the swing is handled from here: the item's height after a hit and while it is changed in place, and only with
	 * settings that are not the game's. The swing itself is always the game's: the hand draws the game's own timer, with only its length changed
	 * (see {@link #swingDuration}).
	 */
	public boolean handlesSwing() {
		return isEnabled() && !neutral();
	}

	/**
	 * The length of the local player's swing in ticks, as the game works it out: at the chosen speed, else the game's own.
	 *
	 * @param game      the game's length, with Haste and Mining Fatigue
	 * @param baseTicks the length of the item's swing without effects, asked only when Haste is ignored
	 */
	public int swingDuration(int game, IntSupplier baseTicks) {
		if (!customSwing()) {
			return game;
		}
		return Math.max(1, Math.round(SwingSpeed.ticks(ignoreHaste.get() ? baseTicks.getAsInt() : game, swingRate.get())));
	}

	/** @return how far the swing is as the first person hand draws it: the game's own, read here for the swing values of the Debug log */
	public float swingDrawn(float game, float partialTick) {
		if (SwingTrace.on()) {
			LocalPlayer player = Minecraft.getInstance().player;
			SwingTrace.frame(game, game, partialTick, player == null ? 0f : swingTicks(player, SwingSpeed.BASE_TICKS));
		}
		return game;
	}

	/** @return whether the first person hand is swinging, by the game's swing, while anything of it is handled here */
	public boolean swingRunning() {
		if (!handlesSwing()) {
			return false;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.swinging;
	}

	/** @return how much of the game's movement the item in a hand makes, for its swing and its drop when changed */
	public float swingMovement(HumanoidArm arm) {
		if (!isEnabled() || size.get() >= 1f) {
			return 1f;
		}
		SwingTrace.ran(SwingTrace.SHORTER);
		return SwingShape.movementShare(size.get());
	}

	/** @return whether the item in a hand swings the fancy way: the game's swing is then not drawn for it */
	public boolean fancySwing(HumanoidArm arm) {
		boolean fancy = isEnabled() && swingLook.get() == SwingLook.FANCY;
		if (fancy) {
			SwingTrace.ran(SwingTrace.FANCY);
		}
		return fancy;
	}

	/** @return how far an item that is changed drops: not at all without the equip animation */
	public float equipShare(HumanoidArm arm) {
		if (!isEnabled()) {
			return 1f;
		}
		return equipAnimation.get() ? swingMovement(arm) : 0f;
	}

	/** @return whether the player's arm is drawn holding the item */
	public boolean showsHand() {
		return isEnabled() && showHand.get();
	}

	/** Draws the arm that holds an item, at the item's place and size: the pose is the item's by now, with the swing and the equip in it. */
	public void submitArm(PoseStack pose, SubmitNodeCollector collector, int light, ItemDisplayContext context) {
		if (!showsHand()) {
			return;
		}
		boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
		pose.pushPose();
		SwingShape.placeArmAtItem(pose, right ? 1 : -1);
		HeldArm.submit(pose, collector, light, right ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
		pose.popPose();
	}

	/** @return how many times faster than the game the local player's swing is drawn, for the item's dip */
	public float swingSpeedNow() {
		return customSwing() ? SwingSpeed.factor(swingRate.get()) : 1f;
	}

	/** @return whether the swing runs at another speed than the game's */
	public boolean customSwing() {
		return isEnabled() && (!SwingSpeed.isGame(swingRate.get()) || ignoreHaste.get());
	}

	/** @return how many ticks the local player's swing takes on screen, from the length of the item's swing without effects */
	public float swingTicks(LocalPlayer player, int baseTicks) {
		if (ignoreHaste.get()) {
			return SwingSpeed.ticks(baseTicks, swingRate.get());
		}
		MobEffectInstance fatigue = player.getEffect(MobEffects.MINING_FATIGUE);
		float withEffects = SwingSpeed.withEffects(baseTicks, MobEffectUtil.hasDigSpeed(player), MobEffectUtil.getDigSpeedAmplification(player),
			fatigue != null, fatigue == null ? 0 : fatigue.getAmplifier());
		return SwingSpeed.ticks(withEffects, swingRate.get());
	}

	/** @return whether the player holds attack on a block: the swings of the game's mining then follow each other without a pause */
	public boolean mining() {
		Minecraft client = Minecraft.getInstance();
		return client.gameMode != null && client.gameMode.isDestroying() && client.options.keyAttack.isDown();
	}

}
