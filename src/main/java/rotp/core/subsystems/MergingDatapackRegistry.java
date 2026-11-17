package rotp.core.subsystems;

import java.io.Reader;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BinaryOperator;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;

import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;

/**
 * A datapack registry whose entry, when several packs hold the same file, is all of those files merged instead of the
 * top one only. Vanilla's own loading is kept for every other registry (RegistryDataLoaderLoaderMixin).
 */
public final class MergingDatapackRegistry {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<ResourceKey<? extends Registry<?>>, BinaryOperator<?>> MERGERS = new ConcurrentHashMap<>();
	private static final Set<ResourceKey<? extends Registry<?>>> LOADED = ConcurrentHashMap.newKeySet();

	private MergingDatapackRegistry() {}

	public static <E> void makeMerging(ResourceKey<? extends Registry<E>> registryKey, BinaryOperator<E> merger) {
		MERGERS.put(registryKey, merger);
	}

	/** The registries this has loaded from resources since the game started. */
	public static boolean hasLoaded(ResourceKey<? extends Registry<?>> registryKey) {
		return LOADED.contains(registryKey);
	}

	/** @return true if the registry merges, and it was loaded here instead of by vanilla. */
	@ApiStatus.Internal
	@SuppressWarnings("unchecked")
	public static <E> boolean onLoadFromResources(ResourceManager resourceManager, RegistryOps.RegistryInfoLookup registryInfoLookup,
			RegistryDataLoader.RegistryData<E> data, WritableRegistry<E> registry, Map<ResourceKey<?>, Exception> loadingErrors) {
		BinaryOperator<E> merger = (BinaryOperator<E>) MERGERS.get(data.key());
		if (merger == null) return false;
		loadContents(resourceManager, registryInfoLookup, registry, data.elementCodec(), merger, loadingErrors);
		LOADED.add(data.key());
		return true;
	}

	/**
	 * Vanilla's loadContentsFromManager, with every pack's copy of a file read in pack order and merged. A merged entry is
	 * registered as coming from no known pack: a client would otherwise read only its own top copy instead of being sent it.
	 */
	@ApiStatus.Internal
	public static <E> void loadContents(ResourceManager resourceManager, RegistryOps.RegistryInfoLookup registryInfoLookup,
			WritableRegistry<E> registry, Decoder<E> codec, BinaryOperator<E> merger, Map<ResourceKey<?>, Exception> loadingErrors) {
		FileToIdConverter fileToId = FileToIdConverter.json(Registries.elementsDirPath(registry.key()));
		RegistryOps<JsonElement> ops = new ConditionalOps<>(RegistryOps.create(JsonOps.INSTANCE, registryInfoLookup), ICondition.IContext.TAGS_INVALID);
		Decoder<Optional<E>> decoder = ConditionalOps.createConditionalCodec(NeoForgeExtraCodecs.decodeOnly(codec));

		for (Map.Entry<ResourceLocation, List<Resource>> entry : fileToId.listMatchingResourceStacks(resourceManager).entrySet()) {
			ResourceLocation file = entry.getKey();
			ResourceKey<E> key = ResourceKey.create(registry.key(), fileToId.fileToId(file));
			E merged = null;
			Optional<KnownPack> knownPack = Optional.empty();
			boolean allKnown = true;
			int count = 0;
			boolean failed = false;
			for (Resource resource : entry.getValue()) {
				try (Reader reader = resource.openAsReader()) {
					Optional<E> element = decoder.parse(ops, JsonParser.parseReader(reader)).getOrThrow();
					if (element.isEmpty()) {
						LOGGER.debug("Skipping {} from pack {} as its conditions were not met", key, resource.sourcePackId());
						continue;
					}
					merged = merged == null ? element.get() : merger.apply(merged, element.get());
					knownPack = resource.knownPackInfo();
					allKnown &= knownPack.isPresent();
					count++;
				}
				catch (Exception exception) {
					loadingErrors.put(key, new IllegalStateException(
							String.format(Locale.ROOT, "Failed to parse %s from pack %s", file, resource.sourcePackId()), exception));
					failed = true;
				}
			}
			if (merged == null || failed) continue;
			try {
				registry.register(key, merged, new RegistrationInfo(count == 1 ? knownPack : Optional.empty(),
						allKnown ? Lifecycle.stable() : Lifecycle.experimental()));
			}
			catch (Exception exception) {
				loadingErrors.put(key, new IllegalStateException(String.format(Locale.ROOT, "Failed to register %s", file), exception));
			}
		}
	}
}
