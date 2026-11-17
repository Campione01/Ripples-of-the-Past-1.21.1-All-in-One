package rotp.addons.khnum.client;

import rotp.core.client.entityrender.stand.StandOpacityPolicy;

import rotp.addons.khnum.disguise.KhnumDisguiseState;
import net.minecraft.world.entity.LivingEntity;

final class KhnumVisuals {
    private KhnumVisuals() {}

    static RenderState resolve(
            LivingEntity user, float partialTick) {
        if (user == null
                || user.isInvisible()
                || user.isSpectator()) {
            return null;
        }
        KhnumDisguiseState disguise = KhnumDisguiseState.of(user);
        if (!disguise.isDisguised()) {
            return null;
        }
        // The look outlives the Stand, so it takes nothing from the Stand's fade.
        float alpha = StandOpacityPolicy.apply(1.0F, user);
        return alpha > 0.0F ? new RenderState(disguise, alpha) : null;
    }

    record RenderState(KhnumDisguiseState disguise, float alpha) {}
}
