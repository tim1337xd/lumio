package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.HttpLimits;
import dev.tim1337.lumio.ui.Toasts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Detects the currently playing song from several sources: Cider (an Apple Music client with a local HTTP API), Spotify's Web API, and the
 * Windows media session that the Apple Music app and most other players report to. It caches the track, the cover and, if requested, the
 * lyrics.
 * <p>
 * All slow work runs on background threads; the rest of the mod only reads the cached state.
 */
public final class MusicService {
	public record Track(String title, String artist, String album, long durationMs, String artworkUrl, String id) {
		String key() {
			return title + "\u0001" + artist + "\u0001" + album;
		}
	}

	/** A word of a line with timing, when the player provides it: its character range within the line, and its start and end time. */
	public record Word(int from, int to, long startMs, long endMs) {
	}

	/**
	 * Background vocals sung alongside a line: ad-libs and responses such as "(ooh)". Apple Music marks them in its lyrics, with word timings
	 * separate from the line's.
	 */
	public record Background(String text, List<Word> words, long startMs, long endMs) {
	}

	/** The singer of a line, when the lyrics mark two: the first, the second, or both together. Unmarked lines count as the first. */
	public static final int SINGER_ONE = 0;
	public static final int SINGER_TWO = 1;
	public static final int SINGER_BOTH = 2;

	/**
	 * A line of the lyrics. A line whose text is blank but has background vocals is an adlib sung on its own, in a gap between lines.
	 *
	 * @param backgrounds background vocals (adlibs) sung with the line, each with its own timing
	 * @param singer      who sings it, see {@link #SINGER_ONE}
	 */
	public record LyricLine(long timeMs, String text, List<Word> words, List<Background> backgrounds, int singer) {
		public LyricLine(long timeMs, String text, List<Word> words, Background background) {
			this(timeMs, text, words, background == null ? List.of() : List.of(background), SINGER_ONE);
		}

		public LyricLine(long timeMs, String text, List<Word> words) {
			this(timeMs, text, words, List.of(), SINGER_ONE);
		}

		public LyricLine(long timeMs, String text) {
			this(timeMs, text, List.of(), List.of(), SINGER_ONE);
		}

		/** @return the first background vocal, or null */
		public Background background() {
			return backgrounds.isEmpty() ? null : backgrounds.get(0);
		}

		/** @return whether the line is only an adlib, sung without a main line */
		public boolean adlibOnly() {
			return text.isBlank() && !backgrounds.isEmpty();
		}
	}

	public enum Source {
		AUTO, CIDER, WINDOWS, SPOTIFY
	}

	private static final String CIDER = "http://localhost:10767/api/v1/playback/";
	private static final String LYRICS = "https://lrclib.net/api/";
	/** A difference between the extrapolated and the reported position above this is a seek, not a late response. */
	private static final long SEEK_MS = 700;

	private static volatile Track track;
	private static volatile boolean playing;
	private static volatile long positionMs;
	private static volatile long positionStamp;
	private static volatile List<LyricLine> lyrics = List.of();
	/** The lyrics last checked for word timings, and whether they have them; see {@link #hasWordTiming()}. */
	private static List<LyricLine> timedFrom;
	private static boolean timedWords;
	/** The cover of the current song, prepared off the render thread and waiting to become a texture. */
	private static final AtomicReference<CoverImages.Prepared> PENDING_COVER = new AtomicReference<>();
	/** Side of the cover asked for from the players and services: enough for its largest place at high GUI scales, times two for sharpness. */
	static final int COVER_REQUEST = 600;
	private static volatile int trackNumber;
	private static volatile String status = "";

	private static Thread poller;
	private static volatile boolean running;
	private static volatile Source source = Source.AUTO;
	private static volatile boolean lyricsWanted = true;
	private static volatile boolean coversWanted = true;
	/** For players that report no usable position: the playing time is counted locally. */
	private static boolean selfTimed;
	private static long selfTimerMs;
	private static long selfStamp;
	private static volatile String ciderToken = "";
	/** How long the Windows media session counts as current after it last reported something. */
	private static final long WINDOWS_FRESH_MILLIS = 4000;
	private static volatile long windowsSeen;
	private static volatile String lastCoverKey = "";
	private static volatile String lyricsKey = "";

	private MusicService() {
	}

	// ----------------------------------------------------------------------------------------------------- cached state

	public static Track track() {
		return track;
	}

	/** @return a counter incremented on every song change, for animations */
	public static int trackNumber() {
		return trackNumber;
	}

	public static boolean isPlaying() {
		return playing && track != null;
	}

	/** @return the playback position in milliseconds, extrapolated from the last report */
	public static long position() {
		Track current = track;
		if (current == null) {
			return 0;
		}
		long value = positionMs;
		if (playing) {
			value += (System.nanoTime() - positionStamp) / 1_000_000L;
		}
		return current.durationMs() > 0 ? Math.min(value, current.durationMs()) : value;
	}

	private static volatile boolean safe;
	private static List<LyricLine> safeFrom;
	private static List<LyricLine> safeLines = List.of();
	/** Basic slurs hidden by the "Censor slurs" option, matched as whole words or as word stems with suffixes. */
	private static final Pattern SLURS = Pattern.compile("\\b(?:nigg\\w*|fagg?(?:ot|ots|it|its)?|retard(?:ed|s)?|tranny|trannies|kikes?|chinks?|spics?|wetbacks?|gooks?|dykes?|coons?)\\b", Pattern.CASE_INSENSITIVE);

	/** Enables censoring: slurs in the lyrics are shown as asterisks. */
	public static void setSafe(boolean on) {
		safe = on;
	}

	private static String masked(String text) {
		Matcher matcher = SLURS.matcher(text);
		if (!matcher.find()) {
			return text;
		}
		// one asterisk per character, so word positions within the line stay valid
		return matcher.replaceAll(match -> "*".repeat(match.group().length()));
	}

	private static volatile boolean bracketAdlibs = true;
	private static boolean shownSafe;
	private static boolean shownBrackets;

	/** Enables adlibs from brackets: in lyrics that mark no background vocals, text in round brackets standing on its own is one. */
	public static void setBracketAdlibs(boolean on) {
		bracketAdlibs = on;
	}

	/**
	 * @return the lyrics as shown: adlibs taken from brackets and slurs censored, if enabled, and a break line (see {@link LyricBreaks}) in the intro
	 *         and in every long gap; built once per change, then the same list
	 */
	public static List<LyricLine> lyrics() {
		List<LyricLine> raw = lyrics;
		boolean censor = safe;
		boolean brackets = bracketAdlibs;
		if (safeFrom != raw || shownSafe != censor || shownBrackets != brackets) {
			List<LyricLine> lines = brackets ? LyricAdlibs.fromBrackets(raw) : raw;
			if (censor) {
				List<LyricLine> out = new ArrayList<>(lines.size());
				for (LyricLine line : lines) {
					List<Background> backgrounds = new ArrayList<>(line.backgrounds().size());
					for (Background background : line.backgrounds()) {
						backgrounds.add(new Background(masked(background.text()), background.words(), background.startMs(), background.endMs()));
					}
					out.add(new LyricLine(line.timeMs(), masked(line.text()), line.words(), List.copyOf(backgrounds), line.singer()));
				}
				lines = out;
			}
			safeLines = LyricBreaks.withBreaks(List.copyOf(lines));
			safeFrom = raw;
			shownSafe = censor;
			shownBrackets = brackets;
		}
		return safeLines;
	}

	/**
	 * @return whether the current lyrics have word (or syllable) timings, which always light up word by word; checked once per change of the
	 *         lyrics, on the render thread
	 */
	public static boolean hasWordTiming() {
		List<LyricLine> raw = lyrics;
		if (raw != timedFrom) {
			boolean found = false;
			for (LyricLine line : raw) {
				if (!line.words().isEmpty()) {
					found = true;
					break;
				}
			}
			timedWords = found;
			timedFrom = raw;
		}
		return timedWords;
	}

	/** @return the lyric line sung at the given time, with word timings if known, or null */
	public static LyricLine lyricLineAt(long timeMs) {
		LyricLine found = null;
		for (LyricLine line : lyrics()) {
			if (line.timeMs() <= timeMs) {
				// an adlib on its own does not end the line before it
				if (!line.adlibOnly()) {
					found = line;
				}
			} else {
				break;
			}
		}
		return found == null || found.text().isBlank() ? null : found;
	}

	/** @return the text of the lyric line sung at the given time, or null */
	public static String lyricAt(long timeMs) {
		List<LyricLine> lines = lyrics();
		String found = null;
		for (LyricLine line : lines) {
			if (line.timeMs() <= timeMs) {
				if (!line.adlibOnly()) {
					found = line.text();
				}
			} else {
				break;
			}
		}
		return found == null || found.isBlank() ? null : found;
	}

	/** @return progress through the sung line from 0 to 1, used to highlight words as they are sung */
	public static float lyricProgress(long timeMs) {
		List<LyricLine> lines = lyrics();
		int index = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).timeMs() <= timeMs) {
				if (!lines.get(i).adlibOnly()) {
					index = i;
				}
			} else {
				break;
			}
		}
		if (index < 0) {
			return 0f;
		}
		long start = lines.get(index).timeMs();
		int next = index + 1;
		while (next < lines.size() && lines.get(next).adlibOnly()) {
			next++;
		}
		long end = next < lines.size() ? lines.get(next).timeMs() : start + 4000;
		// a long pause before the next line does not count as singing this line
		long length = Math.max(400, Math.min(end - start, 6500));
		return Math.max(0f, Math.min(1f, (timeMs - start) / (float) length));
	}

	/** @return the cover found since the last call, or null; the caller owns its images */
	static CoverImages.Prepared takeCover() {
		return PENDING_COVER.getAndSet(null);
	}

	/** @return whether a cover is waiting to be taken */
	public static boolean hasPendingOrCover() {
		return PENDING_COVER.get() != null;
	}

	/** Offers a cover (or none), freeing a waiting one that was never taken. */
	private static void offerCover(CoverImages.Prepared cover) {
		CoverImages.Prepared old = PENDING_COVER.getAndSet(cover);
		if (old != null) {
			old.close();
		}
	}

	public static String status() {
		return status;
	}

	private static volatile String windowsApp = "";
	/** Origin of the current lyrics: "Searching...", the provider name, or empty if none were found. */
	private static volatile String lyricsOrigin = "";

	/** @return a description of the current song source, for the menu */
	public static String sourceDescription() {
		if (track == null) {
			return status.isEmpty() ? "Nothing is playing" : status;
		}
		return switch (lastFrom) {
			case CIDER -> "Cider (port 10767)";
			case SPOTIFY -> "Spotify (Web API)";
			case WINDOWS -> "Windows media" + (windowsApp.isEmpty() ? "" : " (" + windowsApp + ")");
		};
	}

	/** @return whether a song plays from the source, for the state of the source cards in the menu; any source counts for automatic */
	static boolean playsFrom(Source wanted) {
		if (track == null) {
			return false;
		}
		From from = lastFrom;
		return switch (wanted) {
			case AUTO -> true;
			case CIDER -> from == From.CIDER;
			case SPOTIFY -> from == From.SPOTIFY;
			case WINDOWS -> from == From.WINDOWS;
		};
	}

	/** @return a description of the current lyrics for the menu: provider, line count and timing precision */
	public static String lyricsDescription() {
		if (track == null) {
			return "No song";
		}
		List<LyricLine> lines = lyrics();
		if (lines.isEmpty()) {
			return lyricsOrigin.isEmpty() ? "None found" : lyricsOrigin;
		}
		boolean words = lines.stream().anyMatch(line -> !line.words().isEmpty());
		return lyricsOrigin + ", " + lines.size() + " lines, timed " + (words ? "word by word" : "line by line");
	}

	/**
	 * @return a short note on the current lyrics for a chip in the menu: how they are synced, and where they come from if asked for, such as
	 *         "Lyrics · word by word · LRCLIB"; empty without a song
	 */
	public static String lyricsChip(boolean withOrigin) {
		if (track == null) {
			return "";
		}
		List<LyricLine> lines = lyrics();
		if (lines.isEmpty()) {
			return lyricsOrigin.startsWith("Searching") ? "Lyrics · searching" : "No lyrics";
		}
		boolean words = false;
		for (LyricLine line : lines) {
			words |= !line.words().isEmpty();
		}
		String sync = words ? "Lyrics · word by word" : "Lyrics · line by line";
		return withOrigin && !lyricsOrigin.isEmpty() ? sync + " · " + lyricsOrigin : sync;
	}

	/** Sets a song manually, for tests and for previewing the feature. */
	public static void demo(Track demoTrack, List<LyricLine> demoLyrics, long position, boolean isPlaying) {
		track = demoTrack;
		trackNumber++;
		lyrics = demoLyrics;
		positionMs = position;
		positionStamp = System.nanoTime();
		playing = isPlaying;
	}

	// ----------------------------------------------------------------------------------------------------- lifecycle

	/** Starts detection, or reconfigures it, according to the settings. Cheap enough to call every frame. */
	public static synchronized void run(Source wanted, boolean withLyrics, boolean withOnlineCovers, String token) {
		source = wanted;
		lyricsWanted = withLyrics;
		coversWanted = withOnlineCovers;
		ciderToken = token == null ? "" : token.trim();
		if (running) {
			return;
		}
		running = true;
		poller = new Thread(MusicService::loop, "Lumio music");
		poller.setDaemon(true);
		poller.start();
	}

	/** @return whether any part of the service is running: the poller or the Windows media reader */
	public static boolean active() {
		return running || poller != null || WindowsMedia.running();
	}

	public static synchronized void stop() {
		running = false;
		WindowsMedia.stop();
		if (poller != null) {
			poller.interrupt();
			poller = null;
		}
		track = null;
		playing = false;
	}

	private static volatile long lostSince;

	/**
	 * The player reports nothing about the song: it may be paused, and some players go silent then. The song is kept as paused and only cleared
	 * after a long silence.
	 */
	private static void lose() {
		Track current = track;
		if (current == null) {
			return;
		}
		if (playing) {
			positionMs = position();
			positionStamp = System.nanoTime();
			playing = false;
			lostSince = System.currentTimeMillis();
		} else if (lostSince > 0 && System.currentTimeMillis() - lostSince > 300_000L) {
			track = null;
			lostSince = 0;
		}
	}

	private static void loop() {
		long ciderRetry = 0;
		while (running) {
			try {
				boolean found = false;
				Source wanted = source;
				if ((wanted == Source.AUTO || wanted == Source.CIDER) && (System.currentTimeMillis() >= ciderRetry || retryCider)) {
					retryCider = false;
					found = pollCider();
					if (!found && wanted == Source.AUTO) {
						// Cider is not running: back off before asking again
						ciderRetry = System.currentTimeMillis() + 6000;
					}
				}
				if (!found && (wanted == Source.SPOTIFY || wanted == Source.AUTO && SpotifyClient.connected())) {
					found = pollSpotify();
					if (!found && wanted == Source.SPOTIFY && track != null && lastFrom == From.SPOTIFY) {
						lose();
					}
				}
				if (found && wanted == Source.AUTO && WindowsMedia.running()) {
					// keep polling the Windows session, so a paused Cider or Spotify does not hide another player that is playing
					pollWindows();
				}
				if (!found && wanted != Source.CIDER && wanted != Source.SPOTIFY) {
					ensureWindows();
					pollWindows();
					// the reader's reports arrive on its own thread; after a long silence nothing is playing
					if (System.currentTimeMillis() - windowsSeen > WINDOWS_FRESH_MILLIS && track != null && lastFrom == From.WINDOWS) {
						lose();
					}
				}
				if (wanted == Source.CIDER || wanted == Source.SPOTIFY) {
					// the Windows reader may still be running from a previous source selection: it must not override the chosen player
					WindowsMedia.stop();
				}
				if (wanted == Source.CIDER && !found) {
					status = "Cider is not answering on port 10767";
					if (track != null && lastFrom == From.CIDER) {
						lose();
					}
				}
				Thread.sleep(wanted == Source.WINDOWS ? 1500 : 800);
			} catch (InterruptedException e) {
				return;
			} catch (Exception e) {
				status = String.valueOf(e.getMessage());
			}
		}
	}

	private enum From {
		CIDER, WINDOWS, SPOTIFY
	}

	private static volatile From lastFrom = From.WINDOWS;
	/** After a command from the player controls the shown state holds until then ({@link System#nanoTime()}), see {@link #update}. */
	private static volatile long heldUntil;
	private static final long HOLD_NANOS = 1_500_000_000L;
	private static volatile boolean windowsCanNext = true;
	private static volatile boolean windowsCanPrevious = true;
	private static volatile boolean windowsCanSeek = true;

	/** Applies a player report. A different song than before triggers lyrics and cover lookups. */
	private static void update(From from, Track reported, boolean isPlaying, long position) {
		update(from, reported, isPlaying, position, System.nanoTime());
	}

	/**
	 * @param sampledAt {@link System#nanoTime()} at which the reported position was valid: players are queried over IPC/network, so the answer
	 *                  is older than its arrival, and lyrics timed from the arrival would lag
	 */
	private static synchronized void update(From from, Track reported, boolean isPlaying, long position, long sampledAt) {
		lastFrom = from;
		lostSince = 0;
		Track before = track;
		boolean changed = before == null || !before.key().equals(reported.key());
		long now = System.nanoTime();
		// some players report no duration and a nonsensical position: then the playing time is counted locally
		boolean unknown = reported.durationMs() <= 0 || position < 0 || position > 12 * 3600_000L;
		if (unknown) {
			if (changed || !selfTimed) {
				selfTimerMs = 0;
			} else if (playing) {
				selfTimerMs += (now - selfStamp) / 1_000_000L;
			}
			selfStamp = now;
			position = selfTimerMs;
		}
		selfTimed = unknown;
		if (unknown) {
			sampledAt = now;
		}
		// The position is extrapolated from the sample. Players report in steps and polls take varying time, so adopting each report as is makes the
		// lyrics jitter back and forth; small differences from the extrapolation are only partially applied, large ones (a seek) immediately.
		if (!changed && !unknown && playing && isPlaying) {
			long counted = positionMs + (sampledAt - positionStamp) / 1_000_000L;
			long drift = position - counted;
			if (Math.abs(drift) < SEEK_MS) {
				position = counted + Math.round(drift * 0.3);
			}
		}
		if (!changed && now < heldUntil) {
			// a report sent before the player took the last command would undo what is shown, so the shown state holds a moment; the first
			// report after that corrects it, if the command failed
			isPlaying = playing;
			position = playing ? positionMs + (now - positionStamp) / 1_000_000L : positionMs;
			sampledAt = now;
		}
		track = reported;
		playing = isPlaying;
		positionMs = position;
		positionStamp = sampledAt;
		if (changed) {
			trackNumber++;
			lyrics = List.of();
			// a pending cover of the previous song does not belong to this one
			offerCover(null);
			if (lyricsWanted) {
				fetchLyrics(reported);
			}
			if (coversWanted && reported.artworkUrl().isEmpty()) {
				lookUpCover(reported);
			}
		} else if (lyricsWanted && lyrics.isEmpty() && !reported.key().equals(lyricsKey)) {
			fetchLyrics(reported);
		}
	}

	// ----------------------------------------------------------------------------------------------------- Spotify

	private static volatile long spotifyNext;
	private static volatile long spotifySeen;
	private static volatile boolean spotifyOk;

	public static void setSpotifyClientId(String id) {
		SpotifyClient.setClientId(id);
	}

	/** What a check of Cider found: it is not running, it runs but wants a (different) token, or the token works. */
	public enum CiderState {
		NOT_RUNNING, NEEDS_TOKEN, CONNECTED
	}

	/** The result of a check of Cider, with the song it is playing (empty texts for none). */
	public record CiderCheck(CiderState state, String title, String artist) {
	}

	/** Whether Cider answered the last time it was asked; the music page shows when it stopped. */
	private static volatile boolean ciderRunning = true;
	/** Set to ask Cider again right away, without waiting for the pause after it did not answer. */
	private static volatile boolean retryCider;
	private static final ExecutorService CHECKS = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Lumio source check");
		thread.setDaemon(true);
		return thread;
	});

	/** @return whether Cider answered the last time it was asked */
	public static boolean ciderRunning() {
		return ciderRunning;
	}

	/** Asks Cider again right away, for the Retry button. */
	public static void retryCider() {
		retryCider = true;
	}

	/**
	 * Asks Cider once with a token, on a thread of its own, at its usual address on this computer: whether it runs, whether it takes the token,
	 * and what it plays. The token is sent only there.
	 */
	public static CompletableFuture<CiderCheck> checkCider(String token) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpURLConnection connection = (HttpURLConnection) URI.create(CIDER + "now-playing").toURL().openConnection();
				connection.setConnectTimeout(350);
				connection.setReadTimeout(900);
				if (!token.isBlank()) {
					connection.setRequestProperty("apptoken", token.strip());
				}
				try {
					int code;
					try {
						code = connection.getResponseCode();
					} catch (IOException notRunning) {
						return new CiderCheck(CiderState.NOT_RUNNING, "", "");
					}
					if (code == 401 || code == 403) {
						return new CiderCheck(CiderState.NEEDS_TOKEN, "", "");
					}
					if (code != 200) {
						return new CiderCheck(CiderState.CONNECTED, "", "");
					}
					try (InputStream in = connection.getInputStream()) {
						JsonObject now = JsonParser.parseString(HttpLimits.readText(in)).getAsJsonObject();
						if (now.has("info") && now.get("info").isJsonObject()) {
							JsonObject info = now.getAsJsonObject("info");
							return new CiderCheck(CiderState.CONNECTED, text(info, "name"), text(info, "artistName"));
						}
						return new CiderCheck(CiderState.CONNECTED, "", "");
					}
				} finally {
					connection.disconnect();
				}
			} catch (Exception | Error e) {
				return new CiderCheck(CiderState.NOT_RUNNING, "", "");
			}
		}, CHECKS);
	}

	public static boolean spotifyConnected() {
		return SpotifyClient.connected();
	}

	public static String spotifyRedirect() {
		return SpotifyClient.REDIRECT;
	}

	public static String spotifyMessage() {
		return SpotifyClient.message();
	}

	public static void connectSpotify(Consumer<URI> openBrowser) {
		SpotifyClient.connect(openBrowser);
		status = SpotifyClient.message();
	}

	public static void disconnectSpotify() {
		SpotifyClient.disconnect();
		status = SpotifyClient.message();
	}

	/** @return whether the Windows session recently reported a playing song and it is the one shown */
	private static boolean windowsIsPlaying() {
		return lastFrom == From.WINDOWS && playing && track != null && System.currentTimeMillis() - windowsSeen < WINDOWS_FRESH_MILLIS;
	}

	private static boolean pollSpotify() {
		long now = System.currentTimeMillis();
		if (now < spotifyNext) {
			return spotifyOk;
		}
		spotifyNext = now + 1500;
		SpotifyClient.Playing found = SpotifyClient.poll();
		if (found == null) {
			spotifyOk = false;
			status = SpotifyClient.message();
			return false;
		}
		Track reported = found.track();
		if (source == Source.AUTO && !found.playing() && windowsIsPlaying()) {
			// Spotify reports a paused song (possibly on another device) while another player is playing: show what plays
			spotifyOk = false;
			return false;
		}
		boolean newCover = track == null || !track.key().equals(reported.key());
		update(From.SPOTIFY, reported, found.playing(), found.positionMs());
		if (coverChanged(newCover, reported.artworkUrl())) {
			loadCoverFromUrl(reported.artworkUrl(), reported.key());
		}
		spotifySeen = now;
		spotifyOk = true;
		status = "Spotify";
		return true;
	}

	private static boolean pollCider() {
		try {
			long sent = System.nanoTime();
			JsonObject now = getJson(CIDER + "now-playing", ciderToken);
			// Cider sampled the position roughly halfway between request and response
			long sampledAt = sent + (System.nanoTime() - sent) / 2;
			if (now == null || !now.has("info") || !now.get("info").isJsonObject()) {
				return false;
			}
			JsonObject info = now.getAsJsonObject("info");
			String title = text(info, "name");
			if (title.isEmpty()) {
				return false;
			}
			String artwork = "";
			if (info.has("artwork") && info.get("artwork").isJsonObject()) {
				String side = String.valueOf(COVER_REQUEST);
				artwork = text(info.getAsJsonObject("artwork"), "url").replace("{w}", side).replace("{h}", side);
			}
			long duration = info.has("durationInMillis") ? info.get("durationInMillis").getAsLong() : 0;
			long position = info.has("currentPlaybackTime") ? (long) (info.get("currentPlaybackTime").getAsDouble() * 1000.0) : 0;
			boolean isPlaying = true;
			JsonObject state = getJson(CIDER + "is-playing", ciderToken);
			if (state != null && state.has("is_playing")) {
				isPlaying = state.get("is_playing").getAsBoolean();
			}
			String id = "";
			if (info.has("playParams") && info.get("playParams").isJsonObject()) {
				// library songs have a library id ("i.1YBm...") unknown to the catalog; lyrics are requested by the catalog id
				JsonObject params = info.getAsJsonObject("playParams");
				id = text(params, "catalogId");
				if (id.isEmpty()) {
					id = text(params, "reportingId");
				}
				if (id.isEmpty() || id.startsWith("i.")) {
					id = text(params, "id");
				}
			}
			Track reported = new Track(title, text(info, "artistName"), text(info, "albumName"), duration, artwork, id);
			if (source == Source.AUTO && !isPlaying && windowsIsPlaying()) {
				return false; // Cider is paused while another player is playing: show what plays
			}
			boolean newCover = track == null || !track.key().equals(reported.key());
			update(From.CIDER, reported, isPlaying, position, sampledAt);
			if (coverChanged(newCover, artwork)) {
				loadCoverFromUrl(artwork, reported.key());
			}
			status = "Cider";
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static String text(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}

	private static JsonObject getJson(String url, String token) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setConnectTimeout(350);
		connection.setReadTimeout(900);
		if (!token.isEmpty()) {
			connection.setRequestProperty("apptoken", token);
		}
		try {
			int code;
			try {
				code = connection.getResponseCode();
				ciderRunning = true;
			} catch (IOException notRunning) {
				ciderRunning = false;
				throw notRunning;
			}
			if (code == 401 || code == 403) {
				status = "Cider needs a token: make one in Cider, under Settings, Connectivity";
				Toasts.error("cider.token", "Cider needs a token", "Make one in Cider, under Settings, Connectivity, and put it in the settings of the mod.");
				return null;
			}
			if (code != 200) {
				return null;
			}
			try (InputStream in = connection.getInputStream()) {
				return JsonParser.parseString(HttpLimits.readText(in)).getAsJsonObject();
			}
		} finally {
			connection.disconnect();
		}
	}

	private static final int MAX_COVER_BYTES = 8 * 1024 * 1024;
	/** Largest cover accepted, per side: a small file can declare a huge picture, which would take gigabytes of memory to decode. */
	private static final int MAX_COVER_SIDE = 4096;

	/** @return the decoded cover, or null if it is unreadable or larger than {@link #MAX_COVER_SIDE} on a side */
	private static BufferedImage readCover(byte[] data) throws IOException {
		ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data));
		if (input == null) {
			return null;
		}
		try (input) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return null;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				if (reader.getWidth(0) > MAX_COVER_SIDE || reader.getHeight(0) > MAX_COVER_SIDE) {
					return null;
				}
				return reader.read(0);
			} finally {
				reader.dispose();
			}
		}
	}

	/** URL of the most recently requested cover: a cover arriving later from an older URL (fast song changes) is discarded. */
	private static volatile String requestedCoverUrl = "";

	/** @return whether this song's cover needs loading: a new song, or the same song with a new URL (Cider reports the new song shortly before its cover) */
	private static boolean coverChanged(boolean newSong, String url) {
		return !url.isEmpty() && (newSong || !url.equals(requestedCoverUrl));
	}

	private static void loadCoverFromUrl(String url, String key) {
		// the URL comes from the player, so only HTTPS is fetched: no files and no plain-HTTP local network addresses
		if (!url.regionMatches(true, 0, "https://", 0, 8)) {
			return;
		}
		requestedCoverUrl = url;
		Thread thread = new Thread(() -> {
			try {
				HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
				connection.setConnectTimeout(3000);
				connection.setReadTimeout(5000);
				try (InputStream in = connection.getInputStream()) {
					byte[] data = in.readNBytes(MAX_COVER_BYTES + 1);
					if (data.length > MAX_COVER_BYTES) {
						return;
					}
					BufferedImage image = readCover(data);
					// only accept the cover of the current song from the latest URL; an older, slower request is discarded
					Track now = track;
					if (image != null && url.equals(requestedCoverUrl) && now != null && now.key().equals(key)) {
						offerCover(CoverImages.prepare(image));
						lastCoverKey = key;
					}
				}
			} catch (Exception ignored) {
			}
		}, "Lumio cover");
		thread.setDaemon(true);
		thread.start();
	}

	// ----------------------------------------------------------------------------------------------- Windows media session

	/**
	 * Starts the Windows media reader once. It calls Windows directly from Java (see {@link WindowsMedia}); there is no fallback, so where that does
	 * not work the Windows source stays unavailable and the menu says so.
	 */
	private static void ensureWindows() {
		if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
			status = "The media controls of Windows are only there on Windows";
			return;
		}
		if (!WindowsMedia.start(MusicService::handleWindows)) {
			status = "The media controls of Windows are not available on this Windows version";
		}
	}

	/** Sends a player command to the Windows media reader; only a whitelist of known words is accepted. */
	private static void sendWindows(String command) {
		if (WindowsMedia.running() && (command.equals("next") || command.equals("previous") || command.equals("playpause")
			|| command.matches("seek:\\d{1,10}"))) {
			WindowsMedia.command(command);
		}
	}

	/** Asks the Windows media reader what is playing; the answer arrives at {@link #handleWindows}. */
	private static void pollWindows() {
		if (WindowsMedia.running()) {
			WindowsMedia.poll();
		}
	}

	// ------------------------------------------------------------------------------------------------- player control

	public enum Action {
		PLAY_PAUSE("playpause"),
		NEXT("next"),
		PREVIOUS("previous");

		private final String word;

		Action(String word) {
			this.word = word;
		}
	}

	private static final ExecutorService CONTROLS = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio controls");
		thread.setDaemon(true);
		return thread;
	});

	/**
	 * Pauses, plays, skips or goes back in the player the song comes from. Runs on a background thread, since a player request can take a moment
	 * and the game must not wait for it. Pausing is shown optimistically and corrected by the next report if it failed.
	 */
	public static void control(Action action) {
		Track current = track;
		From target = current != null ? lastFrom : source == Source.CIDER ? From.CIDER : source == Source.SPOTIFY ? From.SPOTIFY : From.WINDOWS;
		if (action == Action.PLAY_PAUSE && current != null) {
			synchronized (MusicService.class) {
				heldUntil = System.nanoTime() + HOLD_NANOS;
				positionMs = position();
				positionStamp = System.nanoTime();
				playing = !playing;
			}
		}
		boolean playingNow = playing;
		CONTROLS.execute(() -> {
			try {
				switch (target) {
					case CIDER -> postCider(action == Action.PLAY_PAUSE && current != null ? (playingNow ? "play" : "pause") : action.word);
					case SPOTIFY -> SpotifyClient.command(action.word, playingNow);
					case WINDOWS -> {
						ensureWindows();
						sendWindows(action.word);
					}
				}
			} catch (Exception e) {
				status = "The player did not answer: " + e.getMessage();
				Toasts.error("control.answer", "The player did not answer", "Is it open? " + (e.getMessage() == null ? "" : e.getMessage()));
			}
		});
	}

	/** @return whether the song's player can seek: Cider and Spotify can, a Windows player when it says so */
	public static boolean canSeek() {
		return lastFrom != From.WINDOWS || windowsCanSeek;
	}

	/** @return whether the song's player can skip to the next song */
	public static boolean canNext() {
		return lastFrom != From.WINDOWS || windowsCanNext;
	}

	/** @return whether the song's player can go back to the previous song */
	public static boolean canPrevious() {
		return lastFrom != From.WINDOWS || windowsCanPrevious;
	}

	/** @return the value of a flag in a report, true when it is missing (a player that does not say allows it) */
	private static boolean flag(JsonObject object, String key) {
		return !(object.get(key) instanceof JsonPrimitive value) || !value.isBoolean() || value.getAsBoolean();
	}

	/** Seeks to a position in milliseconds in the song's player. Shown immediately and corrected by the next report. */
	public static void seek(long milliseconds) {
		Track current = track;
		if (current == null) {
			return;
		}
		long target = Math.max(0L, current.durationMs() > 0 ? Math.min(milliseconds, current.durationMs() - 500L) : milliseconds);
		From from = lastFrom;
		synchronized (MusicService.class) {
			positionMs = target;
			heldUntil = System.nanoTime() + HOLD_NANOS;
			positionStamp = System.nanoTime();
		}
		CONTROLS.execute(() -> {
			try {
				switch (from) {
					case CIDER -> postCiderSeek(target / 1000.0);
					case SPOTIFY -> SpotifyClient.seek(target);
					case WINDOWS -> {
						ensureWindows();
						sendWindows("seek:" + target);
					}
				}
			} catch (Exception e) {
				status = "The player did not answer: " + e.getMessage();
			}
		});
	}

	private static void postCiderSeek(double seconds) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(CIDER + "seek").toURL().openConnection();
		connection.setConnectTimeout(800);
		connection.setReadTimeout(2000);
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "application/json");
		if (!ciderToken.isEmpty()) {
			connection.setRequestProperty("apptoken", ciderToken);
		}
		connection.setDoOutput(true);
		connection.getOutputStream().write(("{\"position\":" + seconds + "}").getBytes(StandardCharsets.UTF_8));
		try {
			connection.getResponseCode();
		} finally {
			connection.disconnect();
		}
	}

	private static void postCider(String action) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(CIDER + action).toURL().openConnection();
		connection.setConnectTimeout(800);
		connection.setReadTimeout(2000);
		connection.setRequestMethod("POST");
		if (!ciderToken.isEmpty()) {
			connection.setRequestProperty("apptoken", ciderToken);
		}
		try {
			int code = connection.getResponseCode();
			if (code >= 400) {
				status = "Cider did not take it (" + code + ")";
				Toasts.error("cider.control." + code, "Cider did not take it", code == 401 || code == 403
					? "Cider needs a token: make one in Cider, under Settings, Connectivity, and put it in the settings of the mod."
					: "Cider answered with the error " + code + ".");
			}
		} finally {
			connection.disconnect();
		}
	}

	/** Handles a Windows media session report: the song, playback state, position, a new cover if changed, and the player. */
	private static void handleWindows(JsonObject object) {
		if (!running) {
			return;
		}
		windowsSeen = System.currentTimeMillis();
		if (source == Source.CIDER || source == Source.SPOTIFY) {
			return; // a specific player was selected, so the Windows session does not take over
		}
		if (object.has("none")) {
			if (lastFrom == From.WINDOWS) {
				lose();
			}
			status = "Nothing is playing";
			return;
		}
		String title = text(object, "title");
		if (title.isEmpty()) {
			return;
		}
		Track reported = new Track(title, text(object, "artist"), text(object, "album"),
			object.has("duration") ? object.get("duration").getAsLong() : 0, "", "");
		boolean isPlaying = object.has("playing") && object.get("playing").getAsBoolean();
		long position = object.has("position") ? object.get("position").getAsLong() : 0;
		// Cider or Spotify is responding and is the better source, unless it is paused while this one plays
		if (source == Source.AUTO && (lastFrom == From.CIDER || lastFrom == From.SPOTIFY && System.currentTimeMillis() - spotifySeen < 5000) && track != null
			&& (playing || !isPlaying)) {
			return;
		}
		update(From.WINDOWS, reported, isPlaying, position);
		windowsCanNext = flag(object, "canNext");
		windowsCanPrevious = flag(object, "canPrevious");
		windowsCanSeek = flag(object, "canSeek");
		// the cover arrives Base64-encoded, only when it changed
		String coverData = text(object, "cover");
		if (!coverData.isEmpty() && coverData.length() < MAX_COVER_BYTES * 2 && !reported.key().equals(lastCoverKey)) {
			try {
				BufferedImage image = readCover(Base64.getDecoder().decode(coverData));
				if (image != null) {
					offerCover(CoverImages.prepare(image));
					lastCoverKey = reported.key();
				}
			} catch (Exception ignored) {
			}
		}
		status = text(object, "app");
		windowsApp = status;
	}

	// ------------------------------------------------------------------------------------------------------ lyrics

	private static final Pattern TIME = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");

	private static void fetchLyrics(Track forTrack) {
		lyricsKey = forTrack.key();
		lyricsOrigin = "Searching...";
		Thread thread = new Thread(() -> {
			try {
				if (lastFrom == From.CIDER && !forTrack.id().isEmpty()) {
					List<LyricLine> fromCider = ciderLyrics(forTrack);
					if (fromCider.isEmpty()) {
						fromCider = ciderLyrics(forTrack); // Cider can be slow right after a song starts: retry once
					}
					Debug.log("Lyrics from Cider for \"{}\" (id {}): {} lines, {} with words", forTrack.title(), forTrack.id(), fromCider.size(),
						fromCider.stream().filter(line -> !line.words().isEmpty()).count());
					if (!fromCider.isEmpty() && forTrack.key().equals(lyricsKey)) {
						lyrics = fromCider;
						lyricsOrigin = "Cider (Apple Music)";
						return;
					}
				}
				String artist = forTrack.artist();
				String main = artist.split("\\s*(,|&|;| feat\\.? | ft\\.? | x )\\s*", 2)[0];
				String title = forTrack.title().replaceAll("\\s*[(\\[].*?[)\\]]", "").trim();
				String synced = null;
				if (forTrack.durationMs() > 0) {
					String exact = "get?artist_name=" + enc(artist) + "&track_name=" + enc(forTrack.title())
						+ (forTrack.album().isEmpty() ? "" : "&album_name=" + enc(forTrack.album()))
						+ "&duration=" + Math.round(forTrack.durationMs() / 1000.0);
					JsonObject found = getLyrics(LYRICS + exact);
					synced = found == null ? null : text(found, "syncedLyrics");
				}
				// the search finds songs the exact lookup misses, since title or artist may be written differently
				String[] searches = {
					"search?track_name=" + enc(forTrack.title()) + "&artist_name=" + enc(artist),
					"search?track_name=" + enc(title) + "&artist_name=" + enc(main),
					"search?q=" + enc(title + " " + main),
					"search?track_name=" + enc(title)
				};
				for (int i = 0; (synced == null || synced.isEmpty()) && i < searches.length; i++) {
					JsonObject found = getLyricsList(LYRICS + searches[i], forTrack);
					synced = found == null ? null : text(found, "syncedLyrics");
				}
				if (synced != null && !synced.isEmpty() && forTrack.key().equals(lyricsKey)) {
					lyrics = parseLyrics(synced);
					lyricsOrigin = "LRCLIB";
				} else if (forTrack.key().equals(lyricsKey)) {
					lyricsOrigin = "";
				}
			} catch (Exception ignored) {
				if (forTrack.key().equals(lyricsKey)) {
					lyricsOrigin = "";
				}
			}
		}, "Lumio lyrics");
		thread.setDaemon(true);
		thread.start();
	}

	private static String enc(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}

	private static String fetch(String url) throws IOException {
		for (int attempt = 0; attempt < 2; attempt++) {
			HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
			connection.setConnectTimeout(4000);
			connection.setReadTimeout(7000);
			connection.setRequestProperty("User-Agent", "Lumio (Minecraft mod)");
			try {
				int code = connection.getResponseCode();
				if (code == 200) {
					try (InputStream in = connection.getInputStream()) {
						return HttpLimits.readText(in);
					}
				}
				if (code != 503 && code != 502 && code != 429) {
					return null;
				}
			} finally {
				connection.disconnect();
			}
			try {
				Thread.sleep(1200); // the service is busy: retry
			} catch (InterruptedException e) {
				return null;
			}
		}
		return null;
	}

	private static JsonObject getLyrics(String url) throws IOException {
		String body = fetch(url);
		return body == null ? null : JsonParser.parseString(body).getAsJsonObject();
	}

	/** @return the search result that has synced lyrics and the duration closest to the song's */
	private static JsonObject getLyricsList(String url, Track wanted) throws IOException {
		long durationMs = wanted.durationMs();
		String body = fetch(url);
		if (body == null) {
			return null;
		}
		JsonObject best = null;
		double bestDistance = Double.MAX_VALUE;
		for (JsonElement element : JsonParser.parseString(body).getAsJsonArray()) {
			JsonObject object = element.getAsJsonObject();
			if (text(object, "syncedLyrics").isEmpty() || !isSameSong(object, wanted)) {
				continue;
			}
			double distance = durationMs > 0 && object.has("duration") && !object.get("duration").isJsonNull()
				? Math.abs(object.get("duration").getAsDouble() * 1000.0 - durationMs) : 0;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = object;
			}
		}
		return best;
	}

	private static String norm(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("\\s*[(\\[].*?[)\\]]", "").replaceAll("[^\\p{L}\\p{N}]", "");
	}

	/**
	 * @return whether a search result is the playing song. The search returns the closest match, which is often a different song, and lyrics of the
	 *         wrong song are worse than none: title and artist must match (normalized) and the duration must be close.
	 */
	private static boolean isSameSong(JsonObject found, Track wanted) {
		String title = norm(wanted.title());
		String foundTitle = norm(text(found, "trackName"));
		if (title.isEmpty() || foundTitle.isEmpty() || !(foundTitle.contains(title) || title.contains(foundTitle))) {
			return false;
		}
		String foundArtist = norm(text(found, "artistName"));
		boolean artist = foundArtist.isEmpty() || wanted.artist().isEmpty();
		for (String part : wanted.artist().split("\\s*(,|&|;| feat\\.? | ft\\.? | x |/)\\s*")) {
			String name = norm(part);
			if (!name.isEmpty() && !foundArtist.isEmpty() && (foundArtist.contains(name) || name.contains(foundArtist))) {
				artist = true;
			}
		}
		if (!artist) {
			return false;
		}
		if (wanted.durationMs() > 0 && found.has("duration") && !found.get("duration").isJsonNull()) {
			return Math.abs(found.get("duration").getAsDouble() * 1000.0 - wanted.durationMs()) <= 7000;
		}
		return true;
	}

	/** Looks up the cover in the Apple Music catalog if the player provided none. */
	private static void lookUpCover(Track forTrack) {
		Thread thread = new Thread(() -> {
			try {
				Thread.sleep(1500);
				if (!forTrack.key().equals(track == null ? "" : track.key()) || forTrack.key().equals(lastCoverKey)) {
					return;
				}
				String body = fetch("https://itunes.apple.com/search?media=music&entity=song&limit=3&term=" + enc(forTrack.title() + " " + forTrack.artist()));
				if (body == null) {
					return;
				}
				for (JsonElement element : JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("results")) {
					String url = text(element.getAsJsonObject(), "artworkUrl100");
					if (!url.isEmpty()) {
						loadCoverFromUrl(url.replace("100x100", COVER_REQUEST + "x" + COVER_REQUEST), forTrack.key());
						return;
					}
				}
			} catch (Exception ignored) {
			}
		}, "Lumio cover search");
		thread.setDaemon(true);
		thread.start();
	}
	/** @return the lines of LRC formatted text: "[01:23.45] words" */
	static List<LyricLine> parseLyrics(String lrc) {
		List<LyricLine> lines = new ArrayList<>();
		for (String raw : lrc.split("\\R")) {
			Matcher matcher = TIME.matcher(raw);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (matcher.find()) {
				long minutes = Long.parseLong(matcher.group(1));
				long seconds = Long.parseLong(matcher.group(2));
				String fraction = matcher.group(3);
				long millis = 0;
				if (fraction != null) {
					millis = Long.parseLong((fraction + "00").substring(0, 3));
				}
				times.add(minutes * 60_000L + seconds * 1000L + millis);
				end = matcher.end();
			}
			String text = raw.substring(end).trim();
			for (long time : times) {
				lines.add(new LyricLine(time, text));
			}
		}
		lines.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
		return List.copyOf(lines);
	}

	// ------------------------------------------------------------------------------- lyrics from Cider

	private static volatile String storefront = "";

	/** Queries the Apple Music API through Cider, which is signed in, the same way Cider fetches its own lyrics. */
	private static JsonObject amapi(String path) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:10767/api/v1/amapi/run-v3").toURL().openConnection();
		connection.setConnectTimeout(3000);
		connection.setReadTimeout(12000);
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/json");
		if (!ciderToken.isEmpty()) {
			connection.setRequestProperty("apptoken", ciderToken);
		}
		JsonObject body = new JsonObject();
		body.addProperty("path", path);
		try {
			connection.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
			if (connection.getResponseCode() != 200) {
				return null;
			}
			try (InputStream in = connection.getInputStream()) {
				return JsonParser.parseString(HttpLimits.readText(in)).getAsJsonObject();
			}
		} finally {
			connection.disconnect();
		}
	}

	/** @return the first string value with the given name anywhere in the JSON tree */
	private static String find(JsonElement element, String name) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonObject()) {
			for (var entry : element.getAsJsonObject().entrySet()) {
				if (entry.getKey().equals(name) && entry.getValue().isJsonPrimitive()) {
					return entry.getValue().getAsString();
				}
			}
			for (var entry : element.getAsJsonObject().entrySet()) {
				String found = find(entry.getValue(), name);
				if (found != null) {
					return found;
				}
			}
		} else if (element.isJsonArray()) {
			for (JsonElement item : element.getAsJsonArray()) {
				String found = find(item, name);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static List<LyricLine> ciderLyrics(Track forTrack) {
		try {
			if (storefront.isEmpty()) {
				JsonObject answer = amapi("/v1/me/storefront");
				String id = answer == null ? null : find(answer.get("data"), "id");
				storefront = id == null || id.isEmpty() ? "us" : id;
			}
			// prefer word-synced lyrics, fall back to line-synced ones
			for (String kind : new String[]{"syllable-lyrics", "lyrics"}) {
				JsonObject answer = amapi("/v1/catalog/" + storefront + "/songs/" + forTrack.id() + "/" + kind);
				String ttml = answer == null ? null : find(answer, "ttml");
				if (ttml != null && !ttml.isEmpty()) {
					List<LyricLine> parsed = parseTtml(ttml);
					if (!parsed.isEmpty()) {
						return parsed;
					}
				}
			}
		} catch (Exception e) {
			Debug.log("Lyrics from Cider failed: {}", e.toString());
		}
		return List.of();
	}

	private static final Pattern LINE = Pattern.compile("<p\\b([^>]*)>(.*?)</p>", Pattern.DOTALL);

	private static final Pattern BEGIN = Pattern.compile("\\bbegin=\"([^\"]+)\"");
	private static final Pattern END = Pattern.compile("\\bend=\"([^\"]+)\"");

	private static long ttmlTime(String value) {
		String text = value.trim();
		if (text.endsWith("s")) {
			text = text.substring(0, text.length() - 1);
		}
		String[] parts = text.split(":");
		double seconds = 0;
		for (String part : parts) {
			seconds = seconds * 60 + Double.parseDouble(part);
		}
		return Math.round(seconds * 1000.0);
	}

	private static long attribute(Pattern pattern, String attributes, long fallback) {
		Matcher matcher = pattern.matcher(attributes);
		try {
			return matcher.find() ? ttmlTime(matcher.group(1)) : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String plain(String markup) {
		return markup.replaceAll("<[^>]*>", "").replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'");
	}

	/** Matches a span start or end tag, or the text between: the parts a TTML lyric line consists of. */
	private static final Pattern PART = Pattern.compile("<(/?)span\\b([^>]*?)(/?)>|([^<]+)|<[^>]*>");
	private static final Pattern ROLE_BACKGROUND = Pattern.compile("role=\"x-bg\"");

	/** Text and timed words collected for one voice of a line: the main voice or the background voice. */
	private static final class Voice {
		final StringBuilder text = new StringBuilder();
		final List<Word> words = new ArrayList<>();

		void add(String piece, long start, long end) {
			int from = text.length();
			text.append(piece);
			if (start >= 0 && !piece.isBlank()) {
				Word previous = words.isEmpty() ? null : words.get(words.size() - 1);
				if (previous != null && previous.to() == from) {
					// parts of the same word merge into one word
					words.set(words.size() - 1, new Word(previous.from(), from + piece.length(), previous.startMs(), end));
				} else {
					words.add(new Word(from, from + piece.length(), start, end));
				}
			}
		}

		boolean isEmpty() {
			return text.toString().isBlank();
		}
	}

	/** @return the line with normalized whitespace, with word ranges remapped to it; words are dropped if their positions cannot be determined */
	private static String tidy(Voice voice, List<Word> wordsOut) {
		String raw = voice.text.toString();
		String line = raw.replaceAll("\\s+", " ").trim();
		if (line.isEmpty() || voice.words.isEmpty()) {
			return line;
		}
		int lead = raw.indexOf(line.substring(0, 1));
		if (raw.strip().equals(line) && lead >= 0 && !raw.contains("  ") && !raw.contains("\n")) {
			for (Word word : voice.words) {
				wordsOut.add(new Word(word.from() - lead, word.to() - lead, word.startMs(), word.endMs()));
			}
		}
		return line;
	}

	/** The singers declared in the head of the lyrics, and the singer of a line. */
	private static final Pattern AGENT = Pattern.compile("<ttm:agent\b([^>]*)>");
	private static final Pattern AGENT_TYPE = Pattern.compile("\btype=\"([^\"]+)\"");
	private static final Pattern AGENT_ID = Pattern.compile("\bxml:id=\"([^\"]+)\"");
	private static final Pattern LINE_AGENT = Pattern.compile("\bttm:agent=\"([^\"]+)\"");
	/** Background vocals end at least this long after they start, when their end is not known. */
	private static final long SHORTEST_BACKGROUND_MS = 600L;

	/** A parsed line before its singer is known, which needs all lines. */
	private record Parsed(long start, String text, List<Word> words, List<Background> backgrounds, String agent) {
	}

	/**
	 * Parses Apple Music TTML lyrics: lines, optionally word timings, background vocals (each part with its own timing), and the singer of every
	 * line when two are marked. A line with only background vocals stays an adlib, sung on its own.
	 */
	static List<LyricLine> parseTtml(String ttml) {
		List<Parsed> parsed = new ArrayList<>();
		Matcher lineMatcher = LINE.matcher(ttml);
		while (lineMatcher.find()) {
			String attributes = lineMatcher.group(1);
			String inner = lineMatcher.group(2);
			Voice main = new Voice();
			List<Voice> backgrounds = new ArrayList<>();
			List<long[]> backgroundTimes = new ArrayList<>();
			// open spans: the background part each belongs to (0 for the main voice, otherwise its number plus one), and its begin and end
			ArrayDeque<long[]> open = new ArrayDeque<>();
			Matcher part = PART.matcher(inner);
			while (part.find()) {
				if (part.group(4) != null) {
					String piece = plain(part.group(4));
					int voice = 0;
					long begin = -1;
					long end = -1;
					for (long[] frame : open) {
						if (voice == 0 && frame[0] > 0) {
							voice = (int) frame[0];
						}
						if (begin < 0 && frame[1] >= 0) {
							begin = frame[1];
							end = frame[2];
						}
					}
					(voice > 0 ? backgrounds.get(voice - 1) : main).add(piece, begin, end);
				} else if (part.group(1) != null && part.group(1).equals("/")) {
					if (!open.isEmpty()) {
						open.pop();
					}
				} else if (part.group(2) != null) {
					boolean isBackground = ROLE_BACKGROUND.matcher(part.group(2)).find();
					long begin = attribute(BEGIN, part.group(2), -1);
					long end = attribute(END, part.group(2), begin);
					long voice = 0;
					if (isBackground) {
						backgrounds.add(new Voice());
						backgroundTimes.add(new long[]{begin, end});
						voice = backgrounds.size();
					}
					if (!"/".equals(part.group(3))) {
						open.push(new long[]{voice, begin, end});
					}
				}
			}
			List<Word> mainWords = new ArrayList<>();
			String line = tidy(main, mainWords);
			List<Background> parts = new ArrayList<>();
			// a background part without a time of its own is sung with its line: it is kept, never dropped for its missing time
			long lineBegin = attribute(BEGIN, attributes, mainWords.isEmpty() ? -1 : mainWords.get(0).startMs());
			for (int i = 0; i < backgrounds.size(); i++) {
				List<Word> words = new ArrayList<>();
				String text = tidy(backgrounds.get(i), words);
				long start = words.isEmpty() ? backgroundTimes.get(i)[0] : words.get(0).startMs();
				if (start < 0) {
					start = lineBegin;
				}
				long end = words.isEmpty() ? backgroundTimes.get(i)[1] : words.get(words.size() - 1).endMs();
				if (!text.isEmpty() && start >= 0) {
					// without an end of its own its end stays its start: AdlibTimes.ownEnd then goes by the next adlib or its length, never by its line
					parts.add(new Background(text, List.copyOf(words), start, end > start ? Math.max(end, start + SHORTEST_BACKGROUND_MS) : start));
				}
			}
			if (line.isEmpty() && parts.isEmpty()) {
				continue;
			}
			long first = !mainWords.isEmpty() ? mainWords.get(0).startMs() : parts.isEmpty() ? -1 : parts.get(0).startMs();
			long start = attribute(BEGIN, attributes, first);
			if (start < 0) {
				continue;
			}
			Matcher agent = LINE_AGENT.matcher(attributes);
			parsed.add(new Parsed(start, line, List.copyOf(mainWords), List.copyOf(parts), agent.find() ? agent.group(1) : ""));
		}
		Map<String, Integer> singers = singers(ttml, parsed);
		List<LyricLine> lines = new ArrayList<>(parsed.size());
		for (Parsed line : parsed) {
			lines.add(new LyricLine(line.start(), line.text(), line.words(), line.backgrounds(), singers.getOrDefault(line.agent(), SINGER_ONE)));
		}
		lines.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
		return List.copyOf(lines);
	}

	/**
	 * @return the singer of every agent the lines use: only when two people are marked, the first one to sing is the first singer and any other
	 *         the second; an agent declared as a group is both. With a single singer the map is empty and every line belongs to the first.
	 */
	private static Map<String, Integer> singers(String ttml, List<Parsed> lines) {
		Set<String> groups = new HashSet<>();
		Matcher declared = AGENT.matcher(ttml);
		while (declared.find()) {
			Matcher type = AGENT_TYPE.matcher(declared.group(1));
			Matcher id = AGENT_ID.matcher(declared.group(1));
			if (type.find() && id.find() && type.group(1).equals("group")) {
				groups.add(id.group(1));
			}
		}
		List<String> people = new ArrayList<>();
		for (Parsed line : lines) {
			if (!line.agent().isEmpty() && !groups.contains(line.agent()) && !people.contains(line.agent())) {
				people.add(line.agent());
			}
		}
		Map<String, Integer> singers = new HashMap<>();
		if (people.size() < 2) {
			return singers;
		}
		for (int i = 0; i < people.size(); i++) {
			singers.put(people.get(i), i == 0 ? SINGER_ONE : SINGER_TWO);
		}
		for (String group : groups) {
			singers.put(group, SINGER_BOTH);
		}
		return singers;
	}

	/**
	 * @return the background vocal sung at the given time with the line it belongs to (the sung line, or the previous one, since background vocals
	 *         can continue slightly past their line), or null
	 */
	public static Background backgroundAt(long timeMs) {
		List<LyricLine> all = lyrics();
		int index = -1;
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).timeMs() <= timeMs) {
				index = i;
			} else {
				break;
			}
		}
		for (int i = index; i >= 0 && i >= index - 1; i--) {
			for (Background background : all.get(i).backgrounds()) {
				if (timeMs >= background.startMs() - 250 && timeMs <= background.endMs() + 700) {
					return background;
				}
			}
		}
		return null;
	}
}
