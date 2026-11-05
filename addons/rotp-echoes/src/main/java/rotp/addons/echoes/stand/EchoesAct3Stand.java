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

public final class EchoesAct3Stand {
    private EchoesAct3Stand() {}

    public static EntityStandType create(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility("punch", ModStandAbilities.PUNCH)
                // 092: ACT3 voice is the barrage cry; start voice and
                // silent hits live in EchoesAct3BarrageAbility
                .addAbility(
                        "barrage",
                        EchoesAbilities.ACT3_BARRAGE,
                        barrage -> barrage.barrageCrySound(
                                EchoesSounds.BARRAGE_HIT_SOUND))
                // master InitStands 283-291 (R772 F11): heavy_attack at the PERFORM (1.16 standSound), the core's
                // heavy impact sound on the hit
                .addAbility(
                        "heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        heavy -> heavy.heavyPunchPerformSound(EchoesSounds.HEAVY_ATTACK))
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility("guard", ModStandAbilities.GUARD)
                // R772 F13: the author's 1.21.1 ACT3 grabs and plays his grab clips; wired as the core's Star Platinum
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility("grab_throw", ModStandAbilities.GRAB_THROW)
                .addAbility("grab_punch", ModStandAbilities.PUNCH, punch -> {
                    punch.initIsGrabVariation();
                })
                .addAbility("grab_barrage", EchoesAbilities.ACT3_BARRAGE, barrage -> {
                    barrage.barrageCrySound(EchoesSounds.BARRAGE_HIT_SOUND);
                    barrage.initIsGrabVariation();
                })
                .addAbility("grab_heavy_punch", ModStandAbilities.HEAVY_PUNCH, heavy -> {
                    heavy.heavyPunchPerformSound(EchoesSounds.HEAVY_ATTACK);
                    heavy.initIsGrabVariation();
                })
                // master InitStands gates above ACT3's own Resolve level 3: projectile 4, infuse 4
                .addAbility(
                        "gravity_projectile",
                        EchoesAbilities.GRAVITY_PROJECTILE,
                        ability -> ability.resolveLevelToUnlock(4))
                .addAbility("freeze", EchoesAbilities.FREEZE)
                .addAbility(
                        "freeze_self",
                        EchoesAbilities.FREEZE_SELF)
                .addAbility(
                        "gravity_infuse",
                        EchoesAbilities.GRAVITY_INFUSE,
                        ability -> ability.resolveLevelToUnlock(4))
                .addAbility("hand_freeze", EchoesAbilities.HAND_FREEZE)
                // R772 F11: the master's self hand freeze and cancel actions
                .addAbility("hand_freeze_self", EchoesAbilities.HAND_FREEZE_SELF)
                .addAbility("freeze_cancel", EchoesAbilities.FREEZE_CANCEL)
                .addAbility("hand_freeze_cancel", EchoesAbilities.HAND_FREEZE_CANCEL)
                .addAbility("freeze_self_cancel", EchoesAbilities.FREEZE_SELF_CANCEL)
                .addAbility("hand_freeze_self_cancel", EchoesAbilities.HAND_FREEZE_SELF_CANCEL)
                .addAbility("gravity_infuse_cancel", EchoesAbilities.GRAVITY_INFUSE_CANCEL)
                // master InitStands 274-291: the heavy's finisher variation, at Resolve 3, ACT3's own level (the 092 donor
                // had 5), with heavy_attack at its PERFORM
                .addAbility("finisher", EchoesAbilities.ACT3_FINISHER, finisher -> {
                    finisher.heavyPunchPerformSound(EchoesSounds.HEAVY_ATTACK);
                    finisher.initIsFinisher("heavy_punch");
                })
                .addAbility("select_act", EchoesAbilities.SELECT_ACT)
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            "gravity_projectile",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar("select_act", 0, InputMethod.CLICK)
                    // master: each hand variant and the infuse cancel are the Shift variation of their slot
                    .addToHotbar("freeze", 0, InputMethod.CLICK)
                    .addHotbarSlotVariation("hand_freeze", "freeze", InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar("freeze_cancel", 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            "hand_freeze_cancel", "freeze_cancel", InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar(
                            "freeze_self",
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            "hand_freeze_self", "freeze_self", InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar("freeze_self_cancel", 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            "hand_freeze_self_cancel", "freeze_self_cancel", InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "gravity_infuse",
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            "gravity_infuse_cancel", "gravity_infuse", InputKey.Modifier.SHIFT, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("barrage"))
                .addSkill(StandUnlockableSkill.startingAbility("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "gravity_projectile"))
                .addSkill(StandUnlockableSkill.startingAbility("freeze"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "freeze_self"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "gravity_infuse"))
                .addSkill(StandUnlockableSkill.startingAbility("hand_freeze"))
                .addSkill(StandUnlockableSkill.startingAbility("hand_freeze_self"))
                .addSkill(StandUnlockableSkill.startingAbility("freeze_cancel"))
                .addSkill(StandUnlockableSkill.startingAbility("hand_freeze_cancel"))
                .addSkill(StandUnlockableSkill.startingAbility("freeze_self_cancel"))
                .addSkill(StandUnlockableSkill.startingAbility("hand_freeze_self_cancel"))
                .addSkill(StandUnlockableSkill.startingAbility("gravity_infuse_cancel"))
                .addSkill(StandUnlockableSkill.startingAbility("finisher"))
                .addSkill(StandUnlockableSkill.startingAbility("select_act"))
                .addSkill(StandUnlockableSkill.startingAbility("grab"))
                // the shared grab chain's 100-point throw bundle, as the core's Star Platinum
                .addSkill(StandUnlockableSkill.unlockableAbility("grab_throw", 100)
                        .withAbility("grab_punch", "grab_barrage",
                                "grab_heavy_punch")
                        .prerequisiteSkill("grab"))
                .addHumanoidStandSkills();

        return new EntityStandType(
                // the author's ACT3 stats (master EchoesStandStats and his 1.21.1 AddonStands): power 11, speed 11,
                // durability 11, precision 8; range his 1.21.1 3 to 5, as the core's close-range Stand needs a decay
                // interval and the master's 5.1 / 3 has none
                new StandStats.Builder()
                        .power(11.0, 11.0)
                        .speed(11.0, 11.0)
                        .range(3.0, 5.0)
                        .durability(11.0, 11.0)
                        .precision(8.0)
                        .randomWeight(0.0D)
                        .build(),
                moveset,
                EchoesEntities.ACT3.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.5F, 1.5F)
                // master EchoesEntity: one of the author's ACT3 voices, the ACT3 summon stinger, the recall stinger
                .summonShout(() -> EchoesSounds.SUMMON_VOICE_ACT3)
                .summonSound(() -> EchoesSounds.ECHOES_SUMMON_BGM_ACT3)
                .unsummonSound(() -> EchoesSounds.ECHOES_RECALL_BGM);
    }
}
