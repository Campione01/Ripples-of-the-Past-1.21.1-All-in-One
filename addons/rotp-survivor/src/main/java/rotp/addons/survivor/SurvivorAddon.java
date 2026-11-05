package rotp.addons.survivor;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.survivor.entity.SurvivorEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(SurvivorAddon.MOD_ID)
public final class SurvivorAddon {
    public static final String MOD_ID = "survivor";
    public static final String CONFIG_FILE = "survivor-common.toml";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final float STAND_WIDTH = 0.7F;
    public static final float STAND_HEIGHT = 2.1F;
    public static final double EFFECTIVE_RANGE = 100.0D;
    public static final double MAX_RANGE = 100.0D;

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SurvivorEntity>> SURVIVOR_ENTITY =
            ENTITY_TYPES.register(
                    "survivor",
                    key -> EntityType.Builder.<SurvivorEntity>of(SurvivorEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON_VOICELINE =
            SOUND_EVENTS.register("survivor_summon_voiceline", SoundEvent::createVariableRangeEvent);
    public static final OstSoundList OST = new OstSoundList(id("survivor_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType> SURVIVOR =
            STAND_TYPES.register("survivor", SurvivorAddon::createStand);

    public SurvivorAddon(IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "Survivor requires ROTP addon ABI 1 with namespaced ability resources and Stand visual contexts.");
        }
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(SurvivorAddon::registerAttributes);
        modContainer.registerConfig(ModConfig.Type.COMMON, SurvivorConfig.SPEC, CONFIG_FILE);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 1.16 checked only the Stand type, so a Survivor user acts whether the Stand is summoned or not. */
    public static boolean isSurvivorUser(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null && power.getPowerType() == SURVIVOR.get();
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SURVIVOR_ENTITY.get(), StandEntity.createAttributes().build());
    }

    private static EntityStandType createStand(ResourceLocation id) {
        // 1.16: no actions on either hotbar and manual control disabled
        MovesetBuilder moveset = new MovesetBuilder();

        // 1.16 StandStats: tier 6, power 1, speed 1, range 100/100, durability 1, precision 1
        return new EntityStandType(
                new StandStats.Builder()
                        .power(1.0D)
                        .speed(1.0D)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(1.0D)
                        .precision(1.0D)
                        .build(),
                moveset,
                SURVIVOR_ENTITY.get(),
                id)
                .standControlType(StandControlType.AUTOMATIC)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonShout(() -> SUMMON_VOICELINE)
                .summonSound(() -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> OST);
    }
}
