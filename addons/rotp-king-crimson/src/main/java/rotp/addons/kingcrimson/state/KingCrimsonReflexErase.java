package rotp.addons.kingcrimson.state;

import rotp.core.api.stand.StandAbilityFatigue;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.network.s2c.TrSetStandEntityPacket;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.mixin.LivingLastHurtAccessor;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState.EraseKind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * King Crimson's passive, which takes the place of Epitaph's dodge. The owner, 2026-11-07: "這個建議改成被動技能，玩家受傷大於最大
 * 生命值10%時刪除一次時間進行規避（自動召喚緋紅之王替身並進入兩秒時間刪除狀態...），冷卻和消耗要比原設計更長，同時致死傷害（或瀕死時受到的傷害）會無視
 * 當前冷卻零消耗自動釋放一次持續四秒的時間刪除，這個冷卻獨立計算，十分鐘一次".
 * <ul>
 * <li>The reflex: a hit that would take more than a share of his max health from his health bar is cancelled and he
 * erases time for 2 s, for stamina and with a cooldown of its own.</li>
 * <li>The last chance: a hit that would kill him, or any hit when he is nearly dead, is cancelled and he erases time
 * for 4 s, for nothing, whatever the reflex's cooldown says, once in ten minutes.</li>
 * </ul>
 * Both cooldowns sit in the Stand's own cooldown table, under the names of the two passive skills: saved with the
 * Stand, kept through death and relog, counted as every Stand cooldown is and sent to the client. Neither starts the
 * key's cooldown or waits on it. Nothing saves from what pierces an erase, from the void or /kill, in someone's time
 * stop or during an erase.
 * <p>
 * The core's mode with no cooldowns (the owner, 2026-11-12): the reflex waits for nothing there, as every skill; it
 * stores no cooldown and the core's fatigue prices its repeats as those of a small practical skill. The last chance
 * keeps its ten minutes in both modes: it is no second life to repeat.
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class KingCrimsonReflexErase {
    public static final String REFLEX = "kingcrimson_reflex_erase";
    public static final String LAST_CHANCE = "kingcrimson_last_chance_erase";

    private KingCrimsonReflexErase() {}

    // After every other handler has had the hit: what is left of it is what would land.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getAmount() > 0.0F
                && escapes(player, event.getSource(), event.getAmount())) {
            event.setCanceled(true);
        }
    }

    private static boolean escapes(ServerPlayer player, DamageSource source, float amount) {
        if (!KingCrimsonConfig.reflexErase()
                || !KingCrimsonAddon.isKingCrimsonUser(player)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || KingCrimsonEraseReach.pierces(source)
                || TimeStopState.shouldFreezeOnServer(player)
                || !KingCrimsonTemporalStateService.canStartTimeErase(player)) {
            return false;
        }
        StandPower power = StandPower.get(player);
        Ability reflex = power != null ? power.getAbility(REFLEX) : null;
        // Unlocked with Time Erase itself, at the same Resolve level.
        if (reflex == null || !reflex.isAbilityUnlocked(power) || !power.canUsePower()) {
            return false;
        }
        float hit = afterDefences(player, source, amount);
        if (hit <= 0.0F) {
            return false;
        }
        float absorbed = player.getAbsorptionAmount();
        float maxHealth = player.getMaxHealth();
        boolean kills = hit >= player.getHealth() + absorbed;
        boolean nearlyDead = player.getHealth() <= maxHealth * KingCrimsonConfig.lastChanceEraseHealthShare();
        if ((kills || nearlyDead) && !power.isAbilityOnCooldown(LAST_CHANCE)) {
            return fire(player, power, EraseKind.LAST_CHANCE);
        }
        return hit - absorbed > maxHealth * KingCrimsonConfig.reflexEraseThreshold()
                && (StandAbilityFatigue.applies(power) || !power.isAbilityOnCooldown(REFLEX))
                && StandAbilityStamina.canPay(power, KingCrimsonConfig.reflexEraseStamina())
                && fire(player, power, EraseKind.REFLEX);
    }

    // The passive is spent whether or not the erase begins: one returned to zero (Gold Experience Requiem) never does,
    // and then the hit lands.
    private static boolean fire(ServerPlayer player, StandPower power, EraseKind kind) {
        summonQuietly(player, power);
        // He was not doing whatever he was doing: time went by without him.
        player.stopUsingItem();
        boolean began = KingCrimsonTemporalStateService.beginTimeErase(player, kind);
        if (began) {
            // The donor's sound of Epitaph's future skip, which this passive replaced: his alone, as the erase's start is.
            player.playNotifySound(KingCrimsonAddon.EPITAPH_TIMESKIP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (kind == EraseKind.REFLEX) {
            Ability reflex = power.getAbility(REFLEX);
            StandAbilityStamina.consume(reflex, power, KingCrimsonConfig.reflexEraseStamina(), false);
            if (StandAbilityFatigue.applies(power)) {
                // No wait is stored in the mode with no cooldowns: the core is told the use and prices its repeat.
                // Told by name: a hit often comes inside another ability's use, where a raised cooldown would
                // count as that ability's lock.
                StandAbilityFatigue.recordUse(power, reflex, KingCrimsonConfig.reflexEraseCooldownTicks());
            } else {
                power.setAbilityCooldown(REFLEX, KingCrimsonConfig.reflexEraseCooldownTicks());
            }
        } else {
            // The last chance's wait holds in the mode with no cooldowns too: a lock, and no use whose skipped
            // cooldown the core's fatigue would price. Set from a damage event, it would count as one.
            StandAbilityFatigue.withoutFatigue(
                    () -> power.setAbilityCooldown(LAST_CHANCE, KingCrimsonConfig.lastChanceEraseCooldownTicks()));
        }
        return began;
    }

    // "自動召喚緋紅之王替身": the Stand comes out with him and stays out afterward. No shout and no summon sound: nobody
    // around is told that an erase began (the core's summon would play both).
    private static void summonQuietly(ServerPlayer player, StandPower power) {
        if (power.getSummonedStandEntity() != null || !(power.getPowerType() instanceof EntityStandType type)
                || !type.summon(player, power, stand -> {}, false)) {
            return;
        }
        StandEntity stand = power.getSummonedStandEntity();
        if (stand == null) {
            return;
        }
        if (!player.level().addFreshEntity(stand)) {
            power.setSummonedStand(null);
            stand.discard();
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new TrSetStandEntityPacket(player.getId(), stand.getId()));
    }

    /**
     * What vanilla's own defences leave of a hit, in the order LivingEntity.hurt and actuallyHurt take them: the
     * shield, a helmet under a falling block, the invulnerability frames, armour, Resistance, protection enchantments.
     * Absorption is not taken off: the caller reads it. What other mods cut later, in the damage event, is not known
     * here, so the passive may answer a hit that would have ended a little under its threshold.
     */
    static float afterDefences(LivingEntity body, DamageSource source, float amount) {
        if (body.isInvulnerableTo(source) || body.isDamageSourceBlocked(source)) {
            return 0.0F;
        }
        if (source.is(DamageTypeTags.DAMAGES_HELMET) && !body.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            amount *= 0.75F;
        }
        if (body.invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            float last = ((LivingLastHurtAccessor) body).kingCrimson$lastHurt();
            if (amount <= last) {
                return 0.0F;
            }
            amount -= last;
        }
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            amount = CombatRules.getDamageAfterAbsorb(body, amount, source, body.getArmorValue(),
                    (float) body.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }
        if (!source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            MobEffectInstance resistance = body.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (resistance != null && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                amount = Math.max(amount * (25 - (resistance.getAmplifier() + 1) * 5) / 25.0F, 0.0F);
            }
            if (amount > 0.0F && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                    && body.level() instanceof ServerLevel level) {
                float protection = EnchantmentHelper.getDamageProtection(level, body, source);
                if (protection > 0.0F) {
                    amount = CombatRules.getDamageAfterMagicAbsorb(amount, protection);
                }
            }
        }
        return Math.max(amount, 0.0F);
    }
}
