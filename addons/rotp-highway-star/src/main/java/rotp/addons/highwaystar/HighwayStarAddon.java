package rotp.addons.highwaystar;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.power.AbilityRefusals;
import rotp.core.api.stand.ManualControlBlocker;
import rotp.core.api.stand.ManualControlBlockers;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.highwaystar.ability.HighwayStarActionBlocker;
import rotp.addons.highwaystar.ability.HighwayStarDrainAbility;
import rotp.addons.highwaystar.ability.HighwayStarSeizeAbility;
import rotp.addons.highwaystar.ability.HighwayStarSmellAbility;
import rotp.addons.highwaystar.ability.HighwayStarUtilityAbility;
import rotp.addons.highwaystar.ability.HighwayStarUtilityAbility.Mode;
import rotp.addons.highwaystar.entity.HighwayStarEntity;
import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import rotp.addons.highwaystar.network.HighwayStarPayloads;
import rotp.addons.highwaystar.smell.SmellTargets;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(HighwayStarAddon.MOD_ID)
public final class HighwayStarAddon {
    public static final String MOD_ID = "highwaystar";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String PUNCH = "highway_star_punch";
    public static final String BARRAGE = "highway_star_barrage";
    public static final String HEAVY = "highway_star_heavy_punch";
    public static final String FINISHER = "highway_star_finisher_punch";
    public static final String CHARGED_HEAVY = "heavy_charged";
    public static final String BLOCK = "highway_star_block";
    public static final String SMELL = "highway_star_smell";
    public static final String TRACKING = "highway_star_tracking";
    public static final String TRACK_CANCEL = "highway_star_track_cancel";
    public static final String SEIZE = "highway_star_seize";
    public static final String ARM_RETURN = "highway_star_arm_return";
    public static final String DRAIN = "highway_star_drain";
    public static final String FORM_SWITCH = "highway_star_form_switch";

    public static final float ENTITY_WIDTH = 0.7F;
    public static final float ENTITY_HEIGHT = 2.1F;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final DeferredHolder<AbilityType<?>, AbilityType<HighwayStarSmellAbility>> SMELL_ABILITY =
            ABILITY_TYPES.register(SMELL, key -> new AbilityType<>(key, HighwayStarSmellAbility::new));
    public static final DeferredHolder<AbilityType<?>, AbilityType<HighwayStarSeizeAbility>> SEIZE_ABILITY =
            ABILITY_TYPES.register(SEIZE, key -> new AbilityType<>(key, HighwayStarSeizeAbility::new));
    public static final DeferredHolder<AbilityType<?>, AbilityType<HighwayStarDrainAbility>> DRAIN_ABILITY =
            ABILITY_TYPES.register(DRAIN, key -> new AbilityType<>(key, HighwayStarDrainAbility::new));
    public static final DeferredHolder<AbilityType<?>, AbilityType<HighwayStarUtilityAbility>> TRACKING_ABILITY =
            utility(TRACKING, Mode.TRACKING);
    public static final DeferredHolder<AbilityType<?>, AbilityType<HighwayStarUtilityAbility>> TRACK_CANCEL_ABILITY =
            utility(TRACK_CANCEL, Mode.TRACK_CANCEL);
    public static final DeferredHolder<AbilityType<?>, AbilityType<HighwayStarUtilityAbility>> ARM_RETURN_ABILITY =
            utility(ARM_RETURN, Mode.ARM_RETURN);
    public static final DeferredHolder<AbilityType<?>, AbilityType<HighwayStarUtilityAbility>> FORM_SWITCH_ABILITY =
            utility(FORM_SWITCH, Mode.FORM_SWITCH);

    public static final DeferredHolder<EntityType<?>, EntityType<HighwayStarEntity>> HIGHWAY_STAR_ENTITY =
            ENTITY_TYPES.register("highway_star", key -> EntityType.Builder
                    .<HighwayStarEntity>of(HighwayStarEntity::new, MobCategory.MISC)
                    .noSave()
                    .noSummon()
                    .sized(ENTITY_WIDTH, ENTITY_HEIGHT)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<HighwayStarSoleEntity>> SOLE_ENTITY =
            ENTITY_TYPES.register("highway_star_sole", key -> EntityType.Builder
                    .<HighwayStarSoleEntity>of(HighwayStarSoleEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(64)
                    .updateInterval(3)
                    .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON_VOICELINE = sound("summon_voiceline");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON = sound("summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORM_SWITCH_SOUND = sound("summon2");
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRAGE_VOICE = sound("barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRAGE_VOICE_2 = sound("barrage2");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAIN_VOICE = sound("drain");
    public static final DeferredHolder<SoundEvent, SoundEvent> SMELL_VOICE = sound("smell");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRACKING_VOICE = sound("tracking");

    public static final Supplier<AttachmentType<SmellTargets>> SMELL_TARGETS = ATTACHMENT_TYPES.register(
            "smell_targets", () -> AttachmentType.builder(SmellTargets::new).serialize(SmellTargets.CODEC).build());

    public static final DeferredHolder<StandType, EntityStandType> HIGHWAY_STAR =
            STAND_TYPES.register("highway_star", HighwayStarAddon::createStand);

    public HighwayStarAddon(IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "Highway Star requires ROTP addon ABI 1 with namespaced ability resources and Stand visual contexts.");
        }
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(HighwayStarEvents::registerAttributes);
        modEventBus.addListener(HighwayStarPayloads::register);
        modEventBus.addListener(HighwayStarAddon::commonSetup);
        modContainer.registerConfig(ModConfig.Type.COMMON, HighwayStarConfig.SPEC, "highwaystar-common.toml");
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            AbilityRefusals.register(id("split_and_arm"), HighwayStarActionBlocker::refuse);
            // 1.16 HighwayStarEntity.canBeManuallyControlled answered the split form with this message
            ManualControlBlockers.register(id("split_form"), ManualControlBlocker.withMessage(
                    HighwayStarActionBlocker::blocksManualControl,
                    Component.translatable("action.highwaystar.condition.cannot_remote_in_split")));
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<AbilityType<?>, AbilityType<HighwayStarUtilityAbility>> utility(String name, Mode mode) {
        return ABILITY_TYPES.register(name,
                key -> new AbilityType<>(key, (type, abilityId) -> new HighwayStarUtilityAbility(type, abilityId, mode)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(name, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(PUNCH, ModStandAbilities.PUNCH)
                // 1.16 standSound(PERFORM, barrage): the two-argument form played with the arms only too
                .addAbility(BARRAGE, ModStandAbilities.BARRAGE,
                        barrage -> barrage.barrageCrySound(BARRAGE_VOICE).barrageCryInArmsOnly())
                // 1.16 standSound(WINDUP, barrage2)
                .addAbility(HEAVY, ModStandAbilities.HEAVY_PUNCH,
                        punch -> punch.heavyPunchCrySound(BARRAGE_VOICE_2))
                .addAbility(FINISHER, ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchCrySound(BARRAGE_VOICE_2);
                            punch.initIsFinisher(HEAVY);
                        })
                .addAbility(CHARGED_HEAVY, ModStandAbilities.HEAVY_CHARGED)
                .addAbility(BLOCK, ModStandAbilities.GUARD)
                .addAbility(SEIZE, SEIZE_ABILITY,
                        seize -> seize.heavyPunchCrySound(BARRAGE_VOICE_2))
                .addAbility(ARM_RETURN, ARM_RETURN_ABILITY)
                .addAbility(TRACKING, TRACKING_ABILITY)
                .addAbility(TRACK_CANCEL, TRACK_CANCEL_ABILITY)
                .addAbility(DRAIN, DRAIN_ABILITY)
                .addAbility(SMELL, SMELL_ABILITY)
                .addAbility(FORM_SWITCH, FORM_SWITCH_ABILITY)
                .makeControlScheme("hotbar")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY, InputMethod.CLICK, InputKey.RMB)
                    .bind(CHARGED_HEAVY, InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    // 1.16 left-click hotbar: Seize, Tracking, Nutrient Drain; right-click hotbar: Smell, Form Switch
                    .addToHotbar(SEIZE, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(ARM_RETURN, SEIZE, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar(TRACKING, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(TRACK_CANCEL, TRACKING, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar(DRAIN, 0, InputMethod.HOLD)
                    .addToHotbar(SMELL, 0, InputMethod.HOLD)
                    .addToHotbar(FORM_SWITCH, 0, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY, InputMethod.CLICK, InputKey.RMB)
                    .bind(CHARGED_HEAVY, InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(BARRAGE))
                .addSkill(StandUnlockableSkill.startingAbility(HEAVY))
                .addSkill(StandUnlockableSkill.startingAbility(FINISHER))
                .addSkill(StandUnlockableSkill.startingAbility(CHARGED_HEAVY))
                .addSkill(StandUnlockableSkill.startingAbility(BLOCK))
                .addSkill(StandUnlockableSkill.startingAbility(SEIZE))
                .addSkill(StandUnlockableSkill.startingAbility(ARM_RETURN))
                .addSkill(StandUnlockableSkill.startingAbility(TRACKING))
                .addSkill(StandUnlockableSkill.startingAbility(TRACK_CANCEL))
                .addSkill(StandUnlockableSkill.startingAbility(DRAIN))
                .addSkill(StandUnlockableSkill.startingAbility(SMELL))
                .addSkill(StandUnlockableSkill.startingAbility(FORM_SWITCH))
                .addSkill(StandUnlockableSkill.startingAbility("manual_control"));

        // 1.16 tier(6) has no 1.21 counterpart; the other stats are the donor's
        return new EntityStandType(
                new StandStats.Builder()
                        .power(10.0)
                        .speed(12.0)
                        .range(1.0E8, 1.0E9)
                        .durability(18.0)
                        .precision(2.0)
                        .build(),
                moveset,
                HIGHWAY_STAR_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(ENTITY_WIDTH, ENTITY_HEIGHT)
                .summonSound(() -> SUMMON)
                .summonShout(() -> SUMMON_VOICELINE);
    }
}
