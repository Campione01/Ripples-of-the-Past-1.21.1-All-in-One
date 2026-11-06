package rotp.core.api.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed presentation policies for the client sky.
 *
 * <p>Among active providers the highest priority wins; equal priorities are
 * ordered by owner ID. Registration order does not matter. Policies only
 * transform sky values and compatible shader sky uniform inputs; they do not
 * mutate level time, game rules, framebuffers, or shader programs. Resolution is
 * stateless, so resource reload and disconnect require no retained-world
 * cleanup.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkyPresentationProviders {
	public static final int DEFAULT_PRIORITY = 0;
	/** For a presentation no other may replace, such as a time erase's. */
	public static final int HIGHEST_PRIORITY = Integer.MAX_VALUE;

	private static final Map<ResourceLocation, Registration> PROVIDERS =
			new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private ClientSkyPresentationProviders() {}

	public static synchronized void register(
			ResourceLocation owner,
			ClientSkyPresentationProvider provider) {
		register(owner, DEFAULT_PRIORITY, provider);
	}

	public static synchronized void register(
			ResourceLocation owner,
			int priority,
			ClientSkyPresentationProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(
				owner,
				new Registration(owner, priority, provider)) != null) {
			throw new IllegalStateException(
					"Duplicate client sky presentation provider: "
							+ owner);
		}
		publishSnapshot();
	}

	public static float timeOfDay(
			ClientLevel level,
			float partialTick,
			float original) {
		ResolvedPresentation resolved = resolve(level);
		if (resolved == null) {
			return original;
		}
		try {
			return resolved.presentation()
					.timeOfDay(original, partialTick);
		}
		catch (RuntimeException error) {
			logFailure(resolved.owner(), "time of day", error);
			return original;
		}
	}

	public static float shaderTimeOfDay(
			ClientLevel level, float partialTick, float original) {
		float presented = timeOfDay(level, partialTick, original);
		return Float.isFinite(presented) && presented >= 0.0F && presented < 1.0F
				? presented : original;
	}

	public static int shaderWorldTime(
			ClientLevel level, float partialTick, int original, float originalSkyAngle) {
		return worldTimeFromSkyAngle(original, originalSkyAngle,
				shaderTimeOfDay(level, partialTick, originalSkyAngle));
	}

	static int worldTimeFromSkyAngle(int original, float originalSkyAngle, float presentedSkyAngle) {
		if (presentedSkyAngle == originalSkyAngle || !Float.isFinite(presentedSkyAngle)
				|| presentedSkyAngle < 0.0F || presentedSkyAngle >= 1.0F) {
			return original;
		}
		// Invert DimensionType.timeOfDay's monotonic daylight curve, not angle * 24000.
		double low = 0.0D;
		double high = 1.0D;
		for (int i = 0; i < 32; i++) {
			double phase = (low + high) * 0.5D;
			double angle = (2.0D * phase + 0.5D - Math.cos(Math.PI * phase) * 0.5D) / 3.0D;
			if (angle < presentedSkyAngle) low = phase;
			else high = phase;
		}
		return Math.floorMod((int) Math.round(((low + high) * 0.5D + 0.25D) * 24000.0D), 24000);
	}

	public static float skyDarken(
			ClientLevel level,
			float partialTick,
			float original) {
		ResolvedPresentation resolved = resolve(level);
		if (resolved == null) {
			return original;
		}
		try {
			return resolved.presentation()
					.skyDarken(original, partialTick);
		}
		catch (RuntimeException error) {
			logFailure(resolved.owner(), "sky darken", error);
			return original;
		}
	}

	public static float starBrightness(
			ClientLevel level,
			float partialTick,
			float original) {
		ResolvedPresentation resolved = resolve(level);
		if (resolved == null) {
			return original;
		}
		try {
			return resolved.presentation()
					.starBrightness(original, partialTick);
		}
		catch (RuntimeException error) {
			logFailure(
					resolved.owner(),
					"star brightness",
					error);
			return original;
		}
	}

	public static Vec3 skyColor(
			ClientLevel level,
			Vec3 cameraPosition,
			float partialTick,
			Vec3 original) {
		ResolvedPresentation resolved = resolve(level);
		if (resolved == null) {
			return original;
		}
		try {
			return Objects.requireNonNull(
					resolved.presentation().skyColor(
							original,
							cameraPosition,
							partialTick),
					"sky color");
		}
		catch (RuntimeException error) {
			logFailure(resolved.owner(), "sky color", error);
			return original;
		}
	}

	@Nullable
	private static ResolvedPresentation resolve(
			ClientLevel level) {
		ClientSkyPresentationQuery query =
				new ClientSkyPresentationQuery(level);
		for (Registration registration : snapshot) {
			try {
				ClientSkyPresentation presentation =
						registration.provider().presentation(query);
				if (presentation != null) {
					return new ResolvedPresentation(
							registration.owner(),
							presentation);
				}
			}
			catch (RuntimeException error) {
				logFailure(
						registration.owner(),
						"policy resolution",
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
				"Client sky presentation provider {} failed during {}.",
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
			ClientSkyPresentationProvider provider) {}

	private record ResolvedPresentation(
			ResourceLocation owner,
			ClientSkyPresentation presentation) {}
}
