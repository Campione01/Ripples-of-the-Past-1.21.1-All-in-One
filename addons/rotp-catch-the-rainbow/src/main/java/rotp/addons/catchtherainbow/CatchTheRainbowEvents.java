package rotp.addons.catchtherainbow;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import rotp.addons.catchtherainbow.ability.TempHealthAbility;
import rotp.addons.catchtherainbow.item.UmbrellaItem;
import rotp.addons.catchtherainbow.util.RainExposure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = CatchTheRainbowAddon.MOD_ID)
public final class CatchTheRainbowEvents {
    private CatchTheRainbowEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                CatchTheRainbowAddon.CATCH_THE_RAINBOW_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(player);
        if (controller == null) {
            controller = CatchTheRainbowEntity.recalled(player);
        }
        // Merge now stays on through dry weather, so the block keeps to
        // the rain it was written for.
        if (controller != null
                && controller.isMergeActive()
                && (CatchTheRainbowAddon.isCreative(player)
                        || RainExposure.isExposedToRain(player))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }
        UmbrellaItem.tickPlayer(player);
        boolean dry = !player.getAbilities().instabuild
                && !RainExposure.isExposedToRain(player);
        // 1.16 GameplayHandler ran this for every player, summoned or
        // not: dry weather ends Rain Redemption and Temp Health, and
        // while Redemption lasts resolve is kept full.
        if (dry && player.hasEffect(
                CatchTheRainbowAddon.RAIN_REDEMPTION_EFFECT)) {
            player.removeEffect(
                    CatchTheRainbowAddon.RAIN_REDEMPTION_EFFECT);
        }
        if (dry) {
            TempHealthAbility.clear(player);
        }
        TempHealthAbility.pruneAbsorptionMark(player);
        CatchTheRainbowEntity.tickRedemptionResolve(
                StandPower.get(player), player);
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(player);
        if (controller == null) {
            CatchTheRainbowEntity recalled =
                    CatchTheRainbowEntity.recalled(player);
            if (recalled != null) {
                recalled.tickRecalled(player);
            }
            else {
                CatchTheRainbowCleanup.onControllerMissing(player);
            }
            return;
        }
        TempHealthAbility.adoptStandlessCut(player, controller);
        controller.updateRainWalking(player);
        // Dry weather takes the Rain Merge effect off but leaves Merge
        // and Falling Rain Blades on, as 1.16 did.
        if (dry) {
            player.removeEffect(
                    CatchTheRainbowAddon.RAIN_MERGE_EFFECT);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            CatchTheRainbowEntity controller =
                    CatchTheRainbowAddon.controller(player);
            CatchTheRainbowEntity.forgetRecalled(player);
            CatchTheRainbowCleanup.clearTransientState(
                    player, controller);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        CatchTheRainbowCleanup.onControllerMissing(
                event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(
                        event.getEntity());
        CatchTheRainbowEntity.forgetRecalled(event.getEntity());
        CatchTheRainbowCleanup.onLogout(
                event.getEntity(), controller);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(
                        event.getOriginal());
        CatchTheRainbowEntity.forgetRecalled(event.getOriginal());
        CatchTheRainbowCleanup.clearTransientState(
                event.getOriginal(), controller);
        CatchTheRainbowCleanup.clearTransientState(
                event.getEntity(), null);
    }
}
