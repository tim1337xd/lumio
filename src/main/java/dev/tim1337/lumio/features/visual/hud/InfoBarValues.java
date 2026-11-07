package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.InfoValueProvider;
import dev.tim1337.lumio.api.InfoValues;
import dev.tim1337.lumio.features.music.MusicFeature;
import dev.tim1337.lumio.features.music.MusicService;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.time.LocalTime;

/**
 * The values of the info bar segments: read a few times a second, with the text of a value built only when it changes, so the bar lays out text
 * only then. The measured numbers (frame rate, ping, server speed, memory and clicks per second) are read once per second, as the bar did
 * before its redesign, so they change calmly: the frame rate is the game's own count of the last second and changes only by a few percent at
 * least, the server speed and the ping are averaged. The frame rate line gets one point per second from the same value.
 */
final class InfoBarValues {
	/** Value states: fine, a warning, and bad (red, or orange for the server speed). */
	static final int FINE = 0;
	static final int WARN = 1;
	static final int BAD = 2;

	/** The clock, the area, the song and the like are read four times a second. */
	private static final long SAMPLE_NANOS = 250_000_000L;
	/** The measured numbers are read once per second. */
	private static final long METER_NANOS = 1_000_000_000L;
	/** The shown frame rate changes only by at least this much, or this share of it, so it does not flicker between close values. */
	private static final int FPS_LEAST_CHANGE = 3;
	private static final int FPS_CHANGE_SHARE = 20;
	/** The line of the frame rate covers ten seconds, one point per second. */
	static final int HISTORY = 10;
	static final float HISTORY_STEP_SECONDS = 1f;
	/** Server speed is unknown when the world time has not been received for this long. */
	private static final long TPS_STALE_MILLIS = 8000L;
	/** The highest ping shown, in milliseconds. */
	private static final int MAX_SHOWN_PING = 9999;
	/** The clicks of the last second are counted from a ring of their times. */
	private static final int CLICK_RING = 64;
	private static final long CLICK_WINDOW_NANOS = 1_000_000_000L;
	private static final int TICKS_PER_DAY = 24000;
	private static final int MINUTES_PER_DAY = 1440;
	private static final int MORNING_TICKS = 6000;
	private static final String[] FACINGS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	private final InfoBarFeature bar;
	/** Per segment: its value text, the small label after it, its state, and whether it has a value right now. */
	final String[] text = new String[InfoBarSegment.MOST];
	final String[] unit = new String[InfoBarSegment.MOST];
	final int[] level = new int[InfoBarSegment.MOST];
	final boolean[] available = new boolean[InfoBarSegment.MOST];
	/** Counts changes of any text, so the bar measures its texts again only then. */
	int textVersion;

	/** The frame rate line: the last values, oldest first from {@link #historyStart}, and when the last point was added. */
	final float[] history = new float[HISTORY];
	int historyStart;
	int historyCount;
	private long historyAt;
	private long lastSample;
	private long lastMeter;

	private int shownFps = -1;
	private int shownPing = Integer.MIN_VALUE;
	private int shownTps = Integer.MIN_VALUE;
	private int shownMinute = -1;
	private boolean shownTwelveHours;
	private int shownMemory = -1;
	private int shownCps = -1;
	private int shownX = Integer.MIN_VALUE;
	private int shownY = Integer.MIN_VALUE;
	private int shownZ = Integer.MIN_VALUE;
	private int shownFacing = -1;
	private boolean shownWithY;
	private String shownArea = "";
	/** Where the add-ons put the values of their segments; all of them are kept as fine. */
	private final InfoValues addonValues = new InfoValues() {
		@Override
		public void set(String segment, String value) {
			InfoBarSegment known = InfoBarSegment.byId(segment);
			if (known != null) {
				InfoBarValues.this.set(known, value, FINE);
			}
		}

		@Override
		public void none(String segment) {
			InfoBarSegment known = InfoBarSegment.byId(segment);
			if (known != null) {
				InfoBarValues.this.none(known);
			}
		}
	};
	/** When a lyric line was last on screen, for the song shown only without lyrics. */
	private long lyricSeenAt;

	InfoBarValues(InfoBarFeature bar) {
		this.bar = bar;
		for (int i = 0; i < text.length; i++) {
			text[i] = "";
			unit[i] = "";
		}
		unit[InfoBarSegment.FPS.ordinal()] = "FPS";
		unit[InfoBarSegment.PING.ordinal()] = "MS";
		unit[InfoBarSegment.TPS.ordinal()] = "TPS";
		unit[InfoBarSegment.MEMORY.ordinal()] = "MEM";
		unit[InfoBarSegment.CPS.ordinal()] = "CPS";
	}

	/** @return seconds since the last point of the frame rate line, for the line to scroll smoothly between points */
	float sinceHistoryStep() {
		return Math.min(HISTORY_STEP_SECONDS, (System.nanoTime() - historyAt) / 1_000_000_000f);
	}

	/**
	 * Reads the values when it is time.
	 *
	 * @param preview whether the bar is drawn in the menu without a world
	 * @param measurePing whether the ping may be measured: only for the bar in the game, not while only the settings preview shows it, which
	 *                    shows the last known ping instead
	 */
	void update(Minecraft client, boolean preview, boolean measurePing) {
		if (measurePing) {
			// every frame of the bar in the game, apart from the meter interval below, which the preview shares: the meter itself sends at most
			// one request every few seconds
			sendPingIfDue(client);
		}
		long now = System.nanoTime();
		if (now - lastMeter >= METER_NANOS || lastMeter == 0L) {
			lastMeter = now;
			readFps(client, now);
			readPing(client, preview);
			readTps(preview);
			readMemory();
			readCps(now);
		}
		if (now - lastSample < SAMPLE_NANOS) {
			return;
		}
		lastSample = now;
		readClock(client);
		readArea(client, preview);
		readSong(now);
		readCoords(client, preview);
		readAddons();
	}

	/** Adds the frame rate shown now as the newest point of the line. */
	private void addPoint(int fps, long now) {
		historyAt = now;
		if (historyCount < HISTORY) {
			history[(historyStart + historyCount++) % HISTORY] = fps;
		} else {
			history[historyStart] = fps;
			historyStart = (historyStart + 1) % HISTORY;
		}
	}

	/** @return the frame rate value at a point of the line, 0 the oldest */
	float historyAt(int index) {
		return history[(historyStart + index) % HISTORY];
	}

	private void set(InfoBarSegment segment, String value, int state) {
		int i = segment.ordinal();
		if (!value.equals(text[i])) {
			text[i] = value;
			textVersion++;
		}
		level[i] = state;
		available[i] = true;
	}

	private void none(InfoBarSegment segment) {
		available[segment.ordinal()] = false;
	}

	private void readClock(Minecraft client) {
		boolean twelve = !bar.hours24();
		int minutes;
		if (bar.gameClock() && client.level != null) {
			long ticks = (client.level.getOverworldClockTime() + MORNING_TICKS) % TICKS_PER_DAY;
			minutes = (int) (ticks * MINUTES_PER_DAY / TICKS_PER_DAY);
		} else {
			LocalTime now = LocalTime.now();
			minutes = now.getHour() * 60 + now.getMinute();
		}
		if (minutes != shownMinute || twelve != shownTwelveHours) {
			shownMinute = minutes;
			shownTwelveHours = twelve;
			set(InfoBarSegment.CLOCK, clockText(minutes / 60, minutes % 60, twelve), FINE);
		}
		available[InfoBarSegment.CLOCK.ordinal()] = true;
	}

	private static String clockText(int hour, int minute, boolean twelve) {
		if (!twelve) {
			return (hour < 10 ? "0" : "") + hour + (minute < 10 ? ":0" : ":") + minute;
		}
		int h = hour % 12 == 0 ? 12 : hour % 12;
		return h + (minute < 10 ? ":0" : ":") + minute + (hour < 12 ? " AM" : " PM");
	}

	/** The game's own frame count of the last second; the shown value changes only when it moved by a few frames or percent. */
	private void readFps(Minecraft client, long now) {
		int fps = client.getFps();
		if (shownFps < 0 || Math.abs(fps - shownFps) >= Math.max(FPS_LEAST_CHANGE, shownFps / FPS_CHANGE_SHARE)) {
			shownFps = fps;
			set(InfoBarSegment.FPS, Integer.toString(fps), FINE);
		}
		int state = shownFps < bar.fpsRed() ? BAD : shownFps < bar.fpsYellow() ? WARN : FINE;
		addPoint(shownFps, now);
		level[InfoBarSegment.FPS.ordinal()] = state;
		available[InfoBarSegment.FPS.ordinal()] = true;
	}

	/** @return the frame rate shown now, for the line */
	float fpsNow() {
		return Math.max(0, shownFps);
	}

	/** Sends the game's ping request when one is due: only on a server and while the ping is shown in the bar. */
	private void sendPingIfDue(Minecraft client) {
		var connection = client.getConnection();
		if (connection == null || client.player == null || client.hasSingleplayerServer() || !bar.showsSegment(InfoBarSegment.PING)) {
			return;
		}
		long now = Util.getMillis();
		if (PING.due(connection, client.level, now)) {
			connection.send(new ServerboundPingRequestPacket(now));
		}
	}

	private void readPing(Minecraft client, boolean preview) {
		var connection = client.getConnection();
		if (connection == null || client.player == null) {
			PING.reset();
			shownPing = Integer.MIN_VALUE;
			if (preview) {
				set(InfoBarSegment.PING, "32", FINE);
			} else {
				none(InfoBarSegment.PING);
			}
			return;
		}
		if (client.hasSingleplayerServer() || !bar.showsSegment(InfoBarSegment.PING)) {
			// measured only on a server and while the ping is shown
			PING.reset();
			shownPing = Integer.MIN_VALUE;
			none(InfoBarSegment.PING);
			return;
		}
		int ping = PING.average();
		if (ping == PingMeter.NONE) {
			// a moment after joining, before the first answer
			shownPing = Integer.MIN_VALUE;
			none(InfoBarSegment.PING);
			return;
		}
		ping = Math.min(ping, MAX_SHOWN_PING);
		int state = ping >= bar.pingRed() ? BAD : ping >= bar.pingYellow() ? WARN : FINE;
		if (ping != shownPing) {
			shownPing = ping;
			set(InfoBarSegment.PING, Integer.toString(ping), state);
		}
		level[InfoBarSegment.PING.ordinal()] = state;
		available[InfoBarSegment.PING.ordinal()] = true;
	}

	/** @return the ping shown now in milliseconds, for the signal bars */
	int ping() {
		return Math.max(0, shownPing);
	}

	private void readTps(boolean preview) {
		boolean known = serverTps >= 0f && System.currentTimeMillis() - lastServerUpdate <= TPS_STALE_MILLIS;
		if (!known) {
			if (preview) {
				shownTps = Integer.MIN_VALUE;
				set(InfoBarSegment.TPS, "20.0", FINE);
			} else if (Minecraft.getInstance().getConnection() == null) {
				none(InfoBarSegment.TPS);
			} else {
				if (shownTps != -1) {
					shownTps = -1;
					set(InfoBarSegment.TPS, "--", FINE);
				}
				level[InfoBarSegment.TPS.ordinal()] = FINE;
				available[InfoBarSegment.TPS.ordinal()] = true;
			}
			return;
		}
		int tenths = Math.round(serverTps * 10f);
		float tps = tenths / 10f;
		int state = tps < bar.tpsOrange() ? BAD : tps < bar.tpsYellow() ? WARN : FINE;
		if (tenths != shownTps) {
			shownTps = tenths;
			set(InfoBarSegment.TPS, tenths / 10 + "." + tenths % 10, state);
		}
		level[InfoBarSegment.TPS.ordinal()] = state;
		available[InfoBarSegment.TPS.ordinal()] = true;
	}

	private void readArea(Minecraft client, boolean preview) {
		// an add-on that knows the world names the place (a SkyBlock island); otherwise the server or the world
		String area = null;
		for (InfoValueProvider provider : Addons.infoValues()) {
			area = provider.area();
			if (area != null) {
				break;
			}
		}
		if (area != null) {
			// named by the add-on
		} else if (client.getCurrentServer() != null && client.getCurrentServer().ip != null) {
			area = client.getCurrentServer().ip;
		} else if (client.getSingleplayerServer() != null) {
			area = client.getSingleplayerServer().getWorldData().getLevelName();
		} else {
			area = preview ? "Hub" : "";
		}
		if (area.isEmpty()) {
			none(InfoBarSegment.AREA);
			return;
		}
		if (!area.equals(shownArea)) {
			shownArea = area;
			set(InfoBarSegment.AREA, area, FINE);
		}
		available[InfoBarSegment.AREA.ordinal()] = true;
	}

	/** How long no lyric line has to be on screen before the song shows again, so it does not flicker between two lines. */
	private static final long SONG_RETURN_NANOS = 2_000_000_000L;

	private void readSong(long now) {
		MusicFeature music = ModFeatures.MUSIC;
		MusicService.Track track = music.isEnabled() ? MusicService.track() : null;
		// with the lyrics in the bar, the segment shows the song and its lines in one fixed width as long as a song is there, also paused
		if (track != null && music.barLyricsShown()) {
			set(InfoBarSegment.SONG, track.title(), FINE);
			return;
		}
		if (track == null || !MusicService.isPlaying()) {
			none(InfoBarSegment.SONG);
			return;
		}
		if (music.lyricLineShown()) {
			lyricSeenAt = now;
		}
		if (bar.songOnlyWithoutLyrics() && lyricSeenAt != 0L && now - lyricSeenAt < SONG_RETURN_NANOS) {
			none(InfoBarSegment.SONG);
			return;
		}
		set(InfoBarSegment.SONG, track.title(), FINE);
	}

	private void readCoords(Minecraft client, boolean preview) {
		if (client.player == null) {
			if (preview) {
				shownX = Integer.MIN_VALUE;
				set(InfoBarSegment.COORDS, bar.coordsWithY() ? "128 72 -304" : "128 -304", FINE);
				unit[InfoBarSegment.COORDS.ordinal()] = bar.facing() ? "NE" : "";
			} else {
				none(InfoBarSegment.COORDS);
			}
			return;
		}
		int x = Mth.floor(client.player.getX());
		int y = Mth.floor(client.player.getY());
		int z = Mth.floor(client.player.getZ());
		boolean withY = bar.coordsWithY();
		int facing = bar.facing() ? Math.floorMod(Math.round(Mth.wrapDegrees(client.player.getYRot()) / 45f), FACINGS.length) : -1;
		if (x != shownX || y != shownY || z != shownZ || withY != shownWithY || facing != shownFacing) {
			shownX = x;
			shownY = y;
			shownZ = z;
			shownWithY = withY;
			shownFacing = facing;
			unit[InfoBarSegment.COORDS.ordinal()] = facing < 0 ? "" : FACINGS[facing];
			set(InfoBarSegment.COORDS, withY ? x + " " + y + " " + z : x + " " + z, FINE);
			textVersion++;
		}
		available[InfoBarSegment.COORDS.ordinal()] = true;
	}

	/** The segments the add-ons fill (the SkyBlock date, purse, bits, copper and mayor). */
	private void readAddons() {
		for (InfoValueProvider provider : Addons.infoValues()) {
			provider.read(addonValues);
		}
	}

	private void readMemory() {
		Runtime runtime = Runtime.getRuntime();
		long used = runtime.totalMemory() - runtime.freeMemory();
		int percent = (int) (used * 100L / Math.max(1L, runtime.maxMemory()));
		if (percent != shownMemory) {
			shownMemory = percent;
			set(InfoBarSegment.MEMORY, percent + "%", FINE);
		}
		available[InfoBarSegment.MEMORY.ordinal()] = true;
	}

	private void readCps(long now) {
		int count = 0;
		for (long at : CLICKS) {
			if (at != 0L && now - at < CLICK_WINDOW_NANOS) {
				count++;
			}
		}
		if (count != shownCps) {
			shownCps = count;
			set(InfoBarSegment.CPS, Integer.toString(count), FINE);
		}
		available[InfoBarSegment.CPS.ordinal()] = true;
	}

	// ------------------------------------------------------------------------------------------------- clicks

	private static final long[] CLICKS = new long[CLICK_RING];
	private static int nextClick;

	/** A mouse button was pressed in game (no screen open): counted for the clicks per second. Called on the main thread. */
	static void click() {
		CLICKS[nextClick] = System.nanoTime();
		nextClick = (nextClick + 1) % CLICK_RING;
	}

	// ------------------------------------------------------------------------------------------------- server speed

	/** Server ticks per second, derived from how the world time it sends advances, or -1 if unknown. */
	private static volatile float serverTps = -1f;
	private static long lastServerGameTime = -1;
	private static long lastServerNanos;
	private static volatile long lastServerUpdate;
	/** The world time is only measured over at least this long, since packets arrive unevenly. */
	private static final double TPS_MIN_SECONDS = 0.4;
	private static final float TPS_SMOOTHING = 0.4f;
	private static final float MAX_TPS = 20f;

	/** Called when the server sends the world time: the world time advanced over the real time elapsed since the last packet gives the TPS. */
	static synchronized void onServerTime(long gameTime) {
		long now = System.nanoTime();
		if (lastServerGameTime >= 0 && gameTime > lastServerGameTime) {
			double seconds = (now - lastServerNanos) / 1_000_000_000.0;
			if (seconds > TPS_MIN_SECONDS) {
				float measured = (float) Math.min(MAX_TPS, (gameTime - lastServerGameTime) / seconds);
				serverTps = serverTps < 0f ? measured : serverTps + (measured - serverTps) * TPS_SMOOTHING;
			}
		}
		if (lastServerGameTime < 0 || gameTime != lastServerGameTime) {
			lastServerGameTime = gameTime;
			lastServerNanos = now;
		}
		lastServerUpdate = System.currentTimeMillis();
	}

	// ------------------------------------------------------------------------------------------------- ping

	/**
	 * The ping, measured with the game's own ping request (see {@link PingMeter}): the one packet the mod sends on its own, only on a server and
	 * while the ping is shown. The latency in the player list is not used, since many servers (Hypixel among them) report a fixed value there.
	 */
	private static final PingMeter PING = new PingMeter();

	/** An answer to a ping request arrived, on the network thread. */
	static void onPong(long timestamp) {
		PING.answered(timestamp, Util.getMillis());
	}
}
