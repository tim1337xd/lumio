package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;
import dev.tim1337.lumio.ui.VanillaLook;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.Mth;

/**
 * The game's sliders in the menu's look: the same box as a button, its name on the left and its value on the right (whole, its digits in equal
 * cells; the name is what shortens), a soft tint in the menu color up to the value, a thin bright line along the bottom edge filled to the value,
 * and a round knob on that line that grows slightly under the pointer and while it is dragged. At nothing it says "Off" where the game says OFF,
 * and has no tint. Nothing lies over the texts. Only the drawing: dragging, clicks in the bar, the wheel and the keys are the game's own.
 */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
	/** How strong the tint is, and how much stronger under the pointer. */
	@Unique
	private static final float TINT = 0.22f;
	@Unique
	private static final float TINT_LIT = 0.08f;
	/** How much the whole box brightens under the pointer. */
	@Unique
	private static final float BOX_LIT = 0.06f;
	/** The line: how bright its empty part is, and how far its filled part is from the menu color toward white. */
	@Unique
	private static final float LINE_EMPTY = 0.12f;
	@Unique
	private static final float LINE_WHITER = 0.45f;
	/** How bright the knob is at rest; under the pointer and while dragged it is white. */
	@Unique
	private static final float KNOB_REST_LIGHT = 0.88f;
	@Unique
	private static final float DISABLED = 0.5f;
	@Unique
	private static final float FOCUS_RING = 0.95f;
	@Unique
	private static final float HOVER_PER_SECOND = 12f;

	@Shadow
	protected double value;
	@Shadow
	private boolean dragging;

	@Unique
	private float lumio$hover;
	@Unique
	private long lumio$stamp;

	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
	private void lumio$glass(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!ModFeatures.MENUS.buttonsActive()) {
			return;
		}
		AbstractWidget widget = (AbstractWidget) (Object) this;
		Minecraft client = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = lumio$stamp == 0 ? 0.1f : Math.min(0.1f, (now - lumio$stamp) / 1_000_000_000f);
		lumio$stamp = now;
		boolean lit = widget.isActive() && (widget.isHoveredOrFocused() || dragging);
		lumio$hover += ((lit ? 1f : 0f) - lumio$hover) * (1f - (float) Math.exp(-dt * HOVER_PER_SECOND));
		float h = Mth.clamp(lumio$hover, 0f, 1f);
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, ModFeatures.MENU.theme().palette(), ModFeatures.MENU.accentArgb(), dt, false);
		int x = widget.getX();
		int y = widget.getY();
		int width = widget.getWidth();
		int height = widget.getHeight();
		float radius = VanillaLook.radius(height);
		float fade = widget.isActive() ? 1f : DISABLED;
		float amount = Mth.clamp((float) value, 0f, 1f);

		// the box of a button: the empty part is the plain dark box
		HudStyle.panel(ui, x, y, width, height, radius, HudStyle.opacity() * fade, UiGlow.Target.BUTTONS, h * fade);
		// the soft tint, up to the knob's middle, ending in a soft edge; nothing at 0
		int tinted = VanillaLook.tinted(x, width, amount);
		if (tinted > 0) {
			int tint = Ui.withOpacity(ui.accent, (TINT + TINT_LIT * h) * fade);
			int solid = Math.max(0, tinted - VanillaLook.FILL_EDGE);
			graphics.enableScissor(x, y, x + solid, y + height);
			ui.rounded(x, y, width, height, radius, tint);
			graphics.disableScissor();
			for (int step = 0; step < tinted - solid; step++) {
				float share = 1f - (step + 1f) / (VanillaLook.FILL_EDGE + 1f);
				graphics.enableScissor(x + solid + step, y, x + solid + step + 1, y + height);
				ui.rounded(x, y, width, height, radius, Ui.withOpacity(tint, share));
				graphics.disableScissor();
			}
		}
		if (h > 0.01f) {
			ui.rounded(x, y, width, height, radius, Ui.withOpacity(0xFFFFFFFF, BOX_LIT * h * fade));
		}
		if (widget.isFocused() && widget.isActive()) {
			ui.ring(x, y, width, height, radius, 1, Ui.withOpacity(ui.accent, FOCUS_RING));
		}

		// the line along the bottom edge, bright up to the value, and the round knob on it
		float lineY = y + height - VanillaLook.LINE_HEIGHT;
		float lineLeft = x + VanillaLook.LINE_INSET;
		float lineWidth = width - 2f * VanillaLook.LINE_INSET;
		float knobX = VanillaLook.knobX(x, width, amount);
		ui.roundedSmooth(lineLeft, lineY, lineWidth, VanillaLook.LINE_HEIGHT, VanillaLook.LINE_HEIGHT / 2f, Ui.withOpacity(0xFFFFFFFF, LINE_EMPTY * fade));
		if (knobX > lineLeft) {
			ui.roundedSmooth(lineLeft, lineY, knobX - lineLeft, VanillaLook.LINE_HEIGHT, VanillaLook.LINE_HEIGHT / 2f, Ui.withOpacity(Ui.mix(ui.accent, 0xFFFFFFFF, LINE_WHITER), fade));
		}
		float gripRadius = VanillaLook.knobRadius(Math.max(h, dragging ? 1f : 0f));
		float knobLight = (KNOB_REST_LIGHT + (1f - KNOB_REST_LIGHT) * Math.max(h, dragging ? 1f : 0f)) * fade;
		ui.roundedSmooth(knobX - gripRadius, lineY + VanillaLook.LINE_HEIGHT / 2f - gripRadius, 2f * gripRadius, 2f * gripRadius, gripRadius, Ui.withOpacity(0xFFFFFFFF, knobLight));

		// the name on the left, the value on the right: the value is drawn whole, the name has what is left
		String message = widget.getMessage().getString();
		String shown = VanillaLook.shownValue(VanillaLook.sliderValue(message));
		int valueWidth = ui.tabularWidth(shown);
		// a name too long for its room is set smaller first, and only then shortened
		String whole = VanillaLook.sliderName(message);
		int room = VanillaLook.nameRoom(width, valueWidth);
		float nameScale = ui.width(whole) <= room ? TextScale.BODY : TextScale.SMALL;
		String name = nameScale == TextScale.BODY ? whole : ui.fitScaled(whole, room, nameScale);
		int middle = y + height / 2;
		int text = widget.isActive() ? MacTheme.DARK.palette().text() : SystemColors.GRAY;
		if (nameScale == TextScale.BODY) {
			ui.textMiddle(name, x + VanillaLook.SLIDER_SIDE, middle, text);
		} else {
			ui.textAtScaled(name, x + VanillaLook.SLIDER_SIDE, middle - client.font.lineHeight * nameScale / 2f, nameScale, text);
		}
		// at nothing the value is quieter, as the mockup has "Off"
		ui.textRightTabular(shown, x + width - VanillaLook.SLIDER_SIDE, middle, amount <= 0f ? Ui.withOpacity(text, OFF_LIGHT) : text);
		ci.cancel();
	}

	/** How bright the value is at nothing. */
	@Unique
	private static final float OFF_LIGHT = 0.6f;
}
