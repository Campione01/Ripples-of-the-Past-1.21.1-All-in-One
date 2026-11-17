package rotp.core.api.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.core.JojoMod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed replacements for the vanilla client sky renderer.
 *
 * <p>Among active providers the highest priority wins; equal priorities are
 * ordered by owner ID. Registration order does not matter. Cloud suppression
 * follows the same winner. Provider and renderer failures are logged and fall
 * back to the vanilla sky path.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkyRenderers {
	public static final int DEFAULT_PRIORITY = 0;
	/** For a background no other sky may replace, such as a time erase's. */
	public static final int HIGHEST_PRIORITY = Integer.MAX_VALUE;

	private static final Map<ResourceLocation, Registration>
			PROVIDERS = new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private ClientSkyRenderers() {}

	public static synchronized void register(
			ResourceLocation owner,
			ClientSkyRendererProvider provider) {
		register(owner, DEFAULT_PRIORITY, provider);
	}

	public static synchronized void register(
			ResourceLocation owner,
			int priority,
			ClientSkyRendererProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(
				owner,
				new Registration(owner, priority, provider)) != null) {
			throw new IllegalStateException(
					"Duplicate client sky renderer provider: " + owner);
		}
		publishSnapshot();
	}

	@ApiStatus.Internal
	public static boolean renderSky(ClientSkyRenderContext context) {
		Objects.requireNonNull(context, "context");
		ResolvedRenderer resolved = resolve(context.level());
		if (resolved == null) {
			return false;
		}
		try {
			resolved.renderer().render(context);
			return true;
		}
		catch (RuntimeException error) {
			logFailure(resolved.owner(), "sky render", error);
			return false;
		}
	}

	@ApiStatus.Internal
	public static boolean suppressesClouds(ClientLevel level) {
		ResolvedRenderer resolved = resolve(level);
		if (resolved == null) {
			return false;
		}
		try {
			return resolved.renderer().suppressVanillaClouds();
		}
		catch (RuntimeException error) {
			logFailure(resolved.owner(), "cloud suppression", error);
			return false;
		}
	}

	@Nullable
	private static ResolvedRenderer resolve(ClientLevel level) {
		ClientSkyRendererQuery query = new ClientSkyRendererQuery(level);
		for (Registration registration : snapshot) {
			try {
				ClientSkyRenderer renderer =
						registration.provider().renderer(query);
				if (renderer != null) {
					return new ResolvedRenderer(
							registration.owner(), renderer);
				}
			}
			catch (RuntimeException error) {
				logFailure(
						registration.owner(),
						"provider resolution",
						error);
			}
		}
		return null;
	}

	private static void logFailure(
			ResourceLocation owner,
			String phase,
			RuntimeException error) {
		JojoMod.getLogger().error(
				"Client sky renderer provider {} failed during {}.",
				owner,
				phase,
				error);
	}

	private static void publishSnapshot() {
		List<Registration> registrations =
				new ArrayList<>(PROVIDERS.values());
		registrations.sort(Comparator
				.<Registration>comparingInt(Registration::priority)
				.reversed()
				.thenComparing(Registration::owner));
		snapshot = List.copyOf(registrations);
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(PROVIDERS.keySet());
	}

	static List<ResourceLocation> resolutionOrder() {
		return snapshot.stream().map(Registration::owner).toList();
	}

	static synchronized void resetForTests() {
		PROVIDERS.clear();
		snapshot = List.of();
	}

	private record Registration(
			ResourceLocation owner,
			int priority,
			ClientSkyRendererProvider provider) {}

	private record ResolvedRenderer(
			ResourceLocation owner,
			ClientSkyRenderer renderer) {}
}
