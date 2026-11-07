package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.CiderCheck;
import dev.tim1337.lumio.features.music.MusicService.CiderState;

import java.util.concurrent.CompletableFuture;

/**
 * Connecting Cider, shared by the setup of the Music page and the first-start setup: looks for Cider now and then, and tests a token once when asked,
 * both off the render thread; the answers are taken when they come. The token is only passed on to {@link MusicService#checkCider}; it is not kept
 * here and never logged.
 */
public final class CiderConnect {
	private static final float LOOK_EVERY_SECONDS = 2f;

	private float lookedFor = LOOK_EVERY_SECONDS;
	private CompletableFuture<CiderCheck> looking;
	private CompletableFuture<CiderCheck> testing;
	private CiderCheck looked;
	private CiderCheck tested;
	private boolean found;

	/**
	 * Looks for Cider when it is time, and takes the answers that came.
	 *
	 * @return the answer of a token test that came just now, or null
	 */
	public CiderCheck advance(float dt) {
		lookedFor += dt;
		if (looking == null && lookedFor >= LOOK_EVERY_SECONDS) {
			lookedFor = 0f;
			looking = MusicService.checkCider("");
		}
		if (looking != null && looking.isDone()) {
			looked = looking.getNow(null);
			found = looked != null && looked.state() != CiderState.NOT_RUNNING;
			looking = null;
		}
		if (testing != null && testing.isDone()) {
			tested = testing.getNow(null);
			testing = null;
			found |= tested != null && tested.state() != CiderState.NOT_RUNNING;
			return tested;
		}
		return null;
	}

	/** Tests a token: Cider is asked once; the answer comes through {@link #advance}. */
	public void test(String token) {
		tested = null;
		testing = MusicService.checkCider(token);
	}

	/** Looks for Cider again at the next frame, for "Try again". */
	public void lookNow() {
		lookedFor = LOOK_EVERY_SECONDS;
	}

	/** Forgets the last test, when the token changed. */
	public void forgetTest() {
		tested = null;
	}

	/** @return whether Cider answered the last time it was asked */
	public boolean found() {
		return found;
	}

	/** @return whether it was asked at all and has answered, so "not found" means something */
	public boolean answered() {
		return looked != null || tested != null;
	}

	/** @return whether Cider answers without any token, as it does with its token setting off */
	public boolean needsNoToken() {
		return looked != null && looked.state() == CiderState.CONNECTED;
	}

	public boolean testing() {
		return testing != null;
	}

	/** @return the last test's answer, or null */
	public CiderCheck tested() {
		return tested;
	}

	/** @return whether the last test found the token works */
	public boolean works() {
		return tested != null && tested.state() == CiderState.CONNECTED;
	}
}
