package rotp.addons.kingcrimson.state;

import javax.annotation.Nullable;

import rotp.core.api.stand.BlinkLanding;
import rotp.core.api.stand.StandAbilityFatigue;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.api.time.event.TimeEraseStartEvent;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.network.s2c.TrDirectEntityPosPacket;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Time Erase step (時刪快速移動). The owner, 2026-11-07: "紅王應該增加個時刪快速移動的技能，原理是利用時刪從a移動到b，過程被刪除實現的瞬移，
 * 用於在多人遊戲時模擬時刪的神出鬼沒效果。解鎖等級和冷卻與消耗與距離你參照世界的時停趕路就行，不用太高，算作一個常規技能". Modelled on The World's time
 * stop blink and landing by the same rules (the core's BlinkLanding): the user is gone from where he stands and stands
 * where he aimed, in one tick, as far as he could have walked in his own longest erase. It is no erase for anybody
 * else: no erased world, no shifted image, no jump of the day, no amnesia. He alone sees the red flash an erase shows
 * at its start and its end ("這裏只顯示一瞬間的時刪啟動和結束時的那種紅色屏幕波紋效果即可").
 */
public final class KingCrimsonTimeEraseStepService {
    public static final String ABILITY = "kingcrimson_timeerase_step";
    private static final String ERASE = "kingcrimson_timeerase";

    private KingCrimsonTimeEraseStepService() {}

    /** The erased ticks a step may take: the user's own longest erase, his Resolve level's share of the setting. */
    public static int maxTicks(StandPower power) {
        return KingCrimsonPhase5Spec.timeEraseTicks(KingCrimsonConfig.timeEraseCapTicks(),
                Math.max(power.getResolveLevel(), KingCrimsonPhase5Spec.TIME_ERASE_STEP_UNLOCK_RESOLVE_LEVEL));
    }

    /** How far a step reaches: what the user walks in those ticks, at most the setting. */
    public static double reach(LivingEntity user, StandPower power) {
        return BlinkLanding.reach(user, maxTicks(power), KingCrimsonConfig.timeEraseStepMaxDistance());
    }

    /** What a step costs when its trip implies so many ticks: the blink's share of an erase's start and upkeep. */
    public static float cost(int impliedTicks) {
        return KingCrimsonPhase5Spec.TIME_ERASE_STEP_COST_SHARE
                * (KingCrimsonPhase5Spec.TIME_ERASE_ACTIVATION_STAMINA
                        + KingCrimsonConfig.timeEraseStaminaPerTick() * Math.max(impliedTicks, 0));
    }

    /** The blink's share of what Time Erase would wait after those ticks. */
    public static int cooldownTicks(StandPower power, int impliedTicks) {
        Ability erase = power.getAbility(ERASE);
        return erase != null
                ? Math.round(erase.getCooldown(power, impliedTicks) * KingCrimsonPhase5Spec.TIME_ERASE_STEP_COOLDOWN_SHARE)
                : 0;
    }

    /**
     * The step, from the key. True when it went off: he stands where he aimed, or an answering ability (Gold
     * Experience Requiem) returned it to zero and he stays, having paid and with the cooldown started. False, and
     * nothing spent, when he may not step now, has nowhere safe to land or cannot pay.
     */
    public static boolean step(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        Ability ability = power != null ? power.getAbility(ABILITY) : null;
        // Neither in an erase, his own or another's, nor frozen in stopped time.
        if (ability == null || !ability.isAbilityUnlocked(power)
                || !KingCrimsonTemporalStateService.canStartTimeErase(player)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        int maxTicks = maxTicks(power);
        double reach = reach(player, power);
        if (maxTicks <= 0 || reach <= 0.0D) {
            return false;
        }
        ActionTarget target = BlinkLanding.target(player, power, reach, StandSkillWalls.wallsHideEntities(level, ability));
        Vec3 landing = landing(level, player, target, reach);
        if (landing == null) {
            player.displayClientMessage(Component.translatable("rotp_kingcrimson.message.timeerase_step_blocked"), true);
            return false;
        }
        int impliedTicks = BlinkLanding.impliedTicks(player, landing, maxTicks);
        float cost = cost(impliedTicks);
        if (!StandAbilityStamina.canPay(power, cost)) {
            ConditionCheck.sendActionFailedMessage(ability, ConditionCheck.createNegative("not_enough_stamina"), player);
            return false;
        }
        StandAbilityStamina.consume(ability, power, cost, false);
        startCooldown(power, ability, impliedTicks);
        // Owner: within four chunks of a summoned Gold Experience Requiem a step is returned to zero. The core's
        // event carries the question with its kind; he stays, has paid, and the end of an erase is heard and seen.
        if (TimeEraseStartEvent.post(player, impliedTicks, TimeEraseStartEvent.Kind.STEP)) {
            // Epitaph, T8: the step takes him off his foreseen track for good, as an erase does.
            EpitaphPredictions.onUserStepped(player);
            KingCrimsonTemporalStateService.makeNearbyMobsLoseTarget(player, landing);
            TimeStopBlinkAbility.teleportFacing(player, landing, TimeStopBlinkAbility.getFacingYaw(target, landing));
            player.resetFallDistance();
            // A step into stopped time freezes him where he lands.
            TimeStopState.repinOrReleaseOnServer(player);
            // Nothing between A and B for those who watch: his model does not slide there.
            PacketDistributor.sendToPlayersTrackingEntity(player, new TrDirectEntityPosPacket(player.getId(), landing));
        }
        KingCrimsonTemporalStateService.sendTimeEraseEndPresentation(player);
        return true;
    }

    // The step, and Time Erase unless it already waits, as the blink does to its time stop.
    private static void startCooldown(StandPower power, Ability step, int impliedTicks) {
        int cooldown = cooldownTicks(power, impliedTicks);
        if (cooldown > 0) {
            power.setAbilityCooldown(ABILITY, cooldown);
            if (!power.isAbilityOnCooldown(ERASE)) {
                power.setAbilityCooldown(ERASE, cooldown);
            }
        } else {
            // The mode with no cooldowns skips this wait, which no setCooldownOnUse works out: the core is told of
            // the step's use and of the wait the other mode would set (nothing for anybody else).
            Ability erase = power.getAbility(ERASE);
            StandAbilityFatigue.recordUse(power, step, erase != null ? Math.round(erase.getNormalCooldown(power, impliedTicks)
                    * KingCrimsonPhase5Spec.TIME_ERASE_STEP_COOLDOWN_SHARE) : 0);
        }
    }

    /** An erase ended and took its cooldown: the step takes its share of it, as a time stop's end gives its blink. */
    static void onEraseCooldown(StandPower power) {
        int cooldown = Math.round(power.getAbilityCooldown(ERASE) * KingCrimsonPhase5Spec.TIME_ERASE_STEP_COOLDOWN_SHARE);
        if (cooldown > power.getAbilityCooldown(ABILITY) && power.getAbility(ABILITY) != null) {
            // A share of a cooldown read back: in the Stand's cooldown share already.
            power.restoreAbilityCooldown(ABILITY, cooldown);
        }
    }

    // Where the blink would land, behind an entity he aims at; with no room behind it, before it on his own line, as
    // a blink that does not go behind lands. Null when neither is a place to stand.
    @Nullable
    private static Vec3 landing(ServerLevel level, ServerPlayer player, ActionTarget target, double reach) {
        Vec3 behind = BlinkLanding.landingPos(level, player, target, reach, true);
        if (isSafe(level, player, behind)) {
            return behind;
        }
        if (!target.isEmpty(level) && target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() != null) {
            Vec3 before = BlinkLanding.entityPos(player, target.getEntity(), false);
            if (isSafe(level, player, before)) {
                return before;
            }
        }
        return null;
    }

    // Loaded ground, no wall to stand in and nothing that burns, ground somewhere below, and nobody there whom he
    // cannot perceive (the blink's own refusal).
    private static boolean isSafe(ServerLevel level, ServerPlayer player, Vec3 at) {
        return KingCrimsonShiftWalk.canStand(level, player, player.getDimensions(player.getPose()), at)
                && hasGroundBelow(level, at)
                && !BlinkLanding.refusesLanding(level, player, at);
    }

    private static boolean hasGroundBelow(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(at).mutable();
        while (pos.getY() >= level.getMinBuildHeight()) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return true;
            }
            pos.move(0, -1, 0);
        }
        return false;
    }
}
