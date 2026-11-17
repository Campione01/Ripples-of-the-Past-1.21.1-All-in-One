package rotp.addons.kingcrimson.ability;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.network.s2c.PlayVoiceLinePacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.sound.MultiSoundEventResolver;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase3Spec;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.EpitaphTargeting;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Epitaph (owner 2026-11-07, EPITAPH-ARCH 5.1): one press foresees. The creatures that hunt the user and the one
 * entity under his crosshair are lived ahead in a room of their own; he alone is shown what they will do, and then
 * they do it. Nothing is held: letting go of the key and recalling the Stand change nothing of what was foreseen.
 */
public final class KingCrimsonEpitaphAbility extends Ability {
    public KingCrimsonEpitaphAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        cooldown(KingCrimsonPhase3Spec.EPITAPH_COOLDOWN_TICKS);
    }

    // 1.16 KCConfig epitaphCooldown
    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        return KingCrimsonConfig.configuredCooldown(
                super.getCooldown(context, ticksHeld),
                KingCrimsonConfig.epitaphCooldownTicks());
    }

    // It comes with the prediction, not with the key.
    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        if (!KingCrimsonConfig.epitaphEnabled()) {
            return ConditionCheck.NEGATIVE;
        }
        // First the foresight, then the erase: never the other way round (owner, EPITAPH-ARCH section 4, T1).
        if (context.getUser() instanceof Player player && KingCrimsonTemporalStateService.isErasing(player)) {
            return negative("epitaph_during_erase");
        }
        // One prediction at a time; only the server knows of it here.
        if (context.getUser() instanceof ServerPlayer player && EpitaphSessions.of(player) != null) {
            return negative("epitaph_already_active");
        }
        float plain = KingCrimsonConfig.epitaphStamina();
        float dear = KingCrimsonConfig.epitaphPlayerStamina();
        ConditionCheck either = StandAbilityStamina.check(context, Math.max(plain, dear));
        // The crosshair is looked along only for a bar that cannot pay for both kinds of press.
        return either.isPositive() ? either : StandAbilityStamina.check(context, costNow(context.getUser()));
    }

    /** What a press with this under the crosshair charges: more for a player (EPITAPH-ARCH 5.1). */
    public static float costAt(@Nullable Entity crosshair) {
        return EpitaphTargeting.isPlayerTarget(crosshair) ? KingCrimsonConfig.epitaphPlayerStamina() : KingCrimsonConfig.epitaphStamina();
    }

    // What a press of this user would charge now: the one answer of the refusal, of the HUD and of the press itself.
    private static float costNow(@Nullable LivingEntity user) {
        return costAt(user instanceof Player player ? EpitaphTargeting.crosshair(player, KingCrimsonConfig.epitaphRadius()) : null);
    }

    // Full addon key; the String overload would add the jojo prefix.
    private static ConditionCheck negative(String key) {
        return ConditionCheck.createNegative(Component.translatable("rotp_kingcrimson.message." + key));
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
            InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
        bufferingState.isActionSuccess = false;
        if (level.isClientSide() || !(user instanceof ServerPlayer player)) {
            return null;
        }
        StandPower power = StandPower.get(player);
        if (power == null || !checkConditions(power).isPositive()) {
            return null;
        }
        Entity crosshair = EpitaphTargeting.crosshair(player, KingCrimsonConfig.epitaphRadius());
        // A player under the crosshair costs more and is waited for longer (EPITAPH-ARCH 5.1).
        boolean atPlayer = EpitaphTargeting.isPlayerTarget(crosshair);
        float cost = costAt(crosshair);
        // Paid before the copies are made: his phantom begins with what the press left him (EPITAPH-ARCH, ruling R10).
        float had = power.getStamina();
        StandAbilityStamina.consume((Ability) this, power, cost, false);
        if (EpitaphSessions.begin(player, crosshair) == null) {
            power.setStamina(had);
            return null;
        }
        if (atPlayer) {
            power.setAbilityCooldown(name(), KingCrimsonConfig.epitaphPlayerCooldownTicks());
        } else {
            setCooldownOnUse(power);
        }
        bufferingState.isActionSuccess = true;
        // Nobody around is told that a prediction began: his line and the sound of Epitaph are his alone.
        if (!skipsShoutWhileSneaking(player)) {
            PacketDistributor.sendToPlayer(player, new PlayVoiceLinePacket(
                    MultiSoundEventResolver.resolve(KingCrimsonAddon.DIAVOLO_EPITAPH),
                    SoundSource.VOICE, player.getId(), 1.0F, 1.0F, false));
        }
        player.playNotifySound(KingCrimsonAddon.KINGCRIMSON_EPITAPH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return null;
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return costNow(context.getUser());
    }
}
