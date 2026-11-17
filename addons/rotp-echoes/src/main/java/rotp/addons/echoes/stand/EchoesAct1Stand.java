package rotp.addons.echoes.stand;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;

import rotp.addons.echoes.init.EchoesAbilities;
import rotp.addons.echoes.init.EchoesEntities;
import rotp.addons.echoes.init.EchoesSounds;
import net.minecraft.resources.ResourceLocation;

/**
 * Echoes ACT1. The 1.16 donor never shipped this form (its inventory records ACT1 as absent); it exists in the
 * port because the author made it (his 1.21.1 skin, installed by migration/author-1211-skins). The moveset has the
 * tail whip (punch), the sweep (heavy punch) and its sound-giving finisher (EchoesAct1FinisherAbility), the core guard,
 * the ACT selector (R772 E1) and the author's latest sound actions (R772 F11, EchoesSoundAbility). The stats are the author's latest (RotP-EchoesACT-master EchoesStandStats). It is not
 * in the arrow pool; ACT0, the egg, awakens into it (EchoesAct0Awakening), and the selector returns to it.
 */
public final class EchoesAct1Stand {
    private EchoesAct1Stand() {}

    public static EntityStandType create(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                // his 1.21.1 AddonStands: four punches, in the order of his clips punch to punch4, the core's light combo
                .addAbility("punch", ModStandAbilities.PUNCH)
                .addAbility("punch2", ModStandAbilities.PUNCH)
                .addAbility("punch3", ModStandAbilities.PUNCH)
                .addAbility("punch4", ModStandAbilities.PUNCH)
                // master InitStands 95-109: heavy_attack at the PERFORM (1.16 standSound) of the heavy and of its
                // finisher variation, which opens at Resolve 2 and takes the heavy's key at a finisher meter of 0.5
                .addAbility("heavy_punch", ModStandAbilities.HEAVY_PUNCH,
                        heavy -> heavy.heavyPunchPerformSound(EchoesSounds.HEAVY_ATTACK))
                .addAbility("act1_finisher", EchoesAbilities.ACT1_FINISHER, finisher -> {
                    finisher.heavyPunchPerformSound(EchoesSounds.HEAVY_ATTACK);
                    finisher.initIsFinisher("heavy_punch");
                    finisher.resolveLevelToUnlock(2);
                })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility("select_act", EchoesAbilities.SELECT_ACT)
                // R772 F11: the master's sounds; marking and giving open at Resolve 2, above ACT1's own level
                .addAbility("sound_select", EchoesAbilities.SOUND_SELECT)
                .addAbility("sound_self", EchoesAbilities.SOUND_SELF)
                .addAbility("sound_self_cancel", EchoesAbilities.SOUND_SELF_CANCEL)
                .addAbility("sound_mark_block", EchoesAbilities.SOUND_MARK_BLOCK,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility("sound_clear_mark", EchoesAbilities.SOUND_CLEAR_MARK,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility("sound_clear_all_marks", EchoesAbilities.SOUND_CLEAR_ALL_MARKS,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility("sound_give", EchoesAbilities.SOUND_GIVE,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility("sound_give_cancel", EchoesAbilities.SOUND_GIVE_CANCEL,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility("sound_give_cancel_all", EchoesAbilities.SOUND_GIVE_CANCEL_ALL,
                        ability -> ability.resolveLevelToUnlock(2))
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar("select_act", 0, InputMethod.CLICK)
                    .addToHotbar("sound_select", 0, InputMethod.CLICK)
                    .addToHotbar("sound_self", 0, InputMethod.CLICK)
                    .addToHotbar("sound_mark_block", 0, InputMethod.CLICK)
                    .addHotbarSlotVariation("sound_clear_all_marks", "sound_mark_block",
                            InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar("sound_give", 0, InputMethod.CLICK)
                    .addToHotbar("sound_give_cancel", 0, InputMethod.CLICK)
                    .addHotbarSlotVariation("sound_give_cancel_all", "sound_give_cancel",
                            InputKey.Modifier.SHIFT, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility("act1_finisher"))
                .addSkill(StandUnlockableSkill.startingAbility("select_act"))
                .addSkill(StandUnlockableSkill.startingAbility("sound_select"))
                .addSkill(StandUnlockableSkill.startingAbility("sound_self"))
                .addSkill(StandUnlockableSkill.startingAbility("sound_self_cancel"))
                .addSkill(StandUnlockableSkill.startingAbility("sound_mark_block"))
                .addSkill(StandUnlockableSkill.startingAbility("sound_clear_mark"))
                .addSkill(StandUnlockableSkill.startingAbility("sound_clear_all_marks"))
                .addSkill(StandUnlockableSkill.startingAbility("sound_give"))
                .addSkill(StandUnlockableSkill.startingAbility("sound_give_cancel"))
                .addSkill(StandUnlockableSkill.startingAbility("sound_give_cancel_all"))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(5.0, 5.0)
                        .speed(5.0, 5.0)
                        .range(10.0, 50.0)
                        .durability(11.0, 11.0)
                        .precision(8.0)
                        .randomWeight(0.0D)
                        .build(),
                moveset,
                EchoesEntities.ACT1.get(),
                id)
                .standControlType(StandControlType.LONG_DISTANCE_OPERATION)
                .manualControl(true)
                .distanceStrengthDecay(false)
                .standDimensions(0.65F, 1.95F)
                // master EchoesEntity: the ACT1 voice, the common summon stinger, the recall stinger
                .summonShout(() -> EchoesSounds.ACT1_SUMMON)
                .summonSound(() -> EchoesSounds.ECHOES_SUMMON_BGM_COMMON)
                .unsummonSound(() -> EchoesSounds.ECHOES_RECALL_BGM);
    }
}
