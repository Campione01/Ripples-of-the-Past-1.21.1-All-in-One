package rotp.addons.novemberrain.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The remake's destroy_item (DestroyItem): staminaCost(5), cooldown(20), resolveLevelToUnlock(3), holdToFire(15, false),
 * aimed at an entity (TargetRequirement.ENTITY). As it fires, the rain wears away every item the creatures within 3
 * blocks of its user hold or wear (players excepted), without a drop.
 */
public final class DestroyItemAbility extends NovemberRainStandAbility {
    public static final double ACTION_RADIUS = 3.0D;
    public static final float STAMINA_COST = 5.0F;
    public static final int COOLDOWN = 20;
    public static final int RESOLVE_LEVEL = 3;
    public static final int HOLD_TO_FIRE_TICKS = 15;
    public static final int PARTICLES_PER_TICK = 20;

    public DestroyItemAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, new Spec()
                .staminaCost(STAMINA_COST)
                .cooldown(COOLDOWN)
                .resolveLevelToUnlock(RESOLVE_LEVEL)
                .holdToFire(HOLD_TO_FIRE_TICKS)
                .targetRequirement(TargetRequirement.ENTITY));
    }

    @Override
    protected void standTickPerform(NovemberRainAction action, Level level, StandEntity stand, LivingEntity performerUser) {
        if (!(performerUser instanceof Player user)) {
            return;
        }
        if (level.isClientSide()) {
            for (int i = 0; i < PARTICLES_PER_TICK; i++) {
                double x = user.getX() + (level.random.nextDouble() - 0.5D) * ACTION_RADIUS * 2.0D;
                double y = user.getY() + 3.0D;
                double z = user.getZ() + (level.random.nextDouble() - 0.5D) * ACTION_RADIUS * 2.0D;
                level.addParticle(ParticleTypes.RAIN, x, y, z, 0.0D, -0.5D, 0.0D);
            }
            return;
        }
        for (Entity target : level.getEntities(user, user.getBoundingBox().inflate(ACTION_RADIUS))) {
            if (!(target instanceof LivingEntity living) || target instanceof Player) {
                continue;
            }
            boolean itemDestroyed = false;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (!living.getItemBySlot(slot).isEmpty()) {
                    living.setItemSlot(slot, ItemStack.EMPTY);
                    if (living instanceof Mob mob) {
                        mob.setDropChance(slot, 0.0F);
                    }
                    itemDestroyed = true;
                }
            }
            if (itemDestroyed) {
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }
}
