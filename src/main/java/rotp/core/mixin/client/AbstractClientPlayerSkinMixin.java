package rotp.core.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import rotp.core.api.client.render.PlayerSkinTextureProviders;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;

// The skin texture of add-on looks (PlayerSkinTextureProviders), as 1.16 add-ons replaced getSkinTextureLocation
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSkinMixin {

	@ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
	private PlayerSkin jojo_ripples$addonSkinTexture(PlayerSkin skin) {
		return PlayerSkinTextureProviders.apply((AbstractClientPlayer) (Object) this, skin);
	}
}
