package rotp.addons.spin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import rotp.addons.spin.command.SpinCommands;
import rotp.addons.spin.ability.SpinLessonAbilities;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.effect.HemispatialNeglectEffect;
import rotp.addons.spin.power.SpinData;
import rotp.addons.spin.power.SpinFall;
import rotp.addons.spin.world.GyroSpawns;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;

@EventBusSubscriber(modid = SpinAddon.MOD_ID)
public final class SpinGameEvents {
    private SpinGameEvents() {}

    @SubscribeEvent
    public static void tickSpin(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpinData.of(player).ifPresent(data -> data.tickPlayer(player));
        }
    }

    @SubscribeEvent
    public static void tickVillages(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) GyroSpawns.tickVillages(level);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        SpinCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void changeTarget(LivingChangeTargetEvent event) {
        if (SpinConfig.WRECKING_NEGLECT_BLOCKS_RETARGET.get() && event.getEntity() instanceof Mob mob
                && !mob.level().isClientSide() && mob.hasEffect(SpinAddon.NEGLECT)
                && event.getNewAboutToBeSetTarget() != null
                && HemispatialNeglectEffect.onLeft(mob, event.getNewAboutToBeSetTarget())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void braceKnockback(LivingKnockBackEvent event) {
        if (!event.getEntity().level().isClientSide() && SpinConfig.BRACE_NO_KNOCKBACK.get()
                && SpinLessonAbilities.isBraced(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void correctSpinFall(LivingFallEvent event) {
        var entity = event.getEntity();
        if (SpinData.of(entity).isEmpty()) return;
        var power = PlayerPower.get(entity);
        if (power == null || !power.isLeapUnlocked()) return;
        var stand = StandPower.get(entity);
        float standLeap = stand != null && stand.hasPower() && stand.isLeapUnlocked() ? stand.leapStrength() : 0.0F;
        event.setDistance(SpinFall.correct(Math.max(entity.fallDistance, event.getDistance()),
                event.getDistance(), power.leapStrength(), standLeap, SpinConfig.LEAP_FALL_REDUCTION.get().floatValue()));
    }
}
