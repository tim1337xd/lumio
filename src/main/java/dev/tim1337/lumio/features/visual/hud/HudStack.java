package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.ModFeatures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * The shared geometry of the stack above the hotbar, from the bottom up: the hotbar, the rows of status bars, the held item name and the action
 * bar. Every element of the stack asks here where it goes, so they share the hotbar's edges and never overlap. The hotbar is the mod's when it is
 * enabled, otherwise the vanilla one.
 */
public final class HudStack {
	/** The vanilla hotbar's size, in GUI pixels. */
	public static final int VANILLA_HOTBAR_WIDTH = 182;
	public static final int VANILLA_HOTBAR_HEIGHT = 22;
	/** The room the vanilla offhand slot takes beside the hotbar. */
	private static final int VANILLA_OFFHAND_ROOM = 29;
	/** Gap between the stack's rows, and between the hotbar and the lowest row, in GUI pixels at size 1. */
	public static final int GAP = 3;
	/** Height of the held item name's row and the gap above it, at size 1. */
	public static final int NAME_ROW = 12;
	private static final int NAME_GAP = 3;

	/** Top of the stacked bars as drawn last, in GUI pixels, or NaN while no bars are stacked. */
	private static float barsTop = Float.NaN;

	private HudStack() {
	}

	/** Counts the frames of the HUD, so the stack is laid out once per frame by whoever needs it first. */
	private static long frame;

	/** Called at the start of every HUD frame. */
	public static void newFrame() {
		frame++;
	}

	static long frame() {
		return frame;
	}

	/** Called by the status bars every frame with the top of their stack, or NaN when none is shown. */
	static void setBarsTop(float top) {
		barsTop = top;
	}

	/** @return the hotbar's left edge, top, width and size as drawn now: the mod's hotbar, or the vanilla one */
	public static void hotbar(int guiWidth, int guiHeight, float[] out) {
		HotbarFeature hotbar = ModFeatures.HOTBAR;
		if (hotbar != null && hotbar.isEnabled() && hotbar.hudBounds()[2] > 0) {
			int[] box = hotbar.hudBounds();
			out[0] = box[0];
			out[1] = box[1];
			out[2] = box[2];
			out[3] = hotbar.drawnScale();
			return;
		}
		out[0] = (guiWidth - VANILLA_HOTBAR_WIDTH) / 2f;
		out[1] = guiHeight - VANILLA_HOTBAR_HEIGHT;
		out[2] = VANILLA_HOTBAR_WIDTH;
		out[3] = 1f;
	}

	/** @return the hotbar's height as drawn now */
	public static float hotbarHeight() {
		HotbarFeature hotbar = ModFeatures.HOTBAR;
		return hotbar != null && hotbar.isEnabled() && hotbar.hudBounds()[2] > 0 ? hotbar.hudBounds()[3] : VANILLA_HOTBAR_HEIGHT;
	}

	/** @return the room the offhand slot takes beside the hotbar on a side, in GUI pixels, while it holds something */
	public static float offhandRoom(boolean left) {
		HotbarFeature hotbar = ModFeatures.HOTBAR;
		if (hotbar != null && hotbar.isEnabled() && hotbar.hudBounds()[2] > 0) {
			return hotbar.offhandRoom(left);
		}
		Player player = Minecraft.getInstance().player;
		if (player == null || player.getOffhandItem().isEmpty()) {
			return 0f;
		}
		// the game draws the offhand slot on the side of the off arm
		return (player.getMainArm().getOpposite() == HumanoidArm.LEFT) == left ? VANILLA_OFFHAND_ROOM : 0f;
	}

	private static final float[] HOTBAR = new float[4];

	/** @return the size the stack is drawn at: the hotbar's */
	public static float scale(int guiWidth, int guiHeight) {
		hotbar(guiWidth, guiHeight, HOTBAR);
		return HOTBAR[3];
	}

	/** @return the top of the stack below the item name: the highest stacked bar, or the hotbar */
	public static float stackTop(int guiWidth, int guiHeight) {
		if (ModFeatures.STATUS != null) {
			ModFeatures.STATUS.layoutOnce(Minecraft.getInstance(), guiWidth, guiHeight);
		}
		hotbar(guiWidth, guiHeight, HOTBAR);
		return Float.isNaN(barsTop) || ModFeatures.STATUS == null || !ModFeatures.STATUS.isEnabled() ? HOTBAR[1] : Math.min(barsTop, HOTBAR[1]);
	}

	/** @return the bottom of the held item name's row */
	public static float nameBottom(int guiWidth, int guiHeight) {
		float top = stackTop(guiWidth, guiHeight);
		return top - NAME_GAP * HOTBAR[3];
	}

	/** @return the bottom of the action bar's place, above the item name's row (kept free, so the text does not jump with every item switch) */
	public static float actionBarBottom(int guiWidth, int guiHeight) {
		float nameBottom = nameBottom(guiWidth, guiHeight);
		return nameBottom - NAME_ROW * HOTBAR[3];
	}

	/** Where the vanilla game draws the held item name and the action bar: their bottom edge, measured from the screen's bottom. */
	private static final int VANILLA_NAME_BOTTOM = 50;
	private static final int VANILLA_NAME_LOWER_NO_DAMAGE = 14;
	private static final int VANILLA_ACTION_BAR_BOTTOM = 64;

	/** @return the bottom edge of the vanilla held item name, in GUI pixels */
	public static float vanillaNameBottom(int guiHeight) {
		Minecraft client = Minecraft.getInstance();
		boolean lower = client.gameMode != null && !client.gameMode.canHurtPlayer();
		return guiHeight - VANILLA_NAME_BOTTOM + (lower ? VANILLA_NAME_LOWER_NO_DAMAGE : 0);
	}

	/** @return the bottom edge of the vanilla action bar */
	public static float vanillaActionBarBottom(int guiHeight) {
		return guiHeight - VANILLA_ACTION_BAR_BOTTOM;
	}

	/**
	 * Moves what the vanilla game draws next (its item name or action bar) up above the stacked bars where it would cover them. Pair it with
	 * {@link #endLift}.
	 *
	 * @param name whether it is the item name (otherwise the action bar, which goes above the name's row)
	 * @return whether it was moved
	 */
	public static boolean beginLift(GuiGraphicsExtractor graphics, float vanillaBottom, boolean name) {
		if (ModFeatures.STATUS == null || !ModFeatures.STATUS.isEnabled()) {
			return false;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float wanted = name ? nameBottom(width, height) : actionBarBottom(width, height);
		float lift = wanted - vanillaBottom;
		if (lift >= 0f) {
			return false;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(0f, lift);
		return true;
	}

	public static void endLift(GuiGraphicsExtractor graphics, boolean lifted) {
		if (lifted) {
			graphics.pose().popMatrix();
		}
	}

	/** @return the center of the stack across */
	public static float centerX(int guiWidth, int guiHeight) {
		hotbar(guiWidth, guiHeight, HOTBAR);
		return HOTBAR[0] + HOTBAR[2] / 2f;
	}
}
