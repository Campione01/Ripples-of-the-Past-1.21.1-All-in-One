package rotp.addons.ger;

import rotp.core.api.control.CreeperFuseSuppressionProviders;
import rotp.core.api.damage.DefencePiercing;
import rotp.core.api.power.AbilityRefusals;
import rotp.core.api.time.TimeEraseWitnesses;
import rotp.addons.ger.damage.GerUnavoidableDamage;
import rotp.addons.ger.state.GerDisabledAttacks;
import rotp.addons.ger.state.GerRtzRewindService;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class GoldExperienceRequiemModEvents {
    private GoldExperienceRequiemModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                GoldExperienceRequiemAddon.GER_STAND_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    public static void commonSetup(FMLCommonSetupEvent event) {
        AbilityRefusals.register(
                GoldExperienceRequiemAddon.id("disabler"),
                GoldExperienceRequiemGameEvents::refuseWhileDisabled);
        CreeperFuseSuppressionProviders.register(
                GoldExperienceRequiemAddon.id("disabler"),
                GerDisabledAttacks::suppressesFuse);
        // The owner, 2026-11-08: a Requiem holder, his Stand out or not, sees the eraser and keeps his own place.
        TimeEraseWitnesses.register(
                GoldExperienceRequiemAddon.id("requiem_holder"),
                (witness, eraser, question) ->
                        GoldExperienceRequiemAddon.isGerUser(witness));
        // The owner, 2026-11-08: the holder's melee while Requiem is summoned is not avoided either.
        DefencePiercing.registerAttackerRule(
                GoldExperienceRequiemAddon.id("requiem_holder_melee"),
                GerUnavoidableDamage::holderMeleePierces);
        // King Crimson's Epitaph exemption and its answer to a creature's erase, when it is loaded.
        GerRtzRewindService.registerKingCrimsonHooks();
    }
}
