package rotp.addons.whitesnake.disguise;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.entity.WhitesnakeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = WhitesnakeAddon.MOD_ID)
public final class WhitesnakeDisguiseEvents {
    private WhitesnakeDisguiseEvents() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        clearForUser(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event) {
        clearForUser(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        clearForUser(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        clearForUser(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        clearForUser(event.getOriginal());
        clearForUser(event.getEntity());
    }

    // A recall took the Stand's own tick away: losing the Stand is seen from the user's.
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living
                && !living.level().isClientSide()
                && living.hasData(WhitesnakeAddon.DISGUISE_STATE)
                && !WhitesnakeAddon.holdsWhitesnakeSkill(
                        living, "disguise")) {
            clearForUser(living);
        }
    }

    private static void clearForUser(LivingEntity user) {
        if (user == null || user.level().isClientSide()) {
            return;
        }
        StandPower power = StandPower.get(user);
        if (power != null
                && power.getSummonedStandEntity()
                        instanceof WhitesnakeEntity whitesnake) {
            whitesnake.clearDisguiseState();
        }
        WhitesnakeDisguiseState.clear(user);
    }
}
