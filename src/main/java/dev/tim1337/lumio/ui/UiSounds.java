package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.menu.MenuFeature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * Sounds of the menu and screen buttons and switches.
 * <p>
 * They are not sound files but synthesized once at game start from a few soft tones with a fast attack and round decay (like glass or a small
 * bell), plus a little noise for the touch. Sounds play on their own audio line outside the game's sound engine, so the mod's volume setting
 * fully controls them; the game's UI and master volumes are multiplied in so the vanilla sliders still apply.
 */
public final class UiSounds {
	public enum Cue {
		/** A button was pressed. */
		CLICK,
		/** A switch or checkbox was turned on. */
		ON,
		/** A switch or checkbox was turned off. */
		OFF,
		/** A choice was made, or a slider notch was passed. */
		TICK,
		/** A window opens. */
		OPEN,
		/** A window closes. */
		CLOSE,
		/** Something succeeded. */
		DONE,
		/** Something failed. */
		FAIL
	}

	private static final int RATE = 44_100;
	private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);
	/** Number of distinct slider tick pitches from lowest to highest value; the tick pitch rises with the value. */
	private static final int PITCHES = 12;
	private static final int VOICES = 3;

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio sounds");
		thread.setDaemon(true);
		return thread;
	});
	private static final Clip[][] CLIPS = new Clip[Cue.values().length + PITCHES][];
	private static final long[] STAMPS = new long[CLIPS.length];
	private static final int[] NEXT = new int[CLIPS.length];
	private static final byte[][] DATA = new byte[CLIPS.length][];
	private static volatile boolean broken;
	/** How loud the game's bundle sounds are when the settings window docks or comes loose, before the mod's volume: quiet, they are only a hint. */
	private static final float DOCK_VOLUME = 0.35f;
	private static volatile boolean ready;

	private UiSounds() {
	}

	/** Prepares the sounds in the background, so the first press does not have to wait. */
	public static void warmUp() {
		WORKER.execute(() -> {
			try {
				for (int i = 0; i < DATA.length; i++) {
					DATA[i] = synthesize(i);
				}
				ready = true;
			} catch (Throwable error) {
				broken = true;
				Logs.warn("The interface sounds could not be made", error);
			}
		});
	}

	public static void play(Cue cue) {
		play(cue.ordinal());
	}

	/**
	 * Plays the game's own bundle sound when the settings window docks into the menu window (the bundle's insert) or comes loose from it (its
	 * remove), quietly and only with the interface sounds on. It goes through the game's sound engine, so its volume sliders apply as for any
	 * game sound.
	 */
	public static void dock(boolean docked) {
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null || !menu.soundsOn()) {
			return;
		}
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(docked ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE_ONE, 1f,
			DOCK_VOLUME * menu.soundVolume()));
	}

	public static void click() {
		play(Cue.CLICK);
	}

	public static void toggle(boolean on) {
		play(on ? Cue.ON : Cue.OFF);
	}

	/** @param pitch from 0 (lowest tone) to 1 (highest) */
	public static void tick(float pitch) {
		int step = Math.max(0, Math.min(PITCHES - 1, Math.round(pitch * (PITCHES - 1))));
		play(Cue.values().length + step);
	}

	private static void play(int index) {
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null || broken || !menu.soundsOn()) {
			return;
		}
		float volume = menu.soundVolume();
		Minecraft client = Minecraft.getInstance();
		if (client.options != null) {
			volume *= client.options.getFinalSoundSourceVolume(SoundSource.UI);
		}
		if (volume < 0.005f) {
			return;
		}
		// a sound requested again immediately (e.g. dragging across checkboxes) plays only once
		long now = System.nanoTime();
		synchronized (STAMPS) {
			if (now - STAMPS[index] < 22_000_000L) {
				return;
			}
			STAMPS[index] = now;
		}
		float finalVolume = volume;
		WORKER.execute(() -> start(index, finalVolume));
	}

	private static void start(int index, float volume) {
		try {
			if (!ready || DATA[index] == null) {
				return;
			}
			Clip[] voices = CLIPS[index];
			if (voices == null) {
				voices = new Clip[VOICES];
				for (int i = 0; i < VOICES; i++) {
					Clip clip = AudioSystem.getClip();
					clip.open(FORMAT, DATA[index], 0, DATA[index].length);
					voices[i] = clip;
				}
				CLIPS[index] = voices;
			}
			// voices are used round-robin, so a sound repeated while still playing is not cut off
			Clip clip = voices[NEXT[index]];
			NEXT[index] = (NEXT[index] + 1) % VOICES;
			if (clip.isRunning()) {
				clip.stop();
			}
			clip.setFramePosition(0);
			if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
				float decibels = (float) (20.0 * Math.log10(Math.max(0.0001, volume)));
				gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
			}
			clip.start();
		} catch (Throwable error) {
			// no audio device or no free line: the menu works fine without sound, and it is not retried
			broken = true;
			Logs.warn("The interface sounds are off, as no line could be opened", error);
		}
	}

	// ------------------------------------------------------------------------------------------------ synthesis

	private static byte[] synthesize(int index) {
		Cue[] cues = Cue.values();
		if (index >= cues.length) {
			return toPcm(tickSound(index - cues.length), 0.42f);
		}
		// every sound is normalized to its own loudness so the volume setting applies evenly: small sounds are quiet, chimes are not
		return switch (cues[index]) {
			case CLICK -> toPcm(clickSound(), 0.62f);
			case ON -> toPcm(toggleSound(true), 0.62f);
			case OFF -> toPcm(toggleSound(false), 0.56f);
			case TICK -> toPcm(tickSound(PITCHES / 2), 0.42f);
			case OPEN -> toPcm(sweepSound(true), 0.46f);
			case CLOSE -> toPcm(sweepSound(false), 0.42f);
			case DONE -> toPcm(doneSound(), 0.6f);
			case FAIL -> toPcm(failSound(), 0.55f);
		};
	}

	private static float[] buffer(float seconds) {
		return new float[Math.round(seconds * RATE)];
	}

	/** A tone with a quick attack and round decay, like struck glass. Glass overtones are not integer multiples of the fundamental, so a few such partials are added. */
	private static void bell(float[] out, float start, float frequency, float amplitude, float decay) {
		float[] ratios = {1f, 2.76f, 5.4f};
		float[] weights = {1f, 0.22f, 0.07f};
		float[] decays = {1f, 0.45f, 0.2f};
		for (int p = 0; p < ratios.length; p++) {
			tone(out, start, frequency * ratios[p], amplitude * weights[p], decay * decays[p], 0.0012f);
		}
	}

	/** A decaying sine: starts at {@code start}, rises over {@code attack} and decays with the {@code decay} time constant. */
	private static void tone(float[] out, float start, float frequency, float amplitude, float decay, float attack) {
		int from = Math.round(start * RATE);
		double step = 2.0 * Math.PI * frequency / RATE;
		for (int i = from; i < out.length; i++) {
			float t = (i - from) / (float) RATE;
			float envelope = (float) Math.exp(-t / decay) * Math.min(1f, t / attack);
			if (envelope < 0.0005f && t > attack) {
				break;
			}
			out[i] += (float) Math.sin(step * (i - from)) * amplitude * envelope;
		}
	}

	/** A sine gliding from one pitch to another, with an envelope rising over {@code attack} and falling over the rest of the length. */
	private static void slide(float[] out, float start, float length, float from, float to, float amplitude, float attack) {
		int first = Math.round(start * RATE);
		int count = Math.round(length * RATE);
		double phase = 0.0;
		for (int i = 0; i < count && first + i < out.length; i++) {
			float t = i / (float) count;
			// the pitch follows a curve that is fast at first, so the sound opens like released air
			float pitch = (float) (from + (to - from) * (1.0 - Math.pow(1.0 - t, 2.2)));
			phase += 2.0 * Math.PI * pitch / RATE;
			float rise = Math.min(1f, i / (attack * RATE));
			float fall = (float) Math.pow(1.0 - t, 2.0);
			out[first + i] += (float) Math.sin(phase) * amplitude * rise * fall;
		}
	}

	/** A short noise grain, low-pass filtered so it reads as a touch rather than a hiss. */
	private static void touch(float[] out, float start, float amplitude, float decay, float brightness) {
		Random random = new Random(7);
		int from = Math.round(start * RATE);
		float filtered = 0f;
		for (int i = from; i < out.length; i++) {
			float t = (i - from) / (float) RATE;
			float envelope = (float) Math.exp(-t / decay);
			if (envelope < 0.001f) {
				break;
			}
			filtered += ((random.nextFloat() * 2f - 1f) - filtered) * brightness;
			out[i] += filtered * amplitude * envelope;
		}
	}

	/** A press: round and short, like a fingertip tapping glass. */
	private static float[] clickSound() {
		float[] out = buffer(0.12f);
		// the body drops slightly in pitch as it decays, which makes it a tap rather than a beep
		slide(out, 0f, 0.045f, 980f, 560f, 0.55f, 0.0008f);
		tone(out, 0f, 2900f, 0.10f, 0.0026f, 0.0004f);
		tone(out, 0f, 1480f, 0.10f, 0.006f, 0.0006f);
		touch(out, 0f, 0.22f, 0.0014f, 0.5f);
		return out;
	}

	/** A switch: two notes, the second higher when turning on and lower when turning off. */
	private static float[] toggleSound(boolean on) {
		float[] out = buffer(0.32f);
		float first = on ? 1175f : 1568f;
		float second = on ? 1760f : 1047f;
		touch(out, 0f, 0.14f, 0.0012f, 0.45f);
		bell(out, 0f, first, on ? 0.34f : 0.30f, 0.05f);
		bell(out, 0.052f, second, on ? 0.42f : 0.30f, on ? 0.085f : 0.07f);
		return out;
	}

	/** A notch: very short, pitched higher for higher notches. */
	private static float[] tickSound(int step) {
		float[] out = buffer(0.06f);
		float frequency = (float) (1500.0 * Math.pow(2.0, step / (double) (PITCHES - 1) * 1.1));
		tone(out, 0f, frequency, 0.30f, 0.0048f, 0.0005f);
		tone(out, 0f, frequency * 2.01f, 0.07f, 0.0025f, 0.0004f);
		touch(out, 0f, 0.07f, 0.0009f, 0.6f);
		return out;
	}

	/** A window: a soft swell rising when opening and falling when closing, with a little air. */
	private static float[] sweepSound(boolean opening) {
		float[] out = buffer(opening ? 0.26f : 0.2f);
		if (opening) {
			slide(out, 0f, 0.22f, 330f, 780f, 0.26f, 0.03f);
			slide(out, 0.02f, 0.2f, 660f, 1560f, 0.07f, 0.03f);
			touch(out, 0f, 0.05f, 0.05f, 0.08f);
		} else {
			slide(out, 0f, 0.17f, 760f, 300f, 0.22f, 0.012f);
			slide(out, 0f, 0.15f, 1520f, 600f, 0.05f, 0.012f);
		}
		return out;
	}

	/** Three rising notes, a chime. */
	private static float[] doneSound() {
		float[] out = buffer(0.55f);
		bell(out, 0f, 1319f, 0.28f, 0.07f);
		bell(out, 0.065f, 1760f, 0.30f, 0.08f);
		bell(out, 0.13f, 2349f, 0.32f, 0.13f);
		return out;
	}

	/** Two soft descending low notes: a "no", not a scolding. */
	private static float[] failSound() {
		float[] out = buffer(0.34f);
		bell(out, 0f, 440f, 0.32f, 0.06f);
		bell(out, 0.085f, 330f, 0.34f, 0.10f);
		return out;
	}

	/** Converts samples to 16-bit PCM at the requested peak, with a soft limiter so loud sums stay round instead of clipping, and a fade-out at the end to avoid a click. */
	private static byte[] toPcm(float[] samples, float peak) {
		float loudest = 0.0001f;
		for (float sample : samples) {
			loudest = Math.max(loudest, Math.abs(sample));
		}
		float gain = peak / loudest;
		byte[] bytes = new byte[samples.length * 2];
		int fade = RATE / 200;
		for (int i = 0; i < samples.length; i++) {
			float s = (float) Math.tanh(samples[i] * gain * 1.1f) / 1.1f;
			int left = samples.length - i;
			if (left < fade) {
				s *= left / (float) fade;
			}
			short value = (short) Math.round(s * Short.MAX_VALUE);
			bytes[i * 2] = (byte) (value & 0xFF);
			bytes[i * 2 + 1] = (byte) (value >> 8 & 0xFF);
		}
		return bytes;
	}
}
