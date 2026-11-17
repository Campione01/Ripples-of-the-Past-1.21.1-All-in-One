package rotp.addons.gratefuldead;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.block.BlockRandomTickSuppressionProviders;
import rotp.core.api.power.AbilityRefusals;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.api.control.CreeperFuseSuppressionProviders;
import rotp.core.api.control.LivingSwingDurationModifiers;
import rotp.core.api.hamon.HamonEfficiencyFactors;
import rotp.core.api.stand.StandStaminaCostFactors;
import rotp.core.api.timestop.TimeStopTicksModifiers;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

import rotp.addons.gratefuldead.ability.AgingAbility;
import rotp.addons.gratefuldead.ability.AgingAbility.Mode;
import rotp.addons.gratefuldead.ability.AgingInjectAbility;
import rotp.addons.gratefuldead.ability.GratefulDeadGrabThrowAbility;
import rotp.addons.gratefuldead.ability.GratefulDeadHeavyPunchAbility;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.addons.gratefuldead.effect.AgingAuraEffect;
import rotp.addons.gratefuldead.entity.GratefulDeadStandEntity;
import rotp.addons.gratefuldead.network.BlockAgingPayload;
import rotp.addons.gratefuldead.service.AgingService;
import rotp.addons.gratefuldead.state.ItemAgingState;
import rotp.addons.gratefuldead.state.BlockAgingData;
import rotp.addons.gratefuldead.state.LivingAgingState;
import rotp.addons.gratefuldead.state.AgingRateState;
import rotp.addons.gratefuldead.item.IceCubeItem;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(GratefulDeadAddon.MOD_ID)
public final class GratefulDeadAddon {
    public static final String MOD_ID = "rotp_grateful_dead";
    public static final String LEGACY_NAMESPACE = "rotp_nextalbum";
    public static final String PUNCH = "the_grateful_dead_punch";
    public static final String BARRAGE = "the_grateful_dead_barrage";
    public static final String HEAVY_PUNCH =
            "the_grateful_dead_heavy_punch";
    public static final String BLOCK = "the_grateful_dead_block";
    public static final String AGING_TOUCH =
            "the_grateful_dead_aging_touch";
    public static final String AGING_AURA =
            "the_grateful_dead_aging_aura";
    public static final String REMOVE_AGING =
            "the_grateful_dead_remove_aging";
    public static final String AGING_SELF =
            "the_grateful_dead_aging_self";
    public static final String REMOVE_SELF_AGING =
            "the_grateful_dead_remove_self_aging";
    public static final String REMOVE_ALL_AGING =
            "the_grateful_dead_remove_all_aging";
    // Next Album 0.1.13 grab moveset, on the core's grab.
    public static final String GRAB_HEAVY_PUNCH = "grab_heavy_punch";
    public static final String FINISHER = "the_grateful_dead_finisher";
    public static final String AGING_INJECT =
            "the_grateful_dead_aging_inject";
    public static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG,
                    LEGACY_NAMESPACE);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES,
                    LEGACY_NAMESPACE);
    public static final DeferredRegister.DataComponents
            DATA_COMPONENT_TYPES =
                    DeferredRegister.createDataComponents(
                            Registries.DATA_COMPONENT_TYPE,
                            LEGACY_NAMESPACE);
    public static final DeferredRegister<EntityType<?>>
            ENTITY_TYPES = DeferredRegister.create(
                    Registries.ENTITY_TYPE,
                    LEGACY_NAMESPACE);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(LEGACY_NAMESPACE);
    public static final DeferredRegister<SoundEvent>
            SOUND_EVENTS = DeferredRegister.create(
                    Registries.SOUND_EVENT,
                    LEGACY_NAMESPACE);
    public static final DeferredRegister<StandType>
            STAND_TYPES = DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG,
                    "rotp_nextalbum");
    public static final DeferredRegister<EntityCustomEffectType<?>>
            STAND_EFFECT_TYPES = DeferredRegister.create(
                    JojoRegistries.STAND_EFFECTS_REG,
                    LEGACY_NAMESPACE);
    public static final DeferredRegister<ParticleType<?>>
            PARTICLE_TYPES = DeferredRegister.create(
                    Registries.PARTICLE_TYPE,
                    LEGACY_NAMESPACE);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AgingAbility>>
            AGING_TOUCH_TYPE = agingAbility(AGING_TOUCH, Mode.TOUCH);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AgingAbility>>
            AGING_AURA_TYPE = agingAbility(AGING_AURA, Mode.AURA);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AgingAbility>>
            REMOVE_AGING_TYPE =
                    agingAbility(REMOVE_AGING, Mode.REMOVE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AgingAbility>>
            AGING_SELF_TYPE = agingAbility(AGING_SELF, Mode.SELF);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AgingAbility>>
            REMOVE_SELF_AGING_TYPE =
                    agingAbility(REMOVE_SELF_AGING, Mode.REMOVE_SELF);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AgingAbility>>
            REMOVE_ALL_AGING_TYPE =
                    agingAbility(REMOVE_ALL_AGING, Mode.REMOVE_ALL);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AgingInjectAbility>>
            AGING_INJECT_TYPE = ABILITY_TYPES.register(
                    AGING_INJECT,
                    key -> new AbilityType<>(
                            key, AgingInjectAbility::new));

    public static final Supplier<AttachmentType<AgingRateState>>
            AGING_RATE = ATTACHMENT_TYPES.register(
                    "aging_rate",
                    () -> AttachmentType.serializable(
                                    AgingRateState::new)
                            .build());

    public static final Supplier<DataComponentType<Float>>
            ICE_MELT =
                    DATA_COMPONENT_TYPES.registerComponentType(
                            "ice_melt",
                            builder -> builder
                                    .persistent(Codec.FLOAT)
                                    .networkSynchronized(
                                            ByteBufCodecs.FLOAT));

    public static final DeferredItem<IceCubeItem> ICE_CUBE =
            ITEMS.registerItem("ice_cube", IceCubeItem::new);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            AGING_MIST = PARTICLE_TYPES.register(
                    "aging_mist",
                    () -> new SimpleParticleType(false));

    public static final Supplier<AttachmentType<LivingAgingState>>
            LIVING_AGING = ATTACHMENT_TYPES.register(
                    "living_aging",
                    () -> AttachmentType.serializable(
                                    holder ->
                                            holder
                                                    instanceof net.minecraft
                                                            .world.entity
                                                            .LivingEntity
                                            ? new LivingAgingState()
                                            : null)
                            .sync(LivingAgingState.STREAM_CODEC)
                            .build());

    public static final Supplier<DataComponentType<ItemAgingState>>
            ITEM_AGING =
                    DATA_COMPONENT_TYPES.registerComponentType(
                            "item_aging",
                            builder -> builder
                                    .persistent(ItemAgingState.CODEC)
                                    .networkSynchronized(
                                            ItemAgingState.STREAM_CODEC));

    public static final DeferredHolder<
            EntityType<?>, EntityType<GratefulDeadStandEntity>>
            THE_GRATEFUL_DEAD_ENTITY = ENTITY_TYPES.register(
                    "the_grateful_dead",
                    key -> EntityType.Builder
                            .<GratefulDeadStandEntity>of(
                                    GratefulDeadStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.65F, 1.8F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredItem<Item> PLANT_AGING_DUST =
            ITEMS.registerSimpleItem("plant_aging_dust");

    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_GRATEFUL_DEAD_SOUND = SOUND_EVENTS.register(
                    "the_grateful_dead",
                    SoundEvent::createVariableRangeEvent);

    // Next Album 0.1.13 InitSounds: the Stand's own summon (one of two,
    // its MultiSoundEvent) and unsummon, and Prosciutto's two shout sets.
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_GRATEFUL_DEAD_SUMMON = sound("the_grateful_dead_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_GRATEFUL_DEAD_SUMMON_2 =
                    sound("the_grateful_dead_summon_2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_GRATEFUL_DEAD_UNSUMMON =
                    sound("the_grateful_dead_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PROSCIUTTO_KILL = sound("prosciutto_kill");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PROSCIUTTO_MUDA = sound("prosciutto_muda");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PROSCIUTTO_TAKE_THIS = sound("prosciutto_take_this");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PROSCIUTTO_ORA = sound("prosciutto_ora");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PROSCIUTTO_NOISY = sound("prosciutto_noisy");
    private static final List<DeferredHolder<SoundEvent, SoundEvent>>
            SUMMON_SOUNDS = List.of(
                    THE_GRATEFUL_DEAD_SUMMON,
                    THE_GRATEFUL_DEAD_SUMMON_2);
    // PROSCIUTTO_AGING_PUNCH: the Aging Punch and the finisher
    private static final List<DeferredHolder<SoundEvent, SoundEvent>>
            AGING_PUNCH_SHOUTS = List.of(
                    PROSCIUTTO_KILL,
                    PROSCIUTTO_MUDA,
                    PROSCIUTTO_TAKE_THIS,
                    PROSCIUTTO_ORA,
                    PROSCIUTTO_NOISY);
    // PROSCIUTTO_HEAVY_SHOUT: the heavy punch and the throw
    private static final List<DeferredHolder<SoundEvent, SoundEvent>>
            HEAVY_SHOUTS = List.of(
                    PROSCIUTTO_TAKE_THIS,
                    PROSCIUTTO_ORA,
                    PROSCIUTTO_NOISY);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GratefulDeadHeavyPunchAbility>>
            HEAVY_PUNCH_TYPE = ABILITY_TYPES.register(
                    HEAVY_PUNCH,
                    key -> new AbilityType<>(
                            key, GratefulDeadHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GratefulDeadGrabThrowAbility>>
            GRAB_THROW_TYPE = ABILITY_TYPES.register(
                    "the_grateful_dead_throw",
                    key -> new AbilityType<>(
                            key, GratefulDeadGrabThrowAbility::new));

    public static final DeferredHolder<
            EntityCustomEffectType<?>,
            EntityCustomEffectType<AgingAuraEffect>>
            AGING_AURA_EFFECT = STAND_EFFECT_TYPES.register(
                    "the_grateful_dead_aging_aura",
                    key -> new EntityCustomEffectType<>(
                            key, AgingAuraEffect::new));

    public static final DeferredHolder<StandType, EntityStandType>
            THE_GRATEFUL_DEAD = STAND_TYPES.register(
                    "the_grateful_dead",
                    GratefulDeadAddon::createStand);

    public GratefulDeadAddon(IEventBus modEventBus, ModContainer modContainer) {
        requireCoreApi();
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                GratefulDeadConfig.COMMON_SPEC,
                "rotp_grateful_dead-common.toml");
        modContainer.registerConfig(
                ModConfig.Type.CLIENT,
                GratefulDeadConfig.CLIENT_SPEC,
                "rotp_grateful_dead-client.toml");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            rotp.addons.gratefuldead.client.GratefulDeadClientEvents
                    .registerConfigScreen(modContainer);
        }
        // 0.1.13 ActionAgingMixin / PowerBaseImplAgingMixin: a user aged to 0
        // Stand durability can no longer use the Stand (Sex Pistols excepted).
        AbilityRefusals.register(
                ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, "aging_stand_lock"),
                (ability, context, user) ->
                        context instanceof StandPower power
                                && !isSexPistols(power)
                                && AgingService.cannotUseStand(user)
                        ? ConditionCheck.NEGATIVE
                        : null);
        // 0.1.13 TheGratefulDeadBlockAction: no guard while the Stand holds someone.
        AbilityRefusals.register(
                ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, "grab_block_lock"),
                (ability, context, user) ->
                        context instanceof StandPower power
                                && BLOCK.equals(ability.abilityId.nameInMoveset())
                                && power.getSummonedStandEntity() != null
                                && LivingComponentGrab.getEntityGrabbedBy(
                                        power.getSummonedStandEntity()) != null
                        ? ConditionCheck.NEGATIVE
                        : null);
        CreeperFuseSuppressionProviders.register(
                ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, "aging"),
                creeper -> creeper.hasData(LIVING_AGING)
                        && LivingAgingState.get(creeper)
                                .progress()
                                >= 0.25F);
        BlockRandomTickSuppressionProviders.register(
                ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, "aging"),
                query -> BlockAgingData.get(query.level())
                                .progress(query.position())
                        > 0.0F);
        LivingSwingDurationModifiers.register(
                ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, "aging"),
                query -> agedSwingDuration(
                        query.entity(),
                        query.currentDuration()));
        // 1.16 TimeStopInstantAgingMixin: aging cuts the blink too
        TimeStopTicksModifiers.register(
                ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, "aging"),
                AgingService::agedTimeStopBlinkTicks);
        StandStaminaCostFactors.register(
                ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, "aging_stamina"),
                (ability, power, baseCost, ticking) ->
                        AgingService.skillStaminaCostFactor(
                                ability, power.getUser()));
        HamonEfficiencyFactors.register(
                ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, "aging_hamon"),
                AgingService::hamonEfficiency);
        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        DATA_COMPONENT_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        STAND_EFFECT_TYPES.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        modEventBus.addListener(
                GratefulDeadModEvents::registerAttributes);
        modEventBus.addListener(
                GratefulDeadAddon::registerPayloads);
        modEventBus.addListener(
                GratefulDeadAddon::addCreativeTabItems);
    }

    /** 1.16 (Next Album) listed the plant aging dust in the ROTP main tab. */
    private static void addCreativeTabItems(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == rotp.core.init.ModItems.MAIN_TAB.getKey()) {
            event.accept(PLANT_AGING_DUST.get());
            event.accept(ICE_CUBE.get());
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                LEGACY_NAMESPACE, path);
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<AgingAbility>>
            agingAbility(String path, Mode mode) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new AgingAbility(type, id, mode)));
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        event.registrar("3").playToClient(
                BlockAgingPayload.TYPE,
                BlockAgingPayload.STREAM_CODEC,
                BlockAgingPayload::handle);
    }

    private static EntityStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                // legless on very long arms, with 0.1.13's own jab: a boxer's combo does not fit that body
                .noPunchCombo()
                .addAbility(PUNCH, ModStandAbilities.PUNCH)
                .addAbility(BARRAGE, ModStandAbilities.BARRAGE)
                .addAbility(HEAVY_PUNCH, HEAVY_PUNCH_TYPE)
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(BLOCK, ModStandAbilities.GUARD)
                .addAbility(
                        FINISHER,
                        HEAVY_PUNCH_TYPE,
                        punch -> punch.initIsFinisher(HEAVY_PUNCH))
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility("grab_throw", GRAB_THROW_TYPE)
                .addAbility(
                        "grab_punch",
                        ModStandAbilities.PUNCH,
                        punch -> {
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> {
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        HEAVY_PUNCH_TYPE,
                        punch -> {
                            punch.initIsGrabVariation();
                        })
                .addAbility(AGING_INJECT, AGING_INJECT_TYPE)
                .addAbility(AGING_TOUCH, AGING_TOUCH_TYPE)
                .addAbility(AGING_AURA, AGING_AURA_TYPE)
                .addAbility(REMOVE_AGING, REMOVE_AGING_TYPE)
                .addAbility(AGING_SELF, AGING_SELF_TYPE)
                .addAbility(REMOVE_SELF_AGING, REMOVE_SELF_AGING_TYPE)
                .addAbility(REMOVE_ALL_AGING, REMOVE_ALL_AGING_TYPE)
                .makeControlScheme("hotbar")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
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
                            AGING_TOUCH,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            AGING_SELF,
                            0,
                            InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            REMOVE_SELF_AGING,
                            AGING_SELF,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(
                            AGING_AURA,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            REMOVE_AGING,
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            REMOVE_ALL_AGING,
                            REMOVE_AGING,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(BARRAGE))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                        "heavy_charged")
                                .prerequisiteSkill(HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(BLOCK))
                .addSkill(
                        StandUnlockableSkill.startingAbility(FINISHER)
                                .withAbility(AGING_INJECT)
                                .prerequisiteSkill(HEAVY_PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility("grab"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                        "grab_throw",
                        100)
                        .withAbility(
                                "grab_punch",
                                "grab_barrage",
                                "grab_heavy_punch")
                        .prerequisiteSkill("grab"))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                AGING_TOUCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                AGING_AURA))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                REMOVE_AGING))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                AGING_SELF))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                REMOVE_SELF_AGING))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                REMOVE_ALL_AGING))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                "manual_control"));

        return new EntityStandType(
                new StandStats.Builder()
                        .power(10.0D)
                        .speed(4.0D)
                        .range(2.0D, 10.0D)
                        .durability(20.0D)
                        .precision(4.0D)
                        .randomWeight(0.0D)
                        .build(),
                moveset,
                THE_GRATEFUL_DEAD_ENTITY.get(),
                id) {
            // Clearing the Stand drops the aura (see AgingAuraEffect).
            @Override
            public StandTypePersistentData newDataInstance() {
                return new AgingAuraEffect.StandData(this);
            }
        }
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.65F, 1.8F)
                .summonShout(() -> THE_GRATEFUL_DEAD_SOUND)
                .summonSound(GratefulDeadAddon::randomSummonSound)
                .unsummonSound(() -> THE_GRATEFUL_DEAD_UNSUMMON);
    }

    public static DeferredHolder<SoundEvent, SoundEvent> randomSummonSound() {
        return pick(SUMMON_SOUNDS);
    }

    public static DeferredHolder<SoundEvent, SoundEvent>
            randomAgingPunchShout() {
        return pick(AGING_PUNCH_SHOUTS);
    }

    public static DeferredHolder<SoundEvent, SoundEvent> randomHeavyShout() {
        return pick(HEAVY_SHOUTS);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> pick(
            List<DeferredHolder<SoundEvent, SoundEvent>> sounds) {
        return sounds.get(
                ThreadLocalRandom.current().nextInt(sounds.size()));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static boolean isSexPistols(StandPower power) {
        return power.getPowerType() != null
                && "sex_pistols".equals(
                        power.getPowerType().getId().getPath());
    }

    private static int agedSwingDuration(
            net.minecraft.world.entity.LivingEntity entity,
            int currentDuration) {
        net.minecraft.world.entity.LivingEntity aged = entity;
        boolean stand = entity instanceof StandEntity;
        if (entity instanceof StandEntity standEntity
                && standEntity.getUser() != null) {
            aged = standEntity.getUser();
        }
        if (aged == null
                || !aged.hasData(LIVING_AGING)) {
            return currentDuration;
        }
        LivingAgingState state = LivingAgingState.get(aged);
        float progress = state.progress();
        if (progress <= 0.0F
                || stand && state.selfInflicted(aged)) {
            return currentDuration;
        }
        // 0.1.13 getSwingDurationMultiplier: 1 / max(0.08, speed efficiency)
        double efficiency =
                LivingAgingState.strengthEfficiency(progress);
        if (efficiency >= 0.999D) {
            return currentDuration;
        }
        return Math.max(
                currentDuration,
                (int) Math.ceil(
                        currentDuration
                                / Math.max(0.08D, efficiency)));
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_RANDOM_WEIGHTS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_CREEPER_FUSE_SUPPRESSION_PROVIDERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_LIVING_BASE_MODEL_TINT_PROVIDERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ITEM_MATERIAL_TINT_POLICIES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_SELECTION_VISUAL_POLICIES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_BLOCK_RANDOM_TICK_SUPPRESSION_PROVIDERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_LIVING_SWING_DURATION_MODIFIERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_PLAYER_ARM_POSE_PROVIDERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_MATERIAL_TINT_POLICIES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_TICKS_MODIFIERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_ACTION_PORT_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_CONDITION_REFUSALS_V1)) {
            throw new IllegalStateException(
                    "RotP Grateful Dead requires ROTP addon ABI 1 "
                            + "with namespaced ability resources and "
                            + "Stand visual, time-stop, and addon "
                            + "post-effect lifecycle contexts and Stand "
                            + "random weights, Creeper fuse suppression, "
                            + "base living and item material tint, and "
                            + "ability selection visual policies, block "
                            + "random-tick suppression, living swing "
                            + "duration, player arm pose, and Stand "
                            + "material tint policies, time-stop "
                            + "ticks modifiers, and the user's "
                            + "Stand-power action slot with its Stand "
                            + "action ports, and ability condition "
                            + "refusals.");
        }
    }
}
