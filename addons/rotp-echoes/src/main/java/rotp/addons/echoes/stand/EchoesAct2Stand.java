package rotp.addons.echoes.stand;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;

import rotp.addons.echoes.init.EchoesAbilities;
import rotp.addons.echoes.init.EchoesEntities;
import rotp.addons.echoes.init.EchoesSounds;
import net.minecraft.resources.ResourceLocation;

public final class EchoesAct2Stand {
    private EchoesAct2Stand() {}

    public static EntityStandType create(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                // his 1.21.1 AddonStands: four punches, in the order of his clips punch to punch4, the core's light combo
                .addAbility("act2_punch", ModStandAbilities.PUNCH)
                .addAbility("act2_punch2", ModStandAbilities.PUNCH)
                .addAbility("act2_punch3", ModStandAbilities.PUNCH)
                .addAbility("act2_punch4", ModStandAbilities.PUNCH)
                // master InitStands 173-187: heavy_attack at the PERFORM (1.16 standSound) of the heavy and of its
                // finisher variation, which takes the heavy's key at a finisher meter of 0.5 (both open with ACT2)
                .addAbility("act2_heavy_punch", ModStandAbilities.HEAVY_PUNCH,
                        heavy -> heavy.heavyPunchPerformSound(EchoesSounds.HEAVY_ATTACK))
                // the core's guard ends at once; this one's clip goes back to the idle over these ticks
                .addAbility("act2_guard", ModStandAbilities.GUARD,
                        guard -> guard.setDefaultPhaseLength(ActionPhase.RECOVERY, 3.0F))
                .addAbility("act2_finisher", EchoesAbilities.ACT2_FINISHER, finisher -> {
                    finisher.heavyPunchPerformSound(EchoesSounds.HEAVY_ATTACK);
                    finisher.initIsFinisher("act2_heavy_punch");
                })
                .addAbility("select_act", EchoesAbilities.SELECT_ACT)
                // R772 F11: the master's onomatopoeia; each cancel is the Shift variation of its slot
                .addAbility("onomatopoeia_select", EchoesAbilities.ONOMATOPOEIA_SELECT)
                .addAbility("onomatopoeia_cancel", EchoesAbilities.ONOMATOPOEIA_CANCEL)
                .addAbility("onomatopoeia_attach", EchoesAbilities.ONOMATOPOEIA_ATTACH)
                .addAbility("onomatopoeia_attach_cancel", EchoesAbilities.ONOMATOPOEIA_ATTACH_CANCEL)
                .addAbility("onomatopoeia_self", EchoesAbilities.ONOMATOPOEIA_SELF)
                .addAbility("onomatopoeia_self_cancel", EchoesAbilities.ONOMATOPOEIA_SELF_CANCEL)
                .addAbility("tail_needle", EchoesAbilities.TAIL_NEEDLE)
                .addAbility("tail_needle_recall", EchoesAbilities.TAIL_NEEDLE_RECALL)
                .addAbility("onomatopoeia_entity_toggle", EchoesAbilities.ONOMATOPOEIA_ENTITY_TOGGLE)
                .makeControlScheme("hotbar")
                    .bind("act2_punch", InputMethod.CLICK, InputKey.LMB)
                    .bind(
                            "act2_heavy_punch",
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind("act2_guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar("select_act", 0, InputMethod.CLICK)
                    .addToHotbar("onomatopoeia_select", 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            "onomatopoeia_cancel", "onomatopoeia_select", InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar("tail_needle", 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            "tail_needle_recall", "tail_needle", InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar("onomatopoeia_attach", 0, InputMethod.CLICK)
                    .addHotbarSlotVariation("onomatopoeia_attach_cancel", "onomatopoeia_attach",
                            InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar("onomatopoeia_entity_toggle", 0, InputMethod.CLICK)
                    .addToHotbar("onomatopoeia_self", 0, InputMethod.CLICK)
                    .addHotbarSlotVariation("onomatopoeia_self_cancel", "onomatopoeia_self",
                            InputKey.Modifier.SHIFT, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("act2_punch", InputMethod.CLICK, InputKey.LMB)
                    .bind(
                            "act2_heavy_punch",
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind("act2_guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("act2_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "act2_heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "act2_finisher"))
                .addSkill(StandUnlockableSkill.startingAbility("select_act"))
                .addSkill(StandUnlockableSkill.startingAbility("onomatopoeia_select"))
                .addSkill(StandUnlockableSkill.startingAbility("onomatopoeia_cancel"))
                .addSkill(StandUnlockableSkill.startingAbility("onomatopoeia_attach"))
                .addSkill(StandUnlockableSkill.startingAbility("onomatopoeia_attach_cancel"))
                .addSkill(StandUnlockableSkill.startingAbility("onomatopoeia_self"))
                .addSkill(StandUnlockableSkill.startingAbility("onomatopoeia_self_cancel"))
                .addSkill(StandUnlockableSkill.startingAbility("tail_needle"))
                .addSkill(StandUnlockableSkill.startingAbility("tail_needle_recall"))
                .addSkill(StandUnlockableSkill.startingAbility("onomatopoeia_entity_toggle"))
                .addHumanoidStandSkills();

        return new EntityStandType(
                // master EchoesStandStats ACT2; the arrow now gives ACT0, the egg
                new StandStats.Builder()
                        .power(8.0, 8.0)
                        .speed(5.0, 5.0)
                        .range(10.0, 50.0)
                        .durability(11.0, 11.0)
                        .precision(8.0)
                        .randomWeight(0.0D)
                        .build(),
                moveset,
                EchoesEntities.ACT2.get(),
                id)
                .standControlType(StandControlType.LONG_DISTANCE_OPERATION)
                .manualControl(true)
                .distanceStrengthDecay(false)
                .standDimensions(0.65F, 1.95F)
                // master EchoesEntity: the ACT2 voice, the common summon stinger, the recall stinger
                .summonShout(() -> EchoesSounds.ACT2_SUMMON)
                .summonSound(() -> EchoesSounds.ECHOES_SUMMON_BGM_COMMON)
                .unsummonSound(() -> EchoesSounds.ECHOES_RECALL_BGM);
    }
}
