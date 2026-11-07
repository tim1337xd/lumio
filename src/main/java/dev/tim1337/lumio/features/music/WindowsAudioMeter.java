package dev.tim1337.lumio.features.music;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the music player's loudness on Windows from the system mixer (the same level the volume mixer meters show): the audio sessions of every
 * output device are scanned for those belonging to a music player (Cider, Apple Music, Spotify), and the peak of their meters is the level.
 * <p>
 * It calls the Windows Core Audio COM interfaces directly through Java's Foreign Function & Memory API: no script, program or library is written
 * to disk or started. Every COM interface is a pointer to a function table (vtable); the needed functions are called by their vtable index.
 * <p>
 * Created and used on a single thread, the meter thread.
 */
final class WindowsAudioMeter implements AutoCloseable {
	private static final Linker LINKER = Linker.nativeLinker();
	private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
	private static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT;
	private static final AddressLayout POINTER = ValueLayout.ADDRESS;
	private static final MemoryLayout GUID = MemoryLayout.structLayout(INT, ValueLayout.JAVA_SHORT, ValueLayout.JAVA_SHORT, MemoryLayout.sequenceLayout(8, ValueLayout.JAVA_BYTE));

	private static final String CLSID_MM_DEVICE_ENUMERATOR = "BCDE0395-E52F-467C-8E3D-C4579291692E";
	private static final String IID_MM_DEVICE_ENUMERATOR = "A95664D2-9614-4F35-A746-DE8DB63617E6";
	private static final String IID_AUDIO_SESSION_MANAGER2 = "77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F";
	private static final String IID_AUDIO_SESSION_CONTROL2 = "BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D";
	private static final String IID_AUDIO_METER_INFORMATION = "C02216F6-8C67-4B5B-9D00-D008E73E0064";

	private static final int COINIT_MULTITHREADED = 0;
	private static final int CLSCTX_ALL = 23;
	private static final int E_RENDER = 0;
	private static final int DEVICE_STATE_ACTIVE = 1;

	// signatures of the called functions: each takes the interface pointer first and returns an HRESULT
	private static final FunctionDescriptor RELEASE = FunctionDescriptor.of(INT, POINTER);
	private static final FunctionDescriptor QUERY_INTERFACE = FunctionDescriptor.of(INT, POINTER, POINTER, POINTER);
	private static final FunctionDescriptor ENUM_AUDIO_ENDPOINTS = FunctionDescriptor.of(INT, POINTER, INT, INT, POINTER);
	private static final FunctionDescriptor GET_COUNT = FunctionDescriptor.of(INT, POINTER, POINTER);
	private static final FunctionDescriptor ITEM = FunctionDescriptor.of(INT, POINTER, INT, POINTER);
	private static final FunctionDescriptor ACTIVATE = FunctionDescriptor.of(INT, POINTER, POINTER, INT, POINTER, POINTER);
	private static final FunctionDescriptor GET_SESSION_ENUMERATOR = FunctionDescriptor.of(INT, POINTER, POINTER);
	private static final FunctionDescriptor GET_SESSION = FunctionDescriptor.of(INT, POINTER, INT, POINTER);
	private static final FunctionDescriptor GET_PROCESS_ID = FunctionDescriptor.of(INT, POINTER, POINTER);
	private static final FunctionDescriptor GET_PEAK_VALUE = FunctionDescriptor.of(INT, POINTER, POINTER);

	/** A held COM interface: its pointer, and the frequently called vtable function. */
	private record Meter(MemorySegment pointer, MethodHandle getPeakValue) {
	}

	private final Arena arena = Arena.ofConfined();
	private final MethodHandle coInitializeEx;
	private final MethodHandle coCreateInstance;
	private final MemorySegment clsidEnumerator;
	private final MemorySegment iidEnumerator;
	private final MemorySegment iidSessionManager;
	private final MemorySegment iidSessionControl2;
	private final MemorySegment iidMeter;
	/** Scratch memory for out parameters: a pointer, a number. */
	private final MemorySegment outPointer;
	private final MemorySegment outInt;
	private final MemorySegment outFloat;

	private final List<MemorySegment> managers = new ArrayList<>();
	private final List<Meter> meters = new ArrayList<>();
	private final Map<Long, String> names = new HashMap<>();
	private final Pattern players;

	WindowsAudioMeter(String playerPattern) {
		players = Pattern.compile(playerPattern, Pattern.CASE_INSENSITIVE);
		SymbolLookup ole32 = SymbolLookup.libraryLookup("ole32", arena);
		coInitializeEx = LINKER.downcallHandle(ole32.findOrThrow("CoInitializeEx"), FunctionDescriptor.of(INT, POINTER, INT));
		coCreateInstance = LINKER.downcallHandle(ole32.findOrThrow("CoCreateInstance"), FunctionDescriptor.of(INT, POINTER, POINTER, INT, POINTER, POINTER));
		clsidEnumerator = guid(CLSID_MM_DEVICE_ENUMERATOR);
		iidEnumerator = guid(IID_MM_DEVICE_ENUMERATOR);
		iidSessionManager = guid(IID_AUDIO_SESSION_MANAGER2);
		iidSessionControl2 = guid(IID_AUDIO_SESSION_CONTROL2);
		iidMeter = guid(IID_AUDIO_METER_INFORMATION);
		outPointer = arena.allocate(POINTER);
		outInt = arena.allocate(INT);
		outFloat = arena.allocate(FLOAT);
		try {
			// the result must be captured with its declared type even if unused, otherwise invokeExact does not match
			int ignored = (int) coInitializeEx.invokeExact(MemorySegment.NULL, COINIT_MULTITHREADED);
		} catch (Throwable error) {
			throw new IllegalStateException("COM could not be started", error);
		}
	}

	/** @return a GUID in native memory, parsed from its string form: the first three fields in native byte order, the last eight bytes as written */
	private MemorySegment guid(String text) {
		String[] parts = text.split("-");
		MemorySegment segment = arena.allocate(GUID);
		segment.set(INT, 0, (int) Long.parseLong(parts[0], 16));
		segment.set(ValueLayout.JAVA_SHORT, 4, (short) Integer.parseInt(parts[1], 16));
		segment.set(ValueLayout.JAVA_SHORT, 6, (short) Integer.parseInt(parts[2], 16));
		String tail = parts[3] + parts[4];
		for (int i = 0; i < 8; i++) {
			segment.set(ValueLayout.JAVA_BYTE, 8 + i, (byte) Integer.parseInt(tail.substring(i * 2, i * 2 + 2), 16));
		}
		return segment;
	}

	/** @return the function at a vtable index of an interface */
	private static MethodHandle method(MemorySegment com, int index, FunctionDescriptor descriptor) {
		MemorySegment table = com.reinterpret(POINTER.byteSize()).get(POINTER, 0);
		MemorySegment function = table.reinterpret((index + 1) * POINTER.byteSize()).getAtIndex(POINTER, index);
		return LINKER.downcallHandle(function, descriptor);
	}

	private static boolean ok(int result) {
		return result >= 0;
	}

	private static void release(MemorySegment com) {
		if (com == null || com.equals(MemorySegment.NULL)) {
			return;
		}
		try {
			int ignored = (int) method(com, 2, RELEASE).invokeExact(com);
		} catch (Throwable ignored) {
			// released in any case
		}
	}

	private MemorySegment taken() {
		return outPointer.get(POINTER, 0);
	}

	/**
	 * Opens every output device, not just the default one: players can be routed to another device, as with a mixer like Wave Link.
	 * @return whether any device was opened
	 */
	boolean open() {
		closeManagers();
		try {
			if (!ok((int) coCreateInstance.invokeExact(clsidEnumerator, MemorySegment.NULL, CLSCTX_ALL, iidEnumerator, outPointer))) {
				return false;
			}
			MemorySegment enumerator = taken();
			try {
				if (!ok((int) method(enumerator, 3, ENUM_AUDIO_ENDPOINTS).invokeExact(enumerator, E_RENDER, DEVICE_STATE_ACTIVE, outPointer))) {
					return false;
				}
				MemorySegment devices = taken();
				try {
					if (!ok((int) method(devices, 3, GET_COUNT).invokeExact(devices, outInt))) {
						return false;
					}
					int count = outInt.get(INT, 0);
					for (int i = 0; i < count; i++) {
						if (!ok((int) method(devices, 4, ITEM).invokeExact(devices, i, outPointer))) {
							continue;
						}
						MemorySegment device = taken();
						try {
							if (ok((int) method(device, 3, ACTIVATE).invokeExact(device, iidSessionManager, CLSCTX_ALL, MemorySegment.NULL, outPointer))) {
								managers.add(taken());
							}
						} finally {
							release(device);
						}
					}
				} finally {
					release(devices);
				}
			} finally {
				release(enumerator);
			}
		} catch (Throwable error) {
			closeManagers();
			return false;
		}
		return !managers.isEmpty();
	}

	/** @return the executable name of a process, without path and extension */
	private String nameOf(long pid) {
		String known = names.get(pid);
		if (known != null) {
			return known;
		}
		String name = ProcessHandle.of(pid).flatMap(handle -> handle.info().command()).orElse("");
		int slash = Math.max(name.lastIndexOf('\\'), name.lastIndexOf('/'));
		name = name.substring(slash + 1);
		if (name.toLowerCase(Locale.ROOT).endsWith(".exe")) {
			name = name.substring(0, name.length() - 4);
		}
		names.put(pid, name);
		return name;
	}

	/**
	 * Rescans the audio sessions and keeps those of music players: the device output is not the player's output, since the game and everything
	 * else play there too. Sessions come and go, so this is repeated periodically.
	 */
	void refresh() {
		closeMeters();
		for (MemorySegment manager : managers) {
			try {
				if (!ok((int) method(manager, 5, GET_SESSION_ENUMERATOR).invokeExact(manager, outPointer))) {
					continue;
				}
				MemorySegment sessions = taken();
				try {
					if (!ok((int) method(sessions, 3, GET_COUNT).invokeExact(sessions, outInt))) {
						continue;
					}
					int count = outInt.get(INT, 0);
					for (int i = 0; i < count; i++) {
						if (!ok((int) method(sessions, 4, GET_SESSION).invokeExact(sessions, i, outPointer))) {
							continue;
						}
						MemorySegment session = taken();
						try {
							takeIfPlayer(session);
						} finally {
							release(session);
						}
					}
				} finally {
					release(sessions);
				}
			} catch (Throwable ignored) {
				// a device that disappeared: it is reopened later
			}
		}
	}

	/** Keeps a session's meter if the session belongs to a music player. */
	private void takeIfPlayer(MemorySegment session) throws Throwable {
		if (!ok((int) method(session, 0, QUERY_INTERFACE).invokeExact(session, iidSessionControl2, outPointer))) {
			return;
		}
		MemorySegment control = taken();
		try {
			if (!ok((int) method(control, 14, GET_PROCESS_ID).invokeExact(control, outInt))) {
				return;
			}
			long pid = Integer.toUnsignedLong(outInt.get(INT, 0));
			if (pid == 0 || !players.matcher(nameOf(pid)).find()) {
				return;
			}
			if (ok((int) method(session, 0, QUERY_INTERFACE).invokeExact(session, iidMeter, outPointer))) {
				MemorySegment meter = taken();
				meters.add(new Meter(meter, method(meter, 3, GET_PEAK_VALUE)));
			}
		} finally {
			release(control);
		}
	}

	/** @return the player's current peak level from 0 to 1, or -1 if there is no player session, as the level is then unknown */
	float peak() {
		if (meters.isEmpty()) {
			return -1f;
		}
		float best = 0f;
		for (Meter meter : meters) {
			try {
				if (ok((int) meter.getPeakValue().invokeExact(meter.pointer(), outFloat))) {
					best = Math.max(best, outFloat.get(FLOAT, 0));
				}
			} catch (Throwable error) {
				closeMeters();
				return -1f;
			}
		}
		return best;
	}

	private void closeMeters() {
		for (Meter meter : meters) {
			release(meter.pointer());
		}
		meters.clear();
	}

	private void closeManagers() {
		closeMeters();
		for (MemorySegment manager : managers) {
			release(manager);
		}
		managers.clear();
	}

	@Override
	public void close() {
		closeManagers();
		arena.close();
	}
}
