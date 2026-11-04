package rotp.core.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.core.JojoMod;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed providers of the texture a player's skin is drawn with.
 *
 * <p>Consulted where the client takes a player's skin
 * (AbstractClientPlayer#getSkin): the player renderer's body and its
 * first-person arms, and every core renderer that draws the skin. The first
 * non-null texture wins in registration order; the cape, the elytra and the
 * model type stay the player's own. Without a provider the skin is left
 * untouched. The registry retains no player or level state.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class PlayerSkinTextureProviders {
	private static final Map<ResourceLocation, PlayerSkinTextureProvider>
			PROVIDERS = new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private PlayerSkinTextureProviders() {}

	public static synchronized void register(
			ResourceLocation owner,
			PlayerSkinTextureProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate player skin texture provider: " + owner);
		}
		publishSnapshot();
	}

	@ApiStatus.Internal
	public static PlayerSkin apply(
			AbstractClientPlayer player,
			PlayerSkin skin) {
		List<Registration> registrations = snapshot;
		if (registrations.isEmpty() || skin == null) {
			return skin;
		}
		for (Registration registration : registrations) {
			try {
				ResourceLocation texture =
						registration.provider().skinTexture(player, skin);
				if (texture != null) {
					return texture.equals(skin.texture()) ? skin
							: new PlayerSkin(texture, skin.textureUrl(),
									skin.capeTexture(), skin.elytraTexture(),
									skin.model(), skin.secure());
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Player skin texture provider {} failed.",
						registration.owner(),
						error);
			}
		}
		return skin;
	}

	private static void publishSnapshot() {
		List<Registration> registrations =
				new ArrayList<>(PROVIDERS.size());
		PROVIDERS.forEach((owner, provider) ->
				registrations.add(new Registration(owner, provider)));
		snapshot = List.copyOf(registrations);
	}

	private record Registration(
			ResourceLocation owner,
			PlayerSkinTextureProvider provider) {}
}
