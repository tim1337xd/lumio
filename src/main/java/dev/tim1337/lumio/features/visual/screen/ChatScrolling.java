package dev.tim1337.lumio.features.visual.screen;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.screen.chat.ChatScroll;
import dev.tim1337.lumio.mixin.accessor.ChatComponentAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;

/**
 * Smooth scrolling of the open chat. The game counts in whole lines, and goes on doing so: its counter is set to the whole line in view, and the part of the next line moves everything drawn by that
 * many pixels, inside the rows, with one row more. So what reads the game's counter (its clicks, the marks of a search) sees the same chat.
 */
final class ChatScrolling {
	/** How wide the scroll bar answers to the mouse from the panel's right edge, though it is drawn thinner. */
	private static final int BAR_GRAB_WIDTH = 6;

	private final ChatFeature chat;
	private final ChatScroll scroll = new ChatScroll();
	/** The game's counter as last seen or set: when it differs, the game has moved it itself. */
	private int written;
	/** How far everything drawn moves down in this frame, in GUI pixels. */
	private float shift;
	private boolean dragging;
	private float grab;

	ChatScrolling(ChatFeature chat) {
		this.chat = chat;
	}

	private static ChatComponent component() {
		return Minecraft.getInstance().gui.hud.getChat();
	}

	private static float most(ChatComponent component) {
		return Math.max(0, ((ChatComponentAccessor) component).lumio$lines().size() - component.getLinesPerPage());
	}

	/** Called before the chat draws: works out the place of this frame and gives the game its whole line. */
	void frame() {
		ChatComponent component = component();
		var access = (ChatComponentAccessor) component;
		int counted = access.lumio$scroll();
		if (counted != written) {
			scroll.set(counted);
		}
		scroll.step(System.nanoTime(), most(component));
		int line = scroll.line();
		if (line != counted) {
			access.lumio$setScroll(line);
			if (line == 0) {
				// back at the newest: the game's way of forgetting that there are new lines
				component.scrollChat(0);
			}
		}
		written = line;
		float part = scroll.part();
		if (part == 0f) {
			shift = 0f;
			return;
		}
		// on whole pixels of the screen: text between them would shimmer as it moves
		int pixels = Minecraft.getInstance().getWindow().getGuiScale();
		shift = Math.round(part * access.lumio$lineHeight() * chat.scale() * pixels) / (float) pixels;
	}

	private void go(float lines) {
		ChatComponent component = component();
		long now = System.nanoTime();
		float most = most(component);
		scroll.add(lines, most, now);
		if (!ModFeatures.MENU.animations()) {
			scroll.step(now + ChatScroll.EASE_NANOS, most);
		}
	}

	void wheel(double amount, boolean page) {
		go(ChatScroll.lines(amount, page, component().getLinesPerPage()));
	}

	/** @param direction 1 for older lines, -1 for newer */
	void page(int direction) {
		go(direction * ChatScroll.pageLines(component().getLinesPerPage()));
	}

	/** The game moved its counter: only new lines do that, keeping the view. Anything else is taken as it is. */
	void gameMoved(int lines) {
		int counted = ((ChatComponentAccessor) component()).lumio$scroll();
		if (lines > 0) {
			scroll.linesAdded(lines);
		} else if (lines < 0) {
			scroll.set(counted);
		}
		written = counted;
	}

	void gameReset() {
		scroll.set(0f);
		written = 0;
		shift = 0f;
		dragging = false;
	}

	/** Goes to a line at once, as a jump to a search match. */
	void jumpTo(int line) {
		scroll.set(line);
		written = ((ChatComponentAccessor) component()).lumio$scroll();
	}

	/** @return what the game takes for its counter when a line comes in: scrolled by a part of a line is scrolled too, or the view would move */
	int counterForNewLine(int counted) {
		return counted == 0 && scroll.awayFromNewest() ? 1 : counted;
	}

	/** @return how far everything drawn moves down in this frame, in GUI pixels */
	float shift() {
		return shift;
	}

	/** @return how many rows are drawn: one more while the part of a line is in view */
	int rows(int perPage) {
		return shift == 0f ? perPage : perPage + 1;
	}

	float position() {
		return scroll.position();
	}

	/**
	 * Cuts what is drawn at the rows while the lines stand between them.
	 *
	 * @return whether it was cut, to be ended with {@code disableScissor}
	 */
	boolean clip(GuiGraphicsExtractor graphics) {
		if (shift == 0f) {
			return false;
		}
		ChatComponent component = component();
		var access = (ChatComponentAccessor) component;
		float scale = chat.scale();
		int bottom = Mth.floor(chat.baseBottom(graphics.guiHeight()) / scale);
		int rows = component.getLinesPerPage() * Math.max(1, access.lumio$lineHeight());
		graphics.enableScissor(0, Math.round((bottom - rows) * scale + chat.moveY()), graphics.guiWidth(), Math.round(bottom * scale + chat.moveY()));
		return true;
	}

	/** @return whether a press was on the scroll bar: the chat then goes there directly */
	boolean press(double mouseX, double mouseY, int screenHeight) {
		ChatComponent component = component();
		var access = (ChatComponentAccessor) component;
		int total = access.lumio$lines().size();
		int perPage = component.getLinesPerPage();
		float scale = chat.scale();
		double across = (mouseX - chat.panelLeft()) / scale;
		int panelWidth = chat.panelWidthInChat();
		int track = perPage * Math.max(1, access.lumio$lineHeight());
		float up = fromBottom(mouseY, screenHeight);
		if (total <= perPage || across < panelWidth - BAR_GRAB_WIDTH || across > panelWidth || up < 0f || up > track) {
			return false;
		}
		int thumb = ChatDecor.thumbHeight(track, perPage, total);
		float most = most(component);
		float thumbBottom = ChatScroll.thumbFromBottom(scroll.position(), most, track, thumb);
		grab = up >= thumbBottom && up <= thumbBottom + thumb ? up - thumbBottom : thumb / 2f;
		dragging = true;
		drag(mouseY, screenHeight);
		return true;
	}

	/** @return whether the scroll bar is being dragged */
	boolean drag(double mouseY, int screenHeight) {
		if (!dragging) {
			return false;
		}
		ChatComponent component = component();
		var access = (ChatComponentAccessor) component;
		int total = access.lumio$lines().size();
		int perPage = component.getLinesPerPage();
		int track = perPage * Math.max(1, access.lumio$lineHeight());
		scroll.set(ChatScroll.placeAt(fromBottom(mouseY, screenHeight), grab, most(component), track, ChatDecor.thumbHeight(track, perPage, total)));
		return true;
	}

	void release() {
		dragging = false;
	}

	/** @return how far the mouse is above the lowest row, in the chat's pixels */
	private float fromBottom(double mouseY, int screenHeight) {
		float scale = chat.scale();
		return (float) (Mth.floor(chat.baseBottom(screenHeight) / scale) - (mouseY - chat.moveY()) / scale);
	}
}
