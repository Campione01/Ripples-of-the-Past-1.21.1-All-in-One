package rotp.addons.kingcrimson.epitaph.sandbox;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.mixin.epitaph.LivingStateAccessor;

/**
 * A hit inside a room. It follows LivingEntity.hurt step by step, on the phantom alone and without the events of a
 * real hit, which other mods listen to: the shield, the frames after a hit, armour, effects and enchantments,
 * absorption, health, the knockback, a totem, death and its loot. What it does to the phantom stays in the room; what
 * is recorded is the hit as it was passed in, to be dealt for real, once, when the bodies get there. A victim that is
 * not a phantom of the room is refused: nothing real can be hurt from a room.
 */
public final class SandboxDamage {
    // A hit on a human phantom as it was dealt, before the player scaled it by the difficulty. Weakly: a hit refused
    // before it is recorded leaves it set, and a held phantom keeps its level and server alive.
    @Nullable
    private static WeakReference<Player> dealtTo;
    private static float dealtAmount;

    private SandboxDamage() {}

    /** Read by the game tests. */
    @Nullable
    public static Player dealtToForTests() {
        return dealtTo != null ? dealtTo.get() : null;
    }

    /** From the head of Player.hurt in a room. */
    public static void dealt(Player victim, float amount) {
        dealtTo = new WeakReference<>(victim);
        dealtAmount = amount;
    }

    public static boolean hurt(LivingEntity victim, DamageSource source, float amount) {
        SandboxRoom room = EpitaphSandbox.room();
        if (room == null || !room.owns(victim)) {
            EpitaphSandbox.leak("room code hurt a real " + BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()));
            return false;
        }
        if (victim.isInvulnerableTo(source) || victim.isDeadOrDying()
                || source.is(DamageTypeTags.IS_FIRE) && victim.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        }
        Stack<DamageContainer> hits = ((LivingStateAccessor) victim).kingCrimson$damageContainers();
        DamageContainer hit = new DamageContainer(source, amount);
        hits.push(hit);
        boolean landed;
        boolean killed = false;
        // The real hit does all of this again when it is dealt: nothing in here is a recorded op of its own.
        room.mute();
        try {
            landed = take(victim, source, hit);
            if (landed && victim.isDeadOrDying() && !savedByTotem(victim, source)) {
                killed = true;
            }
        }
        finally {
            room.unmute();
            hits.remove(hit);
        }
        if (landed) {
            // The real player scales the real hit himself.
            float asDealt = dealtTo != null && dealtTo.get() == victim ? dealtAmount : amount;
            room.record(new RecordedOp.Damage(EpitaphSandbox.actorKey(), ActorKey.of(victim), source, asDealt,
                    victim.getHealth()));
        }
        dealtTo = null;
        if (killed) {
            die(room, victim, source);
        }
        return landed;
    }

    private static boolean take(LivingEntity victim, DamageSource source, DamageContainer hit) {
        LivingStateAccessor state = (LivingStateAccessor) victim;
        float amount = hit.getNewDamage();
        if (victim.isSleeping()) {
            victim.stopSleeping();
        }
        victim.setNoActionTime(0);
        boolean blocked = false;
        if (amount > 0.0F && victim.isDamageSourceBlocked(source)) {
            state.kingCrimson$hurtShield(amount);
            if (!source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof LivingEntity attacker) {
                state.kingCrimson$blockUsingShield(attacker);
            }
            amount = 0.0F;
            blocked = true;
        }
        if (source.is(DamageTypeTags.IS_FREEZING) && victim.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
            amount *= 5.0F;
        }
        if (source.is(DamageTypeTags.DAMAGES_HELMET) && !victim.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            state.kingCrimson$hurtHelmet(source, amount);
            amount *= 0.75F;
        }
        hit.setNewDamage(amount);
        victim.walkAnimation.setSpeed(1.5F);
        boolean fresh = true;
        if (victim.invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            if (amount <= state.kingCrimson$lastHurt()) {
                return false;
            }
            hit.setReduction(DamageContainer.Reduction.INVULNERABILITY, state.kingCrimson$lastHurt());
            lose(victim, source, hit);
            state.kingCrimson$setLastHurt(amount);
            fresh = false;
        } else {
            state.kingCrimson$setLastHurt(amount);
            victim.invulnerableTime = hit.getPostAttackInvulnerabilityTicks();
            lose(victim, source, hit);
            victim.hurtDuration = 10;
            victim.hurtTime = victim.hurtDuration;
        }
        amount = hit.getNewDamage();
        blame(victim, source, state);
        if (fresh) {
            if (blocked) {
                victim.level().broadcastEntityEvent(victim, (byte) 29);
            } else {
                victim.level().broadcastDamageEvent(victim, source);
            }
            if (!source.is(DamageTypeTags.NO_IMPACT) && (!blocked || amount > 0.0F)) {
                victim.hurtMarked = true;
            }
            if (!source.is(DamageTypeTags.NO_KNOCKBACK)) {
                double x = 0.0D;
                double z = 0.0D;
                if (source.getDirectEntity() instanceof Projectile projectile) {
                    var direction = projectile.calculateHorizontalHurtKnockbackDirection(victim, source);
                    x = -direction.leftDouble();
                    z = -direction.rightDouble();
                } else if (source.getSourcePosition() != null) {
                    x = source.getSourcePosition().x() - victim.getX();
                    z = source.getSourcePosition().z() - victim.getZ();
                }
                knockBack(victim, 0.4D, x, z);
                if (!blocked) {
                    victim.indicateDamage(x, z);
                }
            }
        }
        return !blocked || amount > 0.0F;
    }

    // LivingEntity.actuallyHurt and the player's own, without the damage events.
    private static void lose(LivingEntity victim, DamageSource source, DamageContainer hit) {
        if (victim.isInvulnerableTo(source)) {
            return;
        }
        LivingStateAccessor state = (LivingStateAccessor) victim;
        hit.setReduction(DamageContainer.Reduction.ARMOR, hit.getNewDamage() - state.kingCrimson$afterArmor(source, hit.getNewDamage()));
        state.kingCrimson$afterMagic(source, hit.getNewDamage());
        float absorbed = Math.min(victim.getAbsorptionAmount(), hit.getNewDamage());
        hit.setReduction(DamageContainer.Reduction.ABSORPTION, absorbed);
        victim.setAbsorptionAmount(Math.max(0.0F, victim.getAbsorptionAmount() - absorbed));
        float taken = hit.getNewDamage();
        if (taken != 0.0F) {
            if (victim instanceof Player player) {
                player.causeFoodExhaustion(source.getFoodExhaustion());
            }
            victim.getCombatTracker().recordDamage(source, taken);
            victim.setHealth(victim.getHealth() - taken);
        }
    }

    private static void blame(LivingEntity victim, DamageSource source, LivingStateAccessor state) {
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living && !source.is(DamageTypeTags.NO_ANGER)
                && (!source.is(DamageTypes.WIND_CHARGE) || !victim.getType().is(EntityTypeTags.NO_ANGER_FROM_WIND_CHARGE))) {
            victim.setLastHurtByMob(living);
        }
        if (attacker instanceof Player player) {
            state.kingCrimson$setLastHurtByPlayerTime(100);
            state.kingCrimson$setLastHurtByPlayer(player);
        } else if (attacker instanceof TamableAnimal tamed && tamed.isTame()) {
            state.kingCrimson$setLastHurtByPlayerTime(100);
            state.kingCrimson$setLastHurtByPlayer(tamed.getOwner() instanceof Player owner ? owner : null);
        }
    }

    /** LivingEntity.knockback, without its event. */
    public static void knockBack(LivingEntity victim, double strength, double x, double z) {
        strength *= 1.0D - victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        if (strength <= 0.0D) {
            return;
        }
        victim.hasImpulse = true;
        Vec3 motion = victim.getDeltaMovement();
        while (x * x + z * z < 1.0E-5F) {
            x = (victim.getRandom().nextDouble() - victim.getRandom().nextDouble()) * 0.01D;
            z = (victim.getRandom().nextDouble() - victim.getRandom().nextDouble()) * 0.01D;
        }
        Vec3 push = new Vec3(x, 0.0D, z).normalize().scale(strength);
        victim.setDeltaMovement(motion.x / 2.0D - push.x,
                victim.onGround() ? Math.min(0.4D, motion.y / 2.0D + strength) : motion.y, motion.z / 2.0D - push.z);
    }

    private static boolean savedByTotem(LivingEntity victim, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = victim.getItemInHand(hand);
            if (held.is(Items.TOTEM_OF_UNDYING)) {
                held.shrink(1);
                victim.setHealth(1.0F);
                victim.removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM);
                victim.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
                victim.level().broadcastEntityEvent(victim, (byte) 35);
                return true;
            }
        }
        return false;
    }

    /**
     * The death of a phantom: its loot is rolled here, from the random streams of the room, and becomes phantoms where
     * it died; the body dies for real at this tick and drops exactly these stacks. Nothing else of a death happens in
     * the room: what a death does to the world, it does once, when it is real.
     */
    public static void die(SandboxRoom room, LivingEntity victim, DamageSource source) {
        SandboxRoom.Phantom phantom = room.info(victim);
        if (phantom == null || phantom.ended() || phantom.dying) {
            return;
        }
        phantom.dying = true;
        LivingStateAccessor state = (LivingStateAccessor) victim;
        ServerLevel level = room.level();
        boolean byPlayer = state.kingCrimson$lastHurtByPlayerTime() > 0;
        int experience = 0;
        Collection<ItemEntity> dropped;
        List<Entity> others;
        room.mute();
        room.beginDeathLoot();
        victim.captureDrops(new ArrayList<>());
        try {
            if (state.kingCrimson$shouldDropLoot() && level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
                state.kingCrimson$dropFromLootTable(source, byPlayer);
                state.kingCrimson$dropCustomDeathLoot(level, source, byPlayer);
            }
            state.kingCrimson$dropEquipment();
            if (!victim.wasExperienceConsumed() && (state.kingCrimson$alwaysDropsExperience()
                    || byPlayer && victim.shouldDropExperience() && level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT))) {
                experience = victim.getExperienceReward(level, source.getEntity());
                ExperienceOrb.award(level, victim.position(), experience);
            }
        }
        finally {
            dropped = victim.captureDrops(null);
            others = room.endDeathLoot();
            room.unmute();
        }
        List<RecordedOp.Drop> drops = new ArrayList<>();
        if (dropped != null) {
            for (ItemEntity item : dropped) {
                room.addLoot(item, phantom.owner);
                drops.add(new RecordedOp.Drop(ActorKey.of(item), item.getItem().copy(), item.saveWithoutId(new CompoundTag())));
            }
        }
        for (Entity other : others) {
            room.addLoot(other, phantom.owner);
        }
        state.kingCrimson$setDead(true);
        room.record(new RecordedOp.Death(EpitaphSandbox.actorKey(), phantom.key, source, drops, experience));
        // The dead leave a room at once, and a Stand does not tick with nobody to be the Stand of.
        PhantomStands.leaveWith(victim);
    }
}
