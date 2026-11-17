package rotp.addons.ger.state;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * The owner, 2026-11-08: for the Disabler's 9 seconds a disabled enemy
 * creature makes no special attack ("能力無效化期間敵方生物無法進行特殊攻擊").
 * Its plain melee and its arrows stay. What it shoots or summons besides is
 * taken out as it appears, what it would blow up does not explode, and the
 * harm it does by neither fist nor arrow does not land.
 */
@EventBusSubscriber(modid = GoldExperienceRequiemAddon.MOD_ID)
public final class GerDisabledAttacks {
    private static final long SONIC_BOOM_HOLD_TICKS = 20L;

    private GerDisabledAttacks() {}

    /** A creature the Disabler holds that counts as its caster's enemy. */
    public static boolean isDisabledEnemy(@Nullable Entity entity) {
        if (!(entity instanceof Mob mob) || mob.level().isClientSide()) {
            return false;
        }
        GerLivingState state =
                GoldExperienceRequiemAddon.livingState(mob);
        if (!state.disablerActive()) {
            return false;
        }
        UUID caster = state.disablerCaster();
        return GerSides.isHostile(
                caster != null
                        ? mob.level().getPlayerByUUID(caster)
                        : null,
                mob);
    }

    /** Every tick of a disabled creature: what no event can refuse is held off here. */
    public static void tick(LivingEntity living) {
        if (living instanceof Warden warden && isDisabledEnemy(warden)) {
            warden.getBrain().setMemoryWithExpiry(
                    MemoryModuleType.SONIC_BOOM_COOLDOWN,
                    Unit.INSTANCE,
                    SONIC_BOOM_HOLD_TICKS);
        }
    }

    /** The core asks it on both sides; only the server knows who is disabled. */
    public static boolean suppressesFuse(Creeper creeper) {
        return isDisabledEnemy(creeper);
    }

    // Fireballs, potions, bullets, skulls, fangs, vexes, breath clouds: all but arrows and tridents.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || event.loadedFromDisk()
                || !(event.getEntity() instanceof TraceableEntity owned)
                || event.getEntity() instanceof AbstractArrow
                // what a creature drops or hands over is no attack
                || event.getEntity() instanceof ItemEntity) {
            return;
        }
        if (isDisabledEnemy(owned.getOwner())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (isDisabledEnemy(event.getExplosion().getDirectSourceEntity())
                || isDisabledEnemy(
                        event.getExplosion().getIndirectSourceEntity())) {
            event.setCanceled(true);
        }
    }

    // A sonic boom, a guardian's beam, a roar: harm that is neither a blow of its body nor its arrow.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Mob)
                || event.getEntity().level().isClientSide()
                || source.getDirectEntity() instanceof AbstractArrow
                || isPlainMelee(source, attacker)
                || !isDisabledEnemy(attacker)) {
            return;
        }
        event.setCanceled(true);
    }

    private static boolean isPlainMelee(
            DamageSource source, Entity attacker) {
        if (source.getDirectEntity() != attacker
                || !(source.is(DamageTypes.MOB_ATTACK)
                        || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) {
            return false;
        }
        // A ravager's roar hurts as its bite does.
        return !(attacker instanceof Ravager ravager
                && ravager.getRoarTick() > 0);
    }
}
