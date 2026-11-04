package rotp.core.api.client.render;

import javax.annotation.Nullable;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * One owner's replacement of the texture a player's skin is drawn with.
 */
@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface PlayerSkinTextureProvider {
	/**
	 * @param skin the player's own skin (its texture, cape, elytra and model
	 *        type, {@link PlayerSkin#model()} telling a slim skin from a wide
	 *        one)
	 * @return the texture to draw this player's skin with, laid out as a skin
	 *         of the same model type, or null to leave it
	 */
	@Nullable
	ResourceLocation skinTexture(
			AbstractClientPlayer player,
			PlayerSkin skin);
}
