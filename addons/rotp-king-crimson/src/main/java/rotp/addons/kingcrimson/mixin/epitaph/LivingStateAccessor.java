package rotp.addons.kingcrimson.mixin.epitaph;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** What the damage of a room reads and writes on a living phantom, and the loot methods it runs for a death. */
@Mixin(LivingEntity.class)
public interface LivingStateAccessor {
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> kingCrimson$healthData() {
        throw new AssertionError();
    }

    @Accessor("lastHurt")
    float kingCrimson$lastHurt();

    @Accessor("lastHurt")
    void kingCrimson$setLastHurt(float amount);

    @Nullable
    @Accessor("lastHurtByPlayer")
    Player kingCrimson$lastHurtByPlayer();

    @Accessor("lastHurtByPlayer")
    void kingCrimson$setLastHurtByPlayer(@Nullable Player player);

    @Accessor("lastHurtByPlayerTime")
    int kingCrimson$lastHurtByPlayerTime();

    @Accessor("lastHurtByPlayerTime")
    void kingCrimson$setLastHurtByPlayerTime(int ticks);

    @Accessor("dead")
    void kingCrimson$setDead(boolean dead);

    @Accessor("attackStrengthTicker")
    int kingCrimson$attackStrengthTicker();

    @Accessor("attackStrengthTicker")
    void kingCrimson$setAttackStrengthTicker(int ticks);

    @Invoker("getDamageAfterArmorAbsorb")
    float kingCrimson$afterArmor(DamageSource source, float amount);

    @Invoker("getDamageAfterMagicAbsorb")
    float kingCrimson$afterMagic(DamageSource source, float amount);

    @Invoker("hurtCurrentlyUsedShield")
    void kingCrimson$hurtShield(float amount);

    @Invoker("hurtHelmet")
    void kingCrimson$hurtHelmet(DamageSource source, float amount);

    @Invoker("blockUsingShield")
    void kingCrimson$blockUsingShield(LivingEntity attacker);

    @Nullable
    @Invoker("getHurtSound")
    SoundEvent kingCrimson$hurtSound(DamageSource source);

    @Nullable
    @Invoker("getDeathSound")
    SoundEvent kingCrimson$deathSound();

    @Invoker("shouldDropLoot")
    boolean kingCrimson$shouldDropLoot();

    @Invoker("dropFromLootTable")
    void kingCrimson$dropFromLootTable(DamageSource source, boolean hitByPlayer);

    @Invoker("dropCustomDeathLoot")
    void kingCrimson$dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit);

    @Invoker("dropEquipment")
    void kingCrimson$dropEquipment();

    @Invoker("dropExperience")
    void kingCrimson$dropExperience(@Nullable Entity killer);

    @Invoker("isAlwaysExperienceDropper")
    boolean kingCrimson$alwaysDropsExperience();

    @Invoker("getRiddenSpeed")
    float kingCrimson$riddenSpeed(net.minecraft.world.entity.player.Player rider);

    // NeoForge keeps the hit being taken here; the armour and effect reductions of vanilla read its top.
    @Accessor(value = "damageContainers", remap = false)
    java.util.Stack<net.neoforged.neoforge.common.damagesource.DamageContainer> kingCrimson$damageContainers();
}
