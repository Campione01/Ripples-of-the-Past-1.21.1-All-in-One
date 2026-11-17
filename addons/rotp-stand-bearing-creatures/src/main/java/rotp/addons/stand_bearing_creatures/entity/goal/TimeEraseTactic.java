package rotp.addons.stand_bearing_creatures.entity.goal;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

/**
 * When a creature that carries King Crimson erases time. The owner, 2026-11-11: such a creature uses the erase in a
 * fight with a player, for a player's cost and cooldown, and "應該把生物替身使者的時刪概率增高，最好是和玩家相距10m內優先高概率發動
 * 一次，防止發動晚了生物替身使者死亡". The erase is no pick of the goal's (MobPowerUtil.isMobSafeAbility leaves it
 * out). The creature decides on it here, while it has a player for its target within 16 blocks:
 * <ul>
 * <li>Right after that player, or his Stand, hits it.</li>
 * <li>The first erase of a fight: as it comes within 10 blocks of him, and then once a second, with a chance of 0.8
 * each time; the fourth asking is certain. On average a quarter of a second after it closed in, and never later than
 * 3 seconds after. It comes first: until then the goal picks no other move.</li>
 * <li>Every later erase of that fight: once a second, with a chance of 0.5 within 10 blocks and of 0.25 out to 16
 * (which is also the chance of a first erase out there).</li>
 * </ul>
 * All of this only while the key can be used (King Crimson's own conditions: unlocked, no erase of its own, its
 * cooldown over, its bar able to pay, time not stopped): a decision made while it cannot is let be. Where only a move
 * of its Stand holds the key back, the creature waits for that move to end, and its goal picks no other move until the
 * key is pressed. A fight is over when the creature has no living player for its target.
 */
final class TimeEraseTactic {
    static final double NEAR_BLOCKS = 10.0D;
    static final double FAR_BLOCKS = 16.0D;
    static final float FIRST_NEAR_CHANCE = 0.8F;
    static final float LATER_NEAR_CHANCE = 0.5F;
    static final float FAR_CHANCE = 0.25F;
    static final int ASK_EVERY_TICKS = 20;
    // So many askings of a first erase within 10 blocks may fail; the next one is certain.
    static final int FIRST_NEAR_ASKS_BY_CHANCE = 3;

    private final Mob mob;
    private int pressedInThisFight;
    private boolean wasNear;
    private boolean wants;
    private boolean firstComes;
    private int sinceAsked;
    private int askedNear;
    private int seenHurtStamp;
    @Nullable
    private HeldInputEntry held;

    TimeEraseTactic(Mob mob) {
        this.mob = mob;
        seenHurtStamp = mob.getLastHurtByMobTimestamp();
    }

    /**
     * The chance of an erase when the creature asks itself, so far from the player it fights.
     *
     * @param askedNear how often it has asked itself within 10 blocks for the first erase of this fight
     */
    static float chance(boolean firstOfTheFight, double distanceSqr, int askedNear) {
        if (distanceSqr > FAR_BLOCKS * FAR_BLOCKS) {
            return 0.0F;
        }
        if (distanceSqr > NEAR_BLOCKS * NEAR_BLOCKS) {
            return FAR_CHANCE;
        }
        if (!firstOfTheFight) {
            return LATER_NEAR_CHANCE;
        }
        return askedNear >= FIRST_NEAR_ASKS_BY_CHANCE ? 1.0F : FIRST_NEAR_CHANCE;
    }

    /** Every tick of the creature's Stand goal, before the goal's own. */
    void tick(@Nullable StandPower power) {
        releaseWhenOver();
        LivingEntity target = mob.getTarget();
        Ability erase = power != null && power.hasPower() ? MobPowerUtil.timeErase(power) : null;
        if (erase == null || !(target instanceof Player) || !target.isAlive()) {
            pressedInThisFight = 0;
            wasNear = false;
            wants = false;
            firstComes = false;
            sinceAsked = 0;
            askedNear = 0;
            seenHurtStamp = mob.getLastHurtByMobTimestamp();
            return;
        }
        double distanceSqr = mob.distanceToSqr(target);
        boolean decided = false;
        int hurtStamp = mob.getLastHurtByMobTimestamp();
        if (hurtStamp != seenHurtStamp) {
            seenHurtStamp = hurtStamp;
            LivingEntity attacker = mob.getLastHurtByMob();
            decided = attacker != null && (attacker == target || StandUtil.getStandUser(attacker) == target);
        }
        boolean near = distanceSqr <= NEAR_BLOCKS * NEAR_BLOCKS;
        // As it closes in for the first erase of the fight it asks itself at once, not at its next second.
        boolean closedIn = near && !wasNear && pressedInThisFight == 0;
        wasNear = near;
        if (closedIn || ++sinceAsked >= ASK_EVERY_TICKS) {
            sinceAsked = 0;
            decided |= mob.getRandom().nextFloat() < chance(pressedInThisFight == 0, distanceSqr, askedNear);
            if (near && pressedInThisFight == 0) {
                askedNear++;
            }
        }
        boolean first = near && pressedInThisFight == 0;
        // What a press of the key would be told now: the core's own check of the ability, as for the goal's picks.
        boolean usable = held == null && distanceSqr <= FAR_BLOCKS * FAR_BLOCKS && (wants || decided || first)
                && power.updateAvailableMoves().inMovesetAndCanBeUsed.get(erase.name()) == erase;
        wants = usable && (wants || decided);
        firstComes = usable && first;
        if (!wants || erase instanceof StandEntityAbility standAbility && standAbility.wouldBufferInput(mob)) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        held = AbilityInput.keyPressMob(erase, mob, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER), InputMethod.HOLD);
        // Taken or refused, this was its erase: the next one of this fight is a later one, and the goal picks again.
        wants = false;
        firstComes = false;
        pressedInThisFight++;
    }

    /** The goal stopped: the key is let go, and what was decided is forgotten. */
    void stop() {
        if (held != null) {
            AbilityInput.keyRelease(held.keyId, mob);
            held = null;
        }
        wants = false;
        firstComes = false;
    }

    // The key is let go as the goal lets its own go: when the action it started is over.
    private void releaseWhenOver() {
        if (held != null && (!(held.action instanceof EntityActionInstance action) || action.isOver())) {
            AbilityInput.keyRelease(held.keyId, mob);
            held = null;
        }
    }

    /**
     * Whether the goal picks no move now: the creature waits for its Stand to let the key through, holds the key, or
     * has the first erase of the fight still before it within 10 blocks with a key that can be used.
     */
    boolean holdsBackPicks() {
        return wants || held != null || firstComes;
    }

    boolean wants() {
        return wants;
    }

    int askedNear() {
        return askedNear;
    }

    int pressedInThisFight() {
        return pressedInThisFight;
    }
}
