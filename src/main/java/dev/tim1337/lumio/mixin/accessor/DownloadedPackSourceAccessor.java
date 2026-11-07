package dev.tim1337.lumio.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.RepositorySource;

import java.util.List;

/**
 * The game's own handling of server packs, for Keep server resource packs: its load of server packs (to remove kept ones), and its pack loader
 * and source (to load the saved packs with the game at its start).
 */
@Mixin(DownloadedPackSource.class)
public interface DownloadedPackSourceAccessor {
	@Invoker("startReload")
	void lumio$startReload(PackReloadConfig.Callbacks callbacks);

	/** @return the packs opened from the files, or null if one of them could not be opened */
	@Invoker("loadRequestedPacks")
	List<Pack> lumio$loadRequestedPacks(List<PackReloadConfig.IdAndPath> packs);

	@Invoker("configureSource")
	static RepositorySource lumio$configureSource(List<Pack> packs) {
		throw new AssertionError();
	}

	@Accessor("packSource")
	void lumio$setPackSource(RepositorySource source);
}
