package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.ui.Confirm;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Swatch;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.controls.CardGridControl;
import dev.tim1337.lumio.ui.controls.DropdownControl;
import dev.tim1337.lumio.ui.controls.OptionCardsControl;
import dev.tim1337.lumio.ui.controls.RadioGroupControl;
import dev.tim1337.lumio.ui.controls.SegmentedControl;
import dev.tim1337.lumio.ui.controls.SwatchControl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** A choice between the constants of an enum, stored by name. */
public class EnumSetting<E extends Enum<E>> extends Setting<E> implements Choice<E> {
	/** How the choices are displayed. */
	public enum Style {
		/** Side by side in a segmented bar, for a few short choices. */
		SEGMENTED,
		/** A list of radio buttons, for choices that need some room. */
		RADIO,
		/** A dropdown menu, for many choices. */
		DROPDOWN,
		/** Colored circles. The constants must implement {@link Swatch}. */
		SWATCHES,
		/** A grid of cards with a preview image each, for choices made by appearance. */
		CARDS
	}

	private final Class<E> type;
	private final Function<E, Component> displayName;
	private final Style style;

	public EnumSetting(String key, Component label, E defaultValue, Function<E, Component> displayName) {
		this(key, label, defaultValue, displayName, Style.SEGMENTED);
	}

	public EnumSetting(String key, Component label, E defaultValue, Function<E, Component> displayName, Style style) {
		super(key, label, defaultValue);
		this.type = defaultValue.getDeclaringClass();
		this.displayName = displayName;
		this.style = style;
	}

	private Predicate<E> warnWhen;
	private String warnTitle = "";
	private String warnText = "";

	/** Choices matching the predicate show a warning first, which the player can cancel or accept. */
	public void warnOn(Predicate<E> when, String title, String text) {
		this.warnWhen = when;
		this.warnTitle = title;
		this.warnText = text;
	}

	/** Applies a choice made by the player, after the warning if it has one; {@code after} runs once the choice is applied. */
	public void choose(E value, Runnable after) {
		if (value == get()) {
			return;
		}
		if (warnWhen != null && warnWhen.test(value)) {
			Confirm.ask(warnTitle, warnText, "Continue", "Cancel", () -> {
				set(value);
				after.run();
			});
			return;
		}
		set(value);
		after.run();
	}

	private Function<E, Identifier> previews;

	/** Sets a preview image per choice for the card style: a 320x180 texture, or null for none. */
	public EnumSetting<E> withPreviews(Function<E, Identifier> previews) {
		this.previews = previews;
		return this;
	}

	/** @return the preview image of a choice, or null */
	public Identifier previewOf(E choice) {
		return previews == null ? null : previews.apply(choice);
	}

	/** A choice's state, such as "Working" or "Needs a token", shown as a chip in its color, or in the neutral color with -1. */
	public record OptionState(String text, int rgb) {
	}

	/** Draws a small picture of a choice into a box, such as a theme's look. */
	@FunctionalInterface
	public interface OptionPicture {
		void draw(Ui ui, int x, int y, int width, int height);
	}

	/**
	 * What a card shows for a choice: its icon on a tile in its color, what it is, and its current state (null for none). A card with a picture
	 * shows the picture instead of the icon.
	 */
	public record OptionCard(String icon, int rgb, String description, Supplier<OptionState> state, OptionPicture picture) {
		public OptionCard(String icon, int rgb, String description, Supplier<OptionState> state) {
			this(icon, rgb, description, state, null);
		}
	}

	private Function<E, OptionCard> optionCards;

	/** Shows the choices as cards with an icon, a description and a state each, for choices that need explaining, such as where music comes from. */
	public EnumSetting<E> withOptionCards(Function<E, OptionCard> cards) {
		this.optionCards = cards;
		return this;
	}

	private boolean optionCardsInGroup;

	/** Puts the cards inside the card of their group, as one of its rows, and not above it. */
	public EnumSetting<E> optionCardsInGroup() {
		optionCardsInGroup = true;
		return this;
	}

	public boolean hasOptionCardsInGroup() {
		return optionCardsInGroup;
	}

	/** @return the card of a choice */
	public OptionCard optionCardOf(E choice) {
		return optionCards.apply(choice);
	}

	public List<E> choices() {
		return List.of(type.getEnumConstants());
	}

	public Component nameOf(E choice) {
		return displayName.apply(choice);
	}

	@Override
	public String valueText() {
		return nameOf(get()).getString();
	}

	@Override
	public int valueSwatch() {
		return get() instanceof Swatch swatch ? swatch.rgb() : -1;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(get().name());
	}

	@Override
	public void load(JsonElement json) {
		if (json == null || !json.isJsonPrimitive()) {
			return;
		}
		for (E constant : type.getEnumConstants()) {
			if (constant.name().equals(json.getAsString())) {
				set(constant);
				return;
			}
		}
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public Control createControl(Runnable onChanged) {
		if (optionCards != null) {
			return new OptionCardsControl<>(this, onChanged);
		}
		return switch (style) {
			case SEGMENTED -> new SegmentedControl<>(this, onChanged);
			case RADIO -> new RadioGroupControl<>(this, onChanged);
			case DROPDOWN -> new DropdownControl<>(this, onChanged);
			case SWATCHES -> new SwatchControl(this, onChanged);
			case CARDS -> new CardGridControl<>(this, onChanged);
		};
	}
}
