package rotp.addons.kingcrimson;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.control.CreeperFuseSuppressionProviders;
import rotp.core.api.control.PlayerOperationPolicies;
import rotp.core.api.perception.PerceptionBlockers;
import rotp.core.api.power.AbilityRefusals;
import rotp.core.api.simulation.EntitySimulations;
import rotp.core.api.stand.StandStaminaModifiers;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.kingcrimson.ability.KingCrimsonChopAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonBarrageAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonBloodThrowAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonDesperateEyejabAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonEpitaphAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonGroundPunchAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonGroundSlamAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonHeavyPunchAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonImpaleAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonProjectileThrowAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonPassiveEraseAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeEraseAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeEraseStepAbility;
import rotp.addons.kingcrimson.state.KingCrimsonCreatureErase;
import rotp.addons.kingcrimson.state.KingCrimsonReflexErase;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeSkipAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonTripleProjectileThrowAbility;
import rotp.addons.kingcrimson.effect.KingCrimsonBleedingEffect;
import rotp.addons.kingcrimson.effect.KingCrimsonGapingWoundEffect;
import rotp.addons.kingcrimson.effect.KingCrimsonHalfBlindnessEffect;
import rotp.addons.kingcrimson.effect.KingCrimsonMangledBodyEffect;
import rotp.addons.kingcrimson.effect.KingCrimsonTimeEraseEffect;
import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;
import rotp.addons.kingcrimson.entity.KingCrimsonDriftBodyEntity;
import rotp.addons.kingcrimson.entity.KingCrimsonPhantomCarrierEntity;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.human.ForcedWalk;
import rotp.addons.kingcrimson.epitaph.human.PhantomBrain;
import rotp.addons.kingcrimson.epitaph.window.WindowBoundary;
import rotp.addons.kingcrimson.epitaph.window.WindowCover;
import rotp.addons.kingcrimson.network.BloodThrowSplashPayload;
import rotp.addons.kingcrimson.network.EpitaphForcedWalkPayload;
import rotp.addons.kingcrimson.network.EpitaphNoticePayload;
import rotp.addons.kingcrimson.network.EpitaphPhantomFramePayload;
import rotp.addons.kingcrimson.network.EpitaphSessionPayload;
import rotp.addons.kingcrimson.network.EpitaphShortcutPayload;
import rotp.addons.kingcrimson.network.TemporalStateSyncPayload;
import rotp.addons.kingcrimson.network.TimeEraseAfterimageRemovePayload;
import rotp.addons.kingcrimson.network.TimeEraseAfterimageSpawnPayload;
import rotp.addons.kingcrimson.network.TimeEraseShiftPayload;
import rotp.addons.kingcrimson.network.TimeEraseTransitionPayload;
import rotp.addons.kingcrimson.stand.KingCrimsonStandType;
import rotp.addons.kingcrimson.state.KingCrimsonBloodThrowService;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseStepService;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(KingCrimsonAddon.MOD_ID)
public final class KingCrimsonAddon {
    public static final String MOD_ID = "rotp_kingcrimson";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    // Epitaph: the one who foresees is marked for the length of his prediction, so that others see the face on him.
    // Not saved. Told to everybody who sees him but a player his prediction is aimed at.
    public static final Supplier<AttachmentType<Unit>> EPITAPH_FORESEEING = ATTACHMENT_TYPES.register(
            "epitaph_foreseeing",
            () -> AttachmentType.builder(() -> Unit.INSTANCE)
                    .sync(EpitaphSessions::showsFaceTo, StreamCodec.unit(Unit.INSTANCE))
                    .build());

    // Saves of before R772.1 hold the state of the old Epitaph under this id. It is read into nothing and never written
    // again: such a save loads without an error line and has lost the entry after its next save.
    public static final Supplier<AttachmentType<Unit>> RETIRED_EPITAPH_STATE = ATTACHMENT_TYPES.register(
            "epitaph_state",
            () -> AttachmentType.builder(() -> Unit.INSTANCE)
                    .serialize(new IAttachmentSerializer<Tag, Unit>() {
                        @Override
                        public Unit read(IAttachmentHolder holder, Tag tag, HolderLookup.Provider provider) {
                            return Unit.INSTANCE;
                        }

                        @Override
                        public Tag write(Unit attachment, HolderLookup.Provider provider) {
                            return null;
                        }
                    })
                    .build());
    public static final Supplier<AttachmentType<KingCrimsonTemporalState>>
            TEMPORAL_STATE = ATTACHMENT_TYPES.register(
                    "temporal_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Player
                                            ? new KingCrimsonTemporalState()
                                            : null)
                            .build());

    public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>>
            KING_CRIMSON_ENTITY = ENTITY_TYPES.register(
                    "kingcrimson",
                    key -> EntityType.Builder.<StandEntity>of(
                                    StandEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(
                                    KingCrimsonPhase1Spec.ENTITY_WIDTH,
                                    KingCrimsonPhase1Spec.ENTITY_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>,
            EntityType<KingCrimsonAfterimageEntity>>
            KC_AFTERIMAGE_ENTITY = ENTITY_TYPES.register(
                    "kc_afterimage",
                    key -> EntityType.Builder
                            .<KingCrimsonAfterimageEntity>of(
                                    KingCrimsonAfterimageEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.6F, 1.8F)
                            .setShouldReceiveVelocityUpdates(false)
                            .setUpdateInterval(Integer.MAX_VALUE)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>,
            EntityType<KingCrimsonTimeEraseDecoyEntity>>
            TIME_ERASE_DECOY_ENTITY = ENTITY_TYPES.register(
                    "time_erase_decoy",
                    key -> EntityType.Builder
                            .<KingCrimsonTimeEraseDecoyEntity>of(
                                    KingCrimsonTimeEraseDecoyEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    // Epitaph: what a phantom is drawn through on the client of the one who foresees. No server ever makes one.
    public static final DeferredHolder<EntityType<?>, EntityType<KingCrimsonPhantomCarrierEntity>> PHANTOM_CARRIER_ENTITY =
            ENTITY_TYPES.register("epitaph_phantom_carrier", key -> EntityType.Builder
                    .<KingCrimsonPhantomCarrierEntity>of(KingCrimsonPhantomCarrierEntity::new, MobCategory.MISC)
                    .noSave()
                    .noSummon()
                    .sized(0.6F, 1.8F)
                    .setShouldReceiveVelocityUpdates(false)
                    .setUpdateInterval(Integer.MAX_VALUE)
                    .build(key.toString()));
    // Epitaph: the mind of a human phantom. It is never in a world, on either side.
    public static final DeferredHolder<EntityType<?>, EntityType<PhantomBrain>> PHANTOM_BRAIN_ENTITY =
            ENTITY_TYPES.register("epitaph_phantom_mind", key -> EntityType.Builder
                    .<PhantomBrain>of(PhantomBrain::new, MobCategory.MISC)
                    .noSave()
                    .noSummon()
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(0)
                    .build(key.toString()));
    // The erase of a creature: where its fake body sees a player. On the server alone; no client is told of one.
    public static final DeferredHolder<EntityType<?>, EntityType<KingCrimsonDriftBodyEntity>> TIME_ERASE_DRIFT_BODY_ENTITY =
            ENTITY_TYPES.register("time_erase_drift_body", key -> EntityType.Builder
                    .<KingCrimsonDriftBodyEntity>of(KingCrimsonDriftBodyEntity::new, MobCategory.MISC)
                    .noSave()
                    .noSummon()
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(0)
                    .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_SUMMON = sound("kingcrimson_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_UNSUMMON = sound("kingcrimson_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_PUNCH = sound("kingcrimson_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_HEAVYPUNCH = sound("kingcrimson_heavypunch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_KINGCRIMSON = sound("diavolo_kingcrimson");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_PUNCH = sound("diavolo_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_BARRAGE = sound("diavolo_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DOPPIO_BARRAGE = sound("doppio_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_HEAVYPUNCH = sound("diavolo_heavypunch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_CHOP = sound("kingcrimson_chop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_IMPALE = sound("kingcrimson_impale");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_CHOP = sound("diavolo_chop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_IMPALE = sound("diavolo_impale");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DOPPIO_THERE_YOU_ARE = sound("doppio_there_you_are");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DOPPIO_DAMNIT = sound("doppio_damnit");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_EPITAPH = sound("kingcrimson_epitaph");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_DASH = sound("kingcrimson_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_DASH = sound("diavolo_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_EPITAPH = sound("diavolo_epitaph");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_GROUNDPUNCH =
                    sound("kingcrimson_groundpunch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_DESPERATE_EYEJAB =
                    sound("kingcrimson_desperate_eyejab");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_COMBOFINISHER = sound("diavolo_combofinisher");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DOPPIO_TAKE_THIS = sound("doppio_take_this");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_BLOODTHROW = sound("kingcrimson_bloodthrow");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            EPITAPH_TIMESKIP = SOUND_EVENTS.register(
                    "a",
                    () -> SoundEvent.createVariableRangeEvent(
                            id("epitaph_timeskip")));
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_TIMESKIP = sound("kingcrimson_timeskip");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_TIMESKIP2 = sound("kingcrimson_timeskip2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_TIMEERASE = sound("kingcrimson_timeerase");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TIME_ERASE_START = sound("time_erase_start");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TIME_ERASE_END = sound("time_erase_end");
    public static final OstSoundList KINGCRIMSON_OST =
            new OstSoundList(id("kc_ost"), SOUND_EVENTS);

    public static final DeferredHolder<MobEffect, KingCrimsonBleedingEffect>
            BLEEDING = MOB_EFFECTS.register(
                    "bleeding",
                    KingCrimsonBleedingEffect::new);
    public static final DeferredHolder<
            MobEffect,
            KingCrimsonGapingWoundEffect> GAPING_WOUND =
                    MOB_EFFECTS.register(
                            "gaping_wound",
                            KingCrimsonGapingWoundEffect::new);
    public static final DeferredHolder<
            MobEffect,
            KingCrimsonMangledBodyEffect> MANGLED_BODY =
                    MOB_EFFECTS.register(
                            "mangled_body",
                            KingCrimsonMangledBodyEffect::new);
    public static final DeferredHolder<
            MobEffect,
            KingCrimsonHalfBlindnessEffect> HALF_BLINDNESS_LEFT =
                    MOB_EFFECTS.register(
                            "half_blindness_left",
                            KingCrimsonHalfBlindnessEffect::new);
    public static final DeferredHolder<
            MobEffect,
            KingCrimsonHalfBlindnessEffect> HALF_BLINDNESS_RIGHT =
                    MOB_EFFECTS.register(
                            "half_blindness_right",
                            KingCrimsonHalfBlindnessEffect::new);
    public static final DeferredHolder<
            MobEffect,
            KingCrimsonTimeEraseEffect> TIME_ERASE =
                    MOB_EFFECTS.register(
                            "time_erase",
                            KingCrimsonTimeEraseEffect::new);

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonBarrageAbility>>
            KING_CRIMSON_BARRAGE_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_barrage",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonBarrageAbility>>
            KING_CRIMSON_DOPPIO_BARRAGE_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_doppio_barrage",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonBarrageAbility::doppio));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonChopAbility>>
            KING_CRIMSON_CHOP_ABILITY = ABILITY_TYPES.register(
                    "kingcrimson_chop",
                    key -> new AbilityType<>(
                            key, KingCrimsonChopAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonImpaleAbility>>
            KING_CRIMSON_IMPALE_ABILITY = ABILITY_TYPES.register(
                    "kingcrimson_impale",
                    key -> new AbilityType<>(
                            key, KingCrimsonImpaleAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonProjectileThrowAbility>>
            KING_CRIMSON_PROJECTILE_THROW_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_projectile_throw",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonProjectileThrowAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonTripleProjectileThrowAbility>>
            KING_CRIMSON_PROJECTILE_THROW_TRIPPLE_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_projectile_throw_tripple",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonTripleProjectileThrowAbility
                                            ::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonEpitaphAbility>>
            KING_CRIMSON_EPITAPH_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_epitaph",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonEpitaphAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonHeavyPunchAbility>>
            KING_CRIMSON_HEAVY_PUNCH_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_heavy_punch",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonGroundPunchAbility>>
            KING_CRIMSON_GROUND_PUNCH_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_groundpunch",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonGroundPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonGroundSlamAbility>>
            KING_CRIMSON_GROUND_SLAM_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_groundslam",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonGroundSlamAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonDesperateEyejabAbility>>
            KING_CRIMSON_DESPERATE_EYEJAB_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_desperate_eyejab",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonDesperateEyejabAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonTimeSkipAbility>>
            KING_CRIMSON_TIME_SKIP_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_timeskip",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonTimeSkipAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonTimeEraseAbility>>
            KING_CRIMSON_TIME_ERASE_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_timeerase",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonTimeEraseAbility::new));
    // Owner 2026-11-07: the Time Erase step, a regular skill on a slot of its own.
    public static final DeferredHolder<AbilityType<?>, AbilityType<KingCrimsonTimeEraseStepAbility>>
            KING_CRIMSON_TIME_ERASE_STEP_ABILITY = ABILITY_TYPES.register(
                    KingCrimsonTimeEraseStepService.ABILITY,
                    key -> new AbilityType<>(key, KingCrimsonTimeEraseStepAbility::new));
    // Owner 2026-11-07: Blood Throw, the variation of Time Erase's slot, on the donor's unused sound and icon.
    public static final DeferredHolder<AbilityType<?>, AbilityType<KingCrimsonBloodThrowAbility>>
            KING_CRIMSON_BLOOD_THROW_ABILITY = ABILITY_TYPES.register(
                    KingCrimsonBloodThrowService.ABILITY,
                    key -> new AbilityType<>(key, KingCrimsonBloodThrowAbility::new));
    // Owner 2026-11-07: the passive that took the place of Epitaph's dodge, as two skills with a cooldown each.
    public static final DeferredHolder<AbilityType<?>, AbilityType<KingCrimsonPassiveEraseAbility>>
            KING_CRIMSON_REFLEX_ERASE_ABILITY = ABILITY_TYPES.register(
                    KingCrimsonReflexErase.REFLEX,
                    key -> new AbilityType<>(key, KingCrimsonPassiveEraseAbility::new));
    public static final DeferredHolder<AbilityType<?>, AbilityType<KingCrimsonPassiveEraseAbility>>
            KING_CRIMSON_LAST_CHANCE_ERASE_ABILITY = ABILITY_TYPES.register(
                    KingCrimsonReflexErase.LAST_CHANCE,
                    key -> new AbilityType<>(key, KingCrimsonPassiveEraseAbility::new));

    public static final DeferredHolder<StandType, EntityStandType>
            KING_CRIMSON = STAND_TYPES.register(
                    "kingcrimson",
                    KingCrimsonAddon::createStand);

    public KingCrimsonAddon(IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_OBSERVER_WORLD_RENDER_POLICIES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_CLIENT_SKY_RENDERERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_LIVING_ENTITY_MATERIAL_TINT_POLICIES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP King Crimson requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and Stand visual "
                            + "contexts, managed addon post effects, and "
                            + "observer world, sky, and living-material "
                            + "render policies.");
        }
        // the time skip calls Ability.checkPerformerStun (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ABILITY_PERFORMER_STUN_V1)) {
            throw new IllegalStateException(
                    "RotP King Crimson requires the core performer stun "
                            + "check (a core from the same release).");
        }
        // the time skip's player action lives in its user's Stand-power slot
        if (!RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_PLAYER_ACTION_GATE_V1)) {
            throw new IllegalStateException(
                    "RotP King Crimson requires the core Stand-power "
                            + "action slot and player action gate (a core "
                            + "from the same release).");
        }

        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        ABILITY_TYPES.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(
                KingCrimsonAddon::registerPayloads);
        modEventBus.addListener(
                KingCrimsonGameEvents::registerAttributes);
        // A2-UNSEEN: the core's rays, aims, Stand picks and blinks, and other add-ons' targeting, ask this rule.
        PerceptionBlockers.register(
                id("time_erase"),
                (viewer, target, sight) -> KingCrimsonTimeEraseDecoyEntity
                        .blocksPerception(viewer, target, sight));
        // The erase of a creature: the fake body of a creeper never goes off.
        CreeperFuseSuppressionProviders.register(id("creature_erase_fake_body"), KingCrimsonCreatureErase::isFakeBody);
        // Epitaph: a phantom is a copy outside the world that shares its body's int id; the core's own state leaves it out.
        EntitySimulations.register(id("epitaph_phantoms"), EpitaphSessions::simulates);
        EntityTimeRates.register(id("epitaph"), EpitaphSessions::rate);
        // The bar of a user who is walked in the slow phase fills at the pace of his track.
        StandStaminaModifiers.registerTickGain(id("epitaph_walk"), EpitaphSessions::staminaGain);
        // A player who is walked along his foreseen track uses no ability but the erase that frees him.
        AbilityRefusals.register(id("epitaph_forced_walk"), ForcedWalk::refuse);
        // In the window of a prediction a participant opens no container and takes no crafting result.
        PlayerOperationPolicies.register(id("epitaph_window"), WindowBoundary::decide);
        WindowCover.check();
        // 1.16 KCConfig: COMMON, rotp_kingcrimson.toml
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                KingCrimsonConfig.SPEC,
                KingCrimsonConfig.FILE_NAME);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static KingCrimsonTemporalState temporalState(
            Player player) {
        return player.getData(TEMPORAL_STATE);
    }

    public static boolean isKingCrimsonUser(Player player) {
        StandPower power = StandPower.get(player);
        return power != null
                && power.getPowerType() == KING_CRIMSON.get();
    }

    public static void syncTemporalState(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                TemporalStateSyncPayload.from(
                        player, temporalState(player)));
    }

    public static void syncTemporalState(
            ServerPlayer observer, Player target) {
        PacketDistributor.sendToPlayer(
                observer,
                TemporalStateSyncPayload.from(
                        target, temporalState(target)));
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        // "3" since R772.1 added the erase's kind and its key-up flag ("2": R772's time-stop pause flag): an older
        // client is refused at login.
        event.registrar("3").playToClient(
                TemporalStateSyncPayload.TYPE,
                TemporalStateSyncPayload.STREAM_CODEC,
                TemporalStateSyncPayload::handle);
        // "2" since R145 dropped its trailing boolean: a port.18 and port.19 pair is refused at login.
        event.registrar("2").playToClient(
                TimeEraseAfterimageSpawnPayload.TYPE,
                TimeEraseAfterimageSpawnPayload.STREAM_CODEC,
                TimeEraseAfterimageSpawnPayload::handle);
        event.registrar("1").playToClient(
                TimeEraseAfterimageRemovePayload.TYPE,
                TimeEraseAfterimageRemovePayload.STREAM_CODEC,
                TimeEraseAfterimageRemovePayload::handle);
        event.registrar("1").playToClient(
                TimeEraseTransitionPayload.TYPE,
                TimeEraseTransitionPayload.STREAM_CODEC,
                TimeEraseTransitionPayload::handle);
        event.registrar("1").playToClient(
                BloodThrowSplashPayload.TYPE,
                BloodThrowSplashPayload.STREAM_CODEC,
                BloodThrowSplashPayload::handle);
        event.registrar("1").playToClient(
                TimeEraseShiftPayload.TYPE,
                TimeEraseShiftPayload.STREAM_CODEC,
                TimeEraseShiftPayload::handle);
        // Epitaph (R772.1): to the one who foresees alone.
        event.registrar("1").playToClient(EpitaphSessionPayload.TYPE, EpitaphSessionPayload.STREAM_CODEC,
                EpitaphSessionPayload::handle);
        event.registrar("1").playToClient(EpitaphPhantomFramePayload.TYPE, EpitaphPhantomFramePayload.STREAM_CODEC,
                EpitaphPhantomFramePayload::handle);
        event.registrar("1").playToClient(EpitaphForcedWalkPayload.TYPE, EpitaphForcedWalkPayload.STREAM_CODEC,
                EpitaphForcedWalkPayload::handle);
        event.registrar("1").playToClient(EpitaphShortcutPayload.TYPE, EpitaphShortcutPayload.STREAM_CODEC,
                EpitaphShortcutPayload::handle);
        // To a player a prediction is aimed at whom a rule of another add-on exempts, and to nobody else.
        event.registrar("1").playToClient(EpitaphNoticePayload.TYPE, EpitaphNoticePayload.STREAM_CODEC,
                EpitaphNoticePayload::handle);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(
                name,
                SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        "punch",
                        ModStandAbilities.PUNCH,
                        punch -> {
                            punch.detachedLightCounterKnockback();
                            // 1.16 StandEntityTask reads PERFORM and RECOVERY when each phase starts
                            punch.laterPhaseEntryTiming();
                            punch.punchImpactSound(KINGCRIMSON_PUNCH);
                            punch.standCrySound(DIAVOLO_PUNCH);
                        })
                .addAbility(
                        "barrage",
                        KING_CRIMSON_BARRAGE_ABILITY,
                        barrage -> {
                            barrage.barrageHitSound(KINGCRIMSON_PUNCH);
                            barrage.barrageCrySound(DIAVOLO_BARRAGE);
                        })
                .addAbility(
                        "kingcrimson_doppio_barrage",
                        KING_CRIMSON_DOPPIO_BARRAGE_ABILITY,
                        barrage -> {
                            barrage.barrageHitSound(KINGCRIMSON_PUNCH);
                            barrage.barrageCrySound(DOPPIO_BARRAGE);
                            // 1.16 standSound(DOPPIO_BARRAGE) also cried with the arms only.
                            barrage.barrageCryInArmsOnly();
                        })
                .addAbility(
                        "heavy_punch",
                        KING_CRIMSON_HEAVY_PUNCH_ABILITY,
                        punch -> {
                            punch.detachedPunchKnockback();
                            punch.recoveryPhaseEntryTiming();
                            punch.partsRequired(StandPart.MAIN_BODY);
                            punch.heavyPunchImpactSound(
                                    KINGCRIMSON_HEAVYPUNCH);
                            punch.heavyPunchCrySound(DIAVOLO_HEAVYPUNCH);
                        })
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility("grab_throw", ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        ModStandAbilities.PUNCH,
                        punch -> {
                            punch.punchImpactSound(KINGCRIMSON_PUNCH);
                            punch.standCrySound(DIAVOLO_PUNCH);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(KINGCRIMSON_PUNCH);
                            barrage.barrageCrySound(DIAVOLO_BARRAGE);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    KINGCRIMSON_HEAVYPUNCH);
                            punch.heavyPunchCrySound(DIAVOLO_HEAVYPUNCH);
                            punch.initIsGrabVariation();
                        })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility(
                        "kingcrimson_chop",
                        KING_CRIMSON_CHOP_ABILITY,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        "kingcrimson_impale",
                        KING_CRIMSON_IMPALE_ABILITY,
                        ability -> ability.resolveLevelToUnlock(4))
                .addAbility(
                        "kingcrimson_projectile_throw",
                        KING_CRIMSON_PROJECTILE_THROW_ABILITY,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        "kingcrimson_projectile_throw_tripple",
                        KING_CRIMSON_PROJECTILE_THROW_TRIPPLE_ABILITY,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        "kingcrimson_epitaph",
                        KING_CRIMSON_EPITAPH_ABILITY,
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility(
                        "kingcrimson_groundpunch",
                        KING_CRIMSON_GROUND_PUNCH_ABILITY)
                .addAbility(
                        "kingcrimson_groundslam",
                        KING_CRIMSON_GROUND_SLAM_ABILITY)
                .addAbility(
                        "kingcrimson_desperate_eyejab",
                        KING_CRIMSON_DESPERATE_EYEJAB_ABILITY)
                .addAbility(
                        "kingcrimson_timeskip",
                        KING_CRIMSON_TIME_SKIP_ABILITY,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        "kingcrimson_timeerase",
                        KING_CRIMSON_TIME_ERASE_ABILITY,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        KingCrimsonTimeEraseStepService.ABILITY,
                        KING_CRIMSON_TIME_ERASE_STEP_ABILITY,
                        ability -> ability.resolveLevelToUnlock(
                                KingCrimsonPhase5Spec.TIME_ERASE_STEP_UNLOCK_RESOLVE_LEVEL))
                .addAbility(
                        KingCrimsonBloodThrowService.ABILITY,
                        KING_CRIMSON_BLOOD_THROW_ABILITY,
                        ability -> ability.resolveLevelToUnlock(KingCrimsonPhase5Spec.TIME_ERASE_UNLOCK_RESOLVE_LEVEL))
                .addAbility(
                        KingCrimsonReflexErase.REFLEX,
                        KING_CRIMSON_REFLEX_ERASE_ABILITY,
                        ability -> ability.resolveLevelToUnlock(KingCrimsonPhase5Spec.TIME_ERASE_UNLOCK_RESOLVE_LEVEL))
                .addAbility(
                        KingCrimsonReflexErase.LAST_CHANCE,
                        KING_CRIMSON_LAST_CHANCE_ERASE_ABILITY,
                        ability -> ability.resolveLevelToUnlock(KingCrimsonPhase5Spec.TIME_ERASE_UNLOCK_RESOLVE_LEVEL))
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            "kingcrimson_chop",
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            "kingcrimson_impale",
                            "kingcrimson_chop",
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "kingcrimson_projectile_throw",
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            "kingcrimson_projectile_throw_tripple",
                            "kingcrimson_projectile_throw",
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "kingcrimson_epitaph",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "kingcrimson_timeskip",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "kingcrimson_timeerase",
                            0,
                            InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            KingCrimsonBloodThrowService.ABILITY,
                            "kingcrimson_timeerase",
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(
                            KingCrimsonTimeEraseStepService.ABILITY,
                            0,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("barrage")
                        .withAbility(
                                "kingcrimson_doppio_barrage"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_punch")
                        .withAbility(
                                "kingcrimson_groundpunch",
                                "kingcrimson_groundslam",
                                "kingcrimson_desperate_eyejab"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility("guard"))
                .addSkill(StandUnlockableSkill.startingAbility("grab"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "grab_throw",
                                100)
                        .withAbility(
                                "grab_punch",
                                "grab_barrage",
                                "grab_heavy_punch")
                        .prerequisiteSkill("grab"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "manual_control"));
        moveset
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "kingcrimson_chop",
                                (int) KingCrimsonPhase2Spec.CHOP_STAMINA)
                        .withAbility(
                                "kingcrimson_projectile_throw",
                                "kingcrimson_projectile_throw_tripple")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "kingcrimson_impale",
                                (int) KingCrimsonPhase2Spec.IMPALE_STAMINA)
                        .prerequisiteSkill("kingcrimson_chop"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "kingcrimson_epitaph",
                                (int) KingCrimsonPhase3Spec
                                        .EPITAPH_ACTIVATION_STAMINA)
                        .prerequisiteSkill("guard"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "kingcrimson_timeskip",
                                (int) KingCrimsonPhase5Spec
                                        .TIME_SKIP_STAMINA)
                        .prerequisiteSkill("kingcrimson_epitaph"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "kingcrimson_timeerase",
                                (int) KingCrimsonPhase5Spec
                                        .TIME_ERASE_ACTIVATION_STAMINA)
                        .withAbility(KingCrimsonBloodThrowService.ABILITY)
                        .prerequisiteSkill("kingcrimson_timeskip"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                KingCrimsonTimeEraseStepService.ABILITY,
                                (int) (KingCrimsonPhase5Spec.TIME_ERASE_STEP_COST_SHARE
                                        * KingCrimsonPhase5Spec.TIME_ERASE_ACTIVATION_STAMINA))
                        .prerequisiteSkill("kingcrimson_epitaph"));

        return new KingCrimsonStandType(
                new StandStats.Builder()
                        .power(KingCrimsonPhase1Spec.POWER)
                        .speed(KingCrimsonPhase1Spec.SPEED)
                        .range(
                                KingCrimsonPhase1Spec.EFFECTIVE_RANGE,
                                KingCrimsonPhase1Spec.MAX_RANGE)
                        .durability(KingCrimsonPhase1Spec.DURABILITY)
                        .precision(KingCrimsonPhase1Spec.PRECISION)
                        .randomWeight(1.0D)
                        .build(),
                moveset,
                KING_CRIMSON_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(
                        KingCrimsonPhase1Spec.ENTITY_WIDTH,
                        KingCrimsonPhase1Spec.ENTITY_HEIGHT)
                .summonShout(() -> DIAVOLO_KINGCRIMSON)
                .summonSound(() -> KINGCRIMSON_SUMMON)
                .unsummonSound(() -> KINGCRIMSON_UNSUMMON)
                .ost(() -> KINGCRIMSON_OST);
    }
}
