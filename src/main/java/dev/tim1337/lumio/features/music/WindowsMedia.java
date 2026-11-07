package dev.tim1337.lumio.features.music;

import com.google.gson.JsonObject;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Reads the Windows media session (what plays in Spotify, a browser, Apple Music, ...) natively from Java, without any external process: queries
 * the player's session for the song, position, playback state and cover, and sends skip, previous, play/pause and seek commands.
 * <p>
 * Windows exposes these as Windows Runtime objects, called through each object's vtable: a function's vtable index is fixed per interface and
 * follows the order in the Windows metadata (after the six functions every object has). Everything runs on a dedicated thread that initializes
 * the runtime once.
 */
final class WindowsMedia {
	// interface and class names as defined by Windows
	/** A music player's app id, preferred over other playing apps. */
	private static final Pattern MUSIC_APP = Pattern.compile("(?i).*(Apple|Cider|iTunes|Music|Spotify).*");
	private static final String MANAGER_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
	private static final Guid.GUID MANAGER_STATICS = new Guid.GUID("{2050c4ee-11a0-57de-aed7-c97c70338245}");
	private static final Guid.GUID ASYNC_INFO = new Guid.GUID("{00000036-0000-0000-C000-000000000046}");
	private static final Guid.GUID RANDOM_ACCESS_STREAM = new Guid.GUID("{905a0fe1-bc53-11df-8c49-001e4fc686da}");
	private static final Guid.GUID INPUT_STREAM = new Guid.GUID("{905a0fe2-bc53-11df-8c49-001e4fc686da}");
	private static final String DATA_READER_CLASS = "Windows.Storage.Streams.DataReader";
	private static final Guid.GUID DATA_READER_FACTORY = new Guid.GUID("{d7527847-57da-4e15-914c-06806699a098}");

	// vtable indices: 0 QueryInterface, 2 Release; the rest belong to the specific interfaces
	private static final int QUERY_INTERFACE = 0;
	private static final int RELEASE = 2;
	private static final int STATICS_REQUEST = 6;
	private static final int MANAGER_GET_SESSIONS = 7;
	private static final int LIST_GET_AT = 6;
	private static final int LIST_SIZE = 7;
	private static final int SESSION_APP = 6;
	private static final int SESSION_MEDIA_PROPERTIES = 7;
	private static final int SESSION_TIMELINE = 8;
	private static final int SESSION_PLAYBACK = 9;
	private static final int SESSION_SKIP_NEXT = 16;
	private static final int SESSION_SKIP_PREVIOUS = 17;
	private static final int SESSION_TOGGLE = 20;
	private static final int SESSION_SEEK = 24;
	private static final int PROPERTIES_TITLE = 6;
	private static final int PROPERTIES_ARTIST = 9;
	private static final int PROPERTIES_ALBUM = 10;
	private static final int PROPERTIES_THUMBNAIL = 15;
	private static final int TIMELINE_START = 6;
	private static final int TIMELINE_END = 7;
	private static final int TIMELINE_POSITION = 10;
	private static final int TIMELINE_UPDATED = 11;
	/** The playback info's controls, and what they allow: skipping, going back, and seeking. */
	private static final int PLAYBACK_CONTROLS = 6;
	private static final int PLAYBACK_STATUS = 7;
	private static final int CONTROLS_NEXT = 12;
	private static final int CONTROLS_PREVIOUS = 13;
	private static final int CONTROLS_POSITION = 20;
	private static final int STATUS_PLAYING = 4;
	private static final int STREAM_REFERENCE_OPEN = 6;
	private static final int STREAM_SIZE = 6;
	private static final int READER_FACTORY_CREATE = 6;
	private static final int READER_READ_BYTES = 14;
	private static final int READER_LOAD = 29;
	private static final int ASYNC_STATUS = 7;
	private static final int ASYNC_RESULTS = 8;

	/** The Unix epoch in Windows time (100 ns units since 1601). */
	private static final long WINDOWS_EPOCH = 116_444_736_000_000_000L;
	private static final int MAX_COVER_BYTES = 8 * 1024 * 1024;

	private static ExecutorService worker;
	private static Consumer<JsonObject> reports;
	private static Pointer manager;
	private static boolean failed;
	private static String lastCover = "";

	private WindowsMedia() {
	}

	/**
	 * Starts the reader once: initializes the Windows Runtime and requests the media session manager.
	 *
	 * @param consumer receives one report object per poll
	 * @return whether native access works; if not, the Windows media source is unavailable
	 */
	static synchronized boolean start(Consumer<JsonObject> consumer) {
		reports = consumer;
		if (worker != null) {
			return !failed;
		}
		if (failed) {
			return false;
		}
		worker = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "Lumio Windows media");
			thread.setDaemon(true);
			return thread;
		});
		try {
			worker.submit(WindowsMedia::open).get();
		} catch (Exception e) {
			failed = true;
		}
		if (failed) {
			worker.shutdownNow();
			worker = null;
		}
		return !failed;
	}

	static synchronized boolean running() {
		return worker != null;
	}

	static synchronized void stop() {
		if (worker != null) {
			worker.submit(() -> {
				if (manager != null) {
					release(manager);
					manager = null;
				}
			});
			worker.shutdown();
			worker = null;
		}
		lastCover = "";
	}

	/** Requests a report of what is playing; it is delivered to the consumer passed to {@link #start}. */
	static void poll() {
		submit(() -> report(read()));
	}

	/** Sends a command to the player ("next", "previous", "playpause" or "seek:milliseconds"), then reports what is playing. */
	static void command(String command) {
		submit(() -> {
			Pointer session = findSession();
			if (session != null) {
				try {
					if (command.startsWith("seek:")) {
						long milliseconds = Long.parseLong(command.substring(5));
						awaitBool(callPointer(session, SESSION_SEEK, milliseconds * 10_000L));
					} else {
						int slot = switch (command) {
							case "next" -> SESSION_SKIP_NEXT;
							case "previous" -> SESSION_SKIP_PREVIOUS;
							default -> SESSION_TOGGLE;
						};
						awaitBool(callPointer(session, slot));
					}
					Thread.sleep(200);
				} catch (Exception ignored) {
				} finally {
					release(session);
				}
			}
			report(read());
		});
	}

	private static synchronized void submit(Runnable task) {
		if (worker != null) {
			worker.submit(() -> {
				try {
					task.run();
				} catch (Throwable ignored) {
					// the player went away during the request: it is queried again next time
				}
			});
		}
	}

	private static void report(JsonObject object) {
		Consumer<JsonObject> consumer = reports;
		if (consumer != null && object != null) {
			consumer.accept(object);
		}
	}

	// ---------------------------------------------------------------------------------------------------- Windows Runtime

	private static Function combase(String name) {
		return NativeLibrary.getInstance("combase").getFunction(name);
	}

	/** Initializes the Windows Runtime on this thread and requests the media session manager. */
	private static void open() {
		try {
			// multithreaded apartment (1): objects can be used from this thread without a message loop
			combase("RoInitialize").invokeInt(new Object[]{1});
			Pointer statics = factory(MANAGER_CLASS, MANAGER_STATICS);
			try {
				manager = awaitPointer(callPointer(statics, STATICS_REQUEST));
			} finally {
				release(statics);
			}
			if (manager == null) {
				failed = true;
			}
		} catch (Throwable e) {
			failed = true;
		}
	}

	private static Pointer factory(String className, Guid.GUID iid) {
		Pointer name = string(className);
		try {
			PointerByReference out = new PointerByReference();
			check(combase("RoGetActivationFactory").invokeInt(new Object[]{name, guid(iid), out}));
			return out.getValue();
		} finally {
			deleteString(name);
		}
	}

	private static Pointer guid(Guid.GUID iid) {
		iid.write();
		return iid.getPointer();
	}

	private static Pointer string(String text) {
		PointerByReference out = new PointerByReference();
		check(combase("WindowsCreateString").invokeInt(new Object[]{new WString(text), text.length(), out}));
		return out.getValue();
	}

	private static String readString(Pointer hstring) {
		if (hstring == null) {
			return "";
		}
		try {
			IntByReference length = new IntByReference();
			Pointer buffer = (Pointer) combase("WindowsGetStringRawBuffer").invoke(Pointer.class, new Object[]{hstring, length});
			return buffer == null ? "" : new String(buffer.getCharArray(0, length.getValue()));
		} finally {
			deleteString(hstring);
		}
	}

	private static void deleteString(Pointer hstring) {
		if (hstring != null) {
			combase("WindowsDeleteString").invokeInt(new Object[]{hstring});
		}
	}

	private static void check(int result) {
		if (result < 0) {
			throw new IllegalStateException("Windows answered 0x" + Integer.toHexString(result));
		}
	}

	/** Calls a vtable function of an object, passing the object as the first argument. @return the HRESULT (negative is an error) */
	private static int call(Pointer object, int slot, Object... arguments) {
		Pointer table = object.getPointer(0);
		Function function = Function.getFunction(table.getPointer((long) slot * Native.POINTER_SIZE));
		Object[] all = new Object[arguments.length + 1];
		all[0] = object;
		System.arraycopy(arguments, 0, all, 1, arguments.length);
		return function.invokeInt(all);
	}

	/** Calls a vtable function whose result (an object or an HSTRING) is returned through the last argument. */
	private static Pointer callPointer(Pointer object, int slot, Object... arguments) {
		PointerByReference out = new PointerByReference();
		Object[] all = new Object[arguments.length + 1];
		System.arraycopy(arguments, 0, all, 0, arguments.length);
		all[arguments.length] = out;
		check(call(object, slot, all));
		return out.getValue();
	}

	private static long callLong(Pointer object, int slot) {
		LongByReference out = new LongByReference();
		check(call(object, slot, out));
		return out.getValue();
	}

	private static int callInt(Pointer object, int slot) {
		IntByReference out = new IntByReference();
		check(call(object, slot, out));
		return out.getValue();
	}

	private static void release(Pointer object) {
		if (object != null) {
			call(object, RELEASE);
		}
	}

	private static Pointer query(Pointer object, Guid.GUID iid) {
		PointerByReference out = new PointerByReference();
		check(call(object, QUERY_INTERFACE, guid(iid), out));
		return out.getValue();
	}

	/** Waits up to four seconds for an asynchronous Windows operation. @return whether it completed */
	private static boolean await(Pointer operation) {
		Pointer info = query(operation, ASYNC_INFO);
		try {
			long until = System.nanoTime() + 4_000_000_000L;
			while (System.nanoTime() < until) {
				int status = callInt(info, ASYNC_STATUS);
				if (status == 1) {
					return true;
				}
				if (status > 1) {
					return false; // canceled or failed
				}
				Thread.sleep(5);
			}
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} finally {
			release(info);
		}
	}

	/** Waits for an operation and releases it. @return the object it produced, or null */
	private static Pointer awaitPointer(Pointer operation) {
		if (operation == null) {
			return null;
		}
		try {
			if (!await(operation)) {
				return null;
			}
			PointerByReference out = new PointerByReference();
			return call(operation, ASYNC_RESULTS, out) < 0 ? null : out.getValue();
		} finally {
			release(operation);
		}
	}

	private static boolean awaitBool(Pointer operation) {
		if (operation == null) {
			return false;
		}
		try {
			if (!await(operation)) {
				return false;
			}
			ByteByReference out = new ByteByReference();
			return call(operation, ASYNC_RESULTS, out) >= 0 && out.getValue() != 0;
		} finally {
			release(operation);
		}
	}

	private static int awaitInt(Pointer operation) {
		if (operation == null) {
			return 0;
		}
		try {
			if (!await(operation)) {
				return 0;
			}
			IntByReference out = new IntByReference();
			return call(operation, ASYNC_RESULTS, out) < 0 ? 0 : out.getValue();
		} finally {
			release(operation);
		}
	}

	// ---------------------------------------------------------------------------------------------------- player session

	/** @return the session to show: a music player if there is one, otherwise whatever is playing, or null; the caller must release it */
	private static Pointer findSession() {
		if (manager == null) {
			return null;
		}
		Pointer sessions = callPointer(manager, MANAGER_GET_SESSIONS);
		if (sessions == null) {
			return null;
		}
		try {
			int size = callInt(sessions, LIST_SIZE);
			Pointer playing = null;
			for (int i = 0; i < size; i++) {
				Pointer session = callPointer(sessions, LIST_GET_AT, i);
				if (session == null) {
					continue;
				}
				String app = readString(callPointer(session, SESSION_APP));
				if (MUSIC_APP.matcher(app).matches()) {
					release(playing);
					return session;
				}
				if (playing == null && isPlaying(session)) {
					playing = session;
				} else {
					release(session);
				}
			}
			return playing;
		} finally {
			release(sessions);
		}
	}

	/** Adds what the player allows to a report: skipping, going back and seeking, as it tells Windows. */
	private static void controls(Pointer session, JsonObject out) {
		Pointer info = callPointer(session, SESSION_PLAYBACK);
		if (info == null) {
			return;
		}
		try {
			Pointer controls = callPointer(info, PLAYBACK_CONTROLS);
			if (controls == null) {
				return;
			}
			try {
				// a Windows Runtime boolean is one byte; the rest of the int read stays 0
				out.addProperty("canNext", (callInt(controls, CONTROLS_NEXT) & 0xFF) != 0);
				out.addProperty("canPrevious", (callInt(controls, CONTROLS_PREVIOUS) & 0xFF) != 0);
				out.addProperty("canSeek", (callInt(controls, CONTROLS_POSITION) & 0xFF) != 0);
			} finally {
				release(controls);
			}
		} catch (RuntimeException e) {
			// an older Windows or a player without the controls: everything stays allowed, as before
		} finally {
			release(info);
		}
	}

	private static boolean isPlaying(Pointer session) {
		Pointer info = callPointer(session, SESSION_PLAYBACK);
		try {
			return info != null && callInt(info, PLAYBACK_STATUS) == STATUS_PLAYING;
		} finally {
			release(info);
		}
	}

	/** @return a report of what is playing: song, playback state, position, duration, cover if new, and the player */
	private static JsonObject read() {
		JsonObject out = new JsonObject();
		Pointer session = findSession();
		if (session == null) {
			out.addProperty("none", true);
			return out;
		}
		try {
			Pointer properties = awaitPointer(callPointer(session, SESSION_MEDIA_PROPERTIES));
			if (properties == null) {
				out.addProperty("none", true);
				return out;
			}
			try {
				String title = readString(callPointer(properties, PROPERTIES_TITLE));
				String artist = readString(callPointer(properties, PROPERTIES_ARTIST));
				String album = readString(callPointer(properties, PROPERTIES_ALBUM));
				out.addProperty("title", title);
				out.addProperty("artist", artist);
				out.addProperty("album", album);
				boolean playing = isPlaying(session);
				out.addProperty("playing", playing);
				controls(session, out);
				Pointer timeline = callPointer(session, SESSION_TIMELINE);
				if (timeline != null) {
					try {
						long position = callLong(timeline, TIMELINE_POSITION) / 10_000L;
						// only trust the last-updated timestamp when it is recent: some players leave it empty
						if (playing) {
							long updated = (callLong(timeline, TIMELINE_UPDATED) - WINDOWS_EPOCH) / 10_000L;
							long age = System.currentTimeMillis() - updated;
							if (updated > 0 && age >= 0 && age < 600_000L) {
								position += age;
							}
						}
						out.addProperty("position", position);
						out.addProperty("duration", (callLong(timeline, TIMELINE_END) - callLong(timeline, TIMELINE_START)) / 10_000L);
					} finally {
						release(timeline);
					}
				}
				String key = title + "|" + artist + "|" + album;
				String cover = "";
				if (!key.equals(lastCover)) {
					lastCover = key;
					cover = cover(properties);
				}
				out.addProperty("cover", cover);
			} finally {
				release(properties);
			}
			out.addProperty("app", readString(callPointer(session, SESSION_APP)));
			return out;
		} finally {
			release(session);
		}
	}

	/** @return the song's cover as provided by the player, Base64-encoded (the image reader accepts PNG, JPEG and others), or an empty string */
	private static String cover(Pointer properties) {
		Pointer reference = null;
		Pointer stream = null;
		Pointer random = null;
		Pointer input = null;
		Pointer readerFactory = null;
		Pointer reader = null;
		try {
			reference = callPointer(properties, PROPERTIES_THUMBNAIL);
			if (reference == null) {
				return "";
			}
			stream = awaitPointer(callPointer(reference, STREAM_REFERENCE_OPEN));
			if (stream == null) {
				return "";
			}
			random = query(stream, RANDOM_ACCESS_STREAM);
			long size = callLong(random, STREAM_SIZE);
			if (size <= 0 || size > MAX_COVER_BYTES) {
				return "";
			}
			input = query(stream, INPUT_STREAM);
			readerFactory = factory(DATA_READER_CLASS, DATA_READER_FACTORY);
			reader = callPointer(readerFactory, READER_FACTORY_CREATE, input);
			int loaded = awaitInt(callPointer(reader, READER_LOAD, (int) size));
			if (loaded <= 0) {
				return "";
			}
			Memory bytes = new Memory(loaded);
			check(call(reader, READER_READ_BYTES, loaded, bytes));
			return Base64.getEncoder().encodeToString(bytes.getByteArray(0, loaded));
		} catch (RuntimeException e) {
			return "";
		} finally {
			release(reader);
			release(readerFactory);
			release(input);
			release(random);
			release(stream);
			release(reference);
		}
	}
}
