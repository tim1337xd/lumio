package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Choices as a grid of picture cards with the name below, for choices made by appearance, such as a sky. The selected card has a white frame;
 * the hovered one lifts slightly and lights up.
 */
public class CardGridControl<E extends Enum<E>> extends Control {
	private static final int GAP = 8;
	private static final int LABEL = 14;
	/** Maximum card width; each row holds as many cards as fit. */
	private static final int MAX_CARD = 128;

	private final EnumSetting<E> setting;
	private final Runnable onChanged;
	private final Map<E, Spring> lifts = new HashMap<>();
	private final Map<E, Spring> frames = new HashMap<>();
	private int columns = 1;
	private int cardWidth = MAX_CARD;
	private int cardHeight = MAX_CARD * 9 / 16;

	public CardGridControl(EnumSetting<E> setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	@Override
	protected int measure(Ui ui, int width) {
		int count = setting.choices().size();
		columns = Math.max(2, Math.min(count, (width + GAP) / (MAX_CARD * 3 / 4 + GAP)));
		cardWidth = Math.min(MAX_CARD, (width - GAP * (columns - 1)) / columns);
		cardHeight = cardWidth * 9 / 16;
		int rows = (count + columns - 1) / columns;
		return rows * (cardHeight + LABEL + 6) + (rows - 1) * 4 + 4;
	}

	private int[] cardAt(int index) {
		int column = index % columns;
		int row = index / columns;
		return new int[]{x + column * (cardWidth + GAP), y + 3 + row * (cardHeight + LABEL + 10), cardWidth, cardHeight};
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		List<E> choices = setting.choices();
		E chosen = setting.get();
		for (int i = 0; i < choices.size(); i++) {
			E choice = choices.get(i);
			int[] card = cardAt(i);
			boolean over = mouseX >= card[0] && mouseX < card[0] + card[2] && mouseY >= card[1] && mouseY < card[1] + card[3] + LABEL;
			float lift = lifts.computeIfAbsent(choice, c -> Spring.bouncy(Spring.DEFAULT)).follow(ui, over ? 1f : 0f);
			float frame = frames.computeIfAbsent(choice, c -> Spring.bouncy(Spring.DEFAULT)).follow(ui, choice == chosen ? 1f : 0f);
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			// lift towards the mouse and grow slightly around the center
			float scale = 1f + 0.04f * lift;
			float cx = card[0] + card[2] / 2f;
			float cy = card[1] + card[3] / 2f;
			pose.translate(cx, cy - 2f * lift);
			pose.scale(scale, scale);
			pose.translate(-cx, -cy);
			ui.shadow(card[0], card[1] + 2, card[2], card[3], Radius.MD, 6, 0.25f + 0.15f * lift);
			Identifier picture = setting.previewOf(choice);
			if (picture != null) {
				// the images have their rounded corners baked in, so no mask is needed
				ui.graphics.blit(RenderPipelines.GUI_TEXTURED, picture, card[0], card[1], 0f, 0f, card[2], card[3], 320, 180, 320, 180, Ui.faded(0xFFFFFFFF));
			} else {
				ui.rounded(card[0], card[1], card[2], card[3], Radius.MD, ui.palette.control());
			}
			if (lift > 0.01f) {
				ui.rounded(card[0], card[1], card[2], card[3], Radius.MD, Ui.withOpacity(0xFFFFFFFF, 0.08f * lift));
			}
			// frame of the selected card: white, 2 px wide, 1 px outside the image
			if (frame > 0.01f) {
				ui.ring(card[0] - 2, card[1] - 2, card[2] + 4, card[3] + 4, Radius.MD, 2, Ui.withOpacity(0xFFFFFFFF, Math.min(1f, frame)));
			} else {
				ui.ring(card[0], card[1], card[2], card[3], Radius.MD, 1, Ui.withOpacity(0xFFFFFFFF, 0.12f + 0.25f * lift));
			}
			pose.popMatrix();
			String name = ui.fit(setting.nameOf(choice).getString(), card[2]);
			int color = choice == chosen ? ui.palette.text() : Ui.mix(ui.palette.textSecondary(), ui.palette.text(), lift);
			float textScale = TextScale.SMALL;
			float tw = ui.widthScaled(name, textScale);
			ui.textAtScaled(name, card[0] + (card[2] - tw) / 2f, card[1] + card[3] + 5f, textScale, color);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		List<E> choices = setting.choices();
		for (int i = 0; i < choices.size(); i++) {
			int[] card = cardAt(i);
			if (mouseX >= card[0] && mouseX < card[0] + card[2] && mouseY >= card[1] && mouseY < card[1] + card[3] + LABEL) {
				E choice = choices.get(i);
				if (choice != setting.get()) {
					final int index = i;
					setting.choose(choice, () -> {
						UiSounds.tick((float) index / Math.max(1, choices.size() - 1));
						onChanged.run();
					});
				}
				return true;
			}
		}
		return true;
	}
}
