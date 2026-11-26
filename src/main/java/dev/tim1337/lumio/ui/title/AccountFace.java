package dev.tim1337.lumio.ui.title;

import com.mojang.authlib.GameProfile;

import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * The player's own profile for the face on the main menu. It is the one the game fetches once as it starts: asking for it by id again would be a
 * second request to Mojang's session server, the one that also takes the logins, moments before the player joins a server.
 */
public final class AccountFace {
	private static volatile ResolvableProfile known;
	private static boolean asked;

	private AccountFace() {
	}

	/** @return the profile to draw, or null while the game's own request is still under way */
	public static ResolvableProfile profile(Minecraft client) {
		if (!asked) {
			asked = true;
			User user = client.getUser();
			GameProfile plain = new GameProfile(user.getProfileId(), user.getName());
			// off the render thread: the game's result blocks until its request is answered
			CompletableFuture.supplyAsync(client::getProfileResult, Util.nonCriticalIoPool()).handle((result, failure) -> {
				GameProfile found = failure == null && result != null ? result.profile() : null;
				known = ResolvableProfile.createResolved(found != null ? found : plain);
				return null;
			});
		}
		return known;
	}
}
