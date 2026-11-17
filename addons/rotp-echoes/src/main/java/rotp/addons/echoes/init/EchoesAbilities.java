package rotp.addons.echoes.init;

import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.ability.AbilityType;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.ability.EchoesAct1FinisherAbility;
import rotp.addons.echoes.ability.EchoesAct3BarrageAbility;
import rotp.addons.echoes.ability.EchoesAct3HeavyChargedAbility;
import rotp.addons.echoes.ability.EchoesGravityAbility;
import rotp.addons.echoes.ability.EchoesGravityCancelAbility;
import rotp.addons.echoes.ability.EchoesGravityAbility.Mode;
import rotp.addons.echoes.ability.EchoesFinisherAbility;
import rotp.addons.echoes.ability.EchoesOnomatopoeiaAbility;
import rotp.addons.echoes.ability.EchoesSelectActAbility;
import rotp.addons.echoes.ability.EchoesSoundAbility;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EchoesAbilities {
    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, RotpEchoes.MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            GRAVITY_PROJECTILE =
                    action("gravity_projectile", Mode.PROJECTILE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            FREEZE = action("freeze", Mode.FREEZE_TARGET);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            FREEZE_SELF = action("freeze_self", Mode.FREEZE_SELF);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            GRAVITY_INFUSE =
                    action("gravity_infuse", Mode.INFUSE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            HAND_FREEZE = action("hand_freeze", Mode.HAND_FREEZE);
    // R772 F11: the master's (RotP-EchoesACT-master) self hand freeze and cancel actions
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            HAND_FREEZE_SELF = action("hand_freeze_self", Mode.HAND_FREEZE_SELF);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityCancelAbility>>
            FREEZE_CANCEL = cancel("freeze_cancel", Mode.FREEZE_CANCEL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityCancelAbility>>
            HAND_FREEZE_CANCEL = cancel("hand_freeze_cancel", Mode.HAND_FREEZE_CANCEL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityCancelAbility>>
            FREEZE_SELF_CANCEL = cancel("freeze_self_cancel", Mode.FREEZE_SELF_CANCEL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityCancelAbility>>
            HAND_FREEZE_SELF_CANCEL = cancel("hand_freeze_self_cancel", Mode.HAND_FREEZE_SELF_CANCEL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityCancelAbility>>
            GRAVITY_INFUSE_CANCEL = cancel("gravity_infuse_cancel", Mode.INFUSE_CANCEL);
    // R772 F11: the ACT2 and ACT3 heavy punches' finisher variations (master InitStands 173-187, 274-291)
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesFinisherAbility>>
            ACT3_FINISHER = ABILITY_TYPES.register(
                    "act3_finisher",
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new EchoesFinisherAbility(
                                    type,
                                    id,
                                    EchoesFinisherAbility.Mode.ACT3)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesFinisherAbility>>
            ACT2_FINISHER = ABILITY_TYPES.register(
                    "act2_finisher",
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new EchoesFinisherAbility(
                                    type,
                                    id,
                                    EchoesFinisherAbility.Mode.ACT2)));
    // R772 F11: ACT1's heavy finisher that gives the chosen sound (master InitStands 95-109)
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesAct1FinisherAbility>>
            ACT1_FINISHER = ABILITY_TYPES.register(
                    "act1_finisher",
                    key -> new AbilityType<>(
                            key, EchoesAct1FinisherAbility::new));
    // 092 ACT3 barrage: start voice, no impact sound
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesAct3BarrageAbility>>
            ACT3_BARRAGE = ABILITY_TYPES.register(
                    "act3_barrage",
                    key -> new AbilityType<>(
                            key, EchoesAct3BarrageAbility::new));
    // the author's EchoesHeavyChargedAbility
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesAct3HeavyChargedAbility>>
            ACT3_HEAVY_CHARGED = ABILITY_TYPES.register(
                    "act3_heavy_charged",
                    key -> new AbilityType<>(
                            key, EchoesAct3HeavyChargedAbility::new));
    // R772 E1: the master's ACT selector replaces the evolve and degrade steps
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesSelectActAbility>>
            SELECT_ACT = ABILITY_TYPES.register(
                    "select_act",
                    key -> new AbilityType<>(
                            key, EchoesSelectActAbility::new));

    // R772 F11: ACT2's onomatopoeia (master InitStands 192-249)
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesOnomatopoeiaAbility>>
            ONOMATOPOEIA_SELECT = onomatopoeia("onomatopoeia_select", EchoesOnomatopoeiaAbility.Mode.SELECT);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesOnomatopoeiaAbility>>
            ONOMATOPOEIA_CANCEL = onomatopoeia("onomatopoeia_cancel", EchoesOnomatopoeiaAbility.Mode.CANCEL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesOnomatopoeiaAbility.Attach>>
            ONOMATOPOEIA_ATTACH = ABILITY_TYPES.register(
                    "onomatopoeia_attach",
                    key -> new AbilityType<>(
                            key, EchoesOnomatopoeiaAbility.Attach::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesOnomatopoeiaAbility>>
            ONOMATOPOEIA_ATTACH_CANCEL = onomatopoeia(
                    "onomatopoeia_attach_cancel", EchoesOnomatopoeiaAbility.Mode.ATTACH_CANCEL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesOnomatopoeiaAbility>>
            ONOMATOPOEIA_SELF = onomatopoeia("onomatopoeia_self", EchoesOnomatopoeiaAbility.Mode.SELF);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesOnomatopoeiaAbility>>
            ONOMATOPOEIA_SELF_CANCEL = onomatopoeia(
                    "onomatopoeia_self_cancel", EchoesOnomatopoeiaAbility.Mode.SELF_CANCEL);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesOnomatopoeiaAbility.TailNeedle>>
            TAIL_NEEDLE = ABILITY_TYPES.register(
                    "tail_needle",
                    key -> new AbilityType<>(
                            key, EchoesOnomatopoeiaAbility.TailNeedle::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesOnomatopoeiaAbility>>
            TAIL_NEEDLE_RECALL = onomatopoeia(
                    "tail_needle_recall", EchoesOnomatopoeiaAbility.Mode.TAIL_NEEDLE_RECALL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesOnomatopoeiaAbility>>
            ONOMATOPOEIA_ENTITY_TOGGLE = onomatopoeia(
                    "onomatopoeia_entity_toggle", EchoesOnomatopoeiaAbility.Mode.ENTITY_TOGGLE);

    // R772 F11: ACT1's sounds (master InitStands 114-165)
    public static final DeferredHolder<AbilityType<?>, AbilityType<EchoesSoundAbility>>
            SOUND_SELECT = sound("sound_select", EchoesSoundAbility.Mode.SELECT);
    public static final DeferredHolder<AbilityType<?>, AbilityType<EchoesSoundAbility>>
            SOUND_SELF = sound("sound_self", EchoesSoundAbility.Mode.SELF);
    public static final DeferredHolder<AbilityType<?>, AbilityType<EchoesSoundAbility>>
            SOUND_SELF_CANCEL = sound("sound_self_cancel", EchoesSoundAbility.Mode.SELF_CANCEL);
    public static final DeferredHolder<AbilityType<?>, AbilityType<EchoesSoundAbility>>
            SOUND_MARK_BLOCK = sound("sound_mark_block", EchoesSoundAbility.Mode.MARK_BLOCK);
    public static final DeferredHolder<AbilityType<?>, AbilityType<EchoesSoundAbility>>
            SOUND_CLEAR_MARK = sound("sound_clear_mark", EchoesSoundAbility.Mode.CLEAR_MARK);
    public static final DeferredHolder<AbilityType<?>, AbilityType<EchoesSoundAbility>>
            SOUND_CLEAR_ALL_MARKS = sound("sound_clear_all_marks", EchoesSoundAbility.Mode.CLEAR_ALL_MARKS);
    public static final DeferredHolder<AbilityType<?>, AbilityType<EchoesSoundAbility>>
            SOUND_GIVE = sound("sound_give", EchoesSoundAbility.Mode.GIVE);
    public static final DeferredHolder<AbilityType<?>, AbilityType<EchoesSoundAbility>>
            SOUND_GIVE_CANCEL = sound("sound_give_cancel", EchoesSoundAbility.Mode.GIVE_CANCEL);
    public static final DeferredHolder<AbilityType<?>, AbilityType<EchoesSoundAbility>>
            SOUND_GIVE_CANCEL_ALL = sound("sound_give_cancel_all", EchoesSoundAbility.Mode.GIVE_CANCEL_ALL);

    private EchoesAbilities() {}

    private static DeferredHolder<AbilityType<?>, AbilityType<EchoesSoundAbility>> sound(
            String path, EchoesSoundAbility.Mode mode) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(key, (type, id) -> new EchoesSoundAbility(type, id, mode)));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<EchoesOnomatopoeiaAbility>>
            onomatopoeia(String path, EchoesOnomatopoeiaAbility.Mode mode) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new EchoesOnomatopoeiaAbility(type, id, mode)));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            action(String path, Mode mode) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new EchoesGravityAbility(type, id, mode)));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityCancelAbility>>
            cancel(String path, Mode mode) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new EchoesGravityCancelAbility(type, id, mode)));
    }
}
