package rotp.addons.survivor.event;

import rotp.addons.survivor.SurvivorAddon;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SurvivorAddon.MOD_ID)
public final class SurvivorEvents {
    private SurvivorEvents() {}

    // 1.16 LivingUpdateEvent, which ran before the entity's own tick; 1.16 had two listeners on it
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) {
            return;
        }
        MobAggroHandler.onLivingTick(entity);
        SurvivorEventHandler.onLivingTick(entity);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MobAggroHandler.onServerTickEnd();
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity) {
            MobAggroHandler.forget(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MobAggroHandler.clear();
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        PlayerAttackHandler.onPlayerAttack(event);
    }
}
