package rotp.addons.yellowtemperance.client;

import rotp.core.client.entityrender.stand.StandOpacityPolicy;

import rotp.addons.yellowtemperance.disguise.YellowTemperanceDisguiseState;
import net.minecraft.world.entity.LivingEntity;

final class YellowTemperanceVisuals {
    private YellowTemperanceVisuals() {}

    // 1.16 DisguiseRenderHandler drew the flesh disguise for every viewer, one who cannot see Stands too; only the fake
    // Stand beside the user is a Stand (YellowTemperanceDisguiseLayer.renderBesideStand)
    static RenderState resolve(
            LivingEntity user, float partialTick) {
        if (user.isInvisible()
                || user.isSpectator()) {
            return null;
        }
        YellowTemperanceDisguiseState disguise =
                YellowTemperanceDisguiseState.of(user);
        if (disguise.isEmpty()) {
            return null;
        }
        // The look outlives the Stand, so it takes nothing from the Stand's fade.
        float alpha = StandOpacityPolicy.apply(1.0F, user);
        return alpha > 0.0F ? new RenderState(disguise, alpha) : null;
    }

    record RenderState(
            YellowTemperanceDisguiseState disguise, float alpha) {}
}
