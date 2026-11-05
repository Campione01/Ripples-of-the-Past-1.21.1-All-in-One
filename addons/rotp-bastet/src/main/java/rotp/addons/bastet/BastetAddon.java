package rotp.addons.bastet;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.bastet.ability.BastetOutletAbility;
import rotp.addons.bastet.ability.BastetOutletAbility.Mode;
import rotp.addons.bastet.block.BastetOutletBlock;
import rotp.addons.bastet.block.BastetOutletBlockEntity;
import rotp.addons.bastet.entity.BastetEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BastetAddon.MOD_ID)
public final class BastetAddon {
    public static final String MOD_ID = "bastet";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final float STAND_WIDTH = 0.7F;
    public static final float STAND_HEIGHT = 2.1F;
    public static final double EFFECTIVE_RANGE = 10.0D;
    public static final double MAX_RANGE = 20.0D;

    public static final String PLACE_OUTLET = "bastet_place_outlet";
    public static final String REMOVE_OUTLET = "bastet_remove_outlet";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<AbilityType<?>, AbilityType<BastetOutletAbility>>
            PLACE_OUTLET_ABILITY = outletAbility(PLACE_OUTLET, Mode.PLACE);
    public static final DeferredHolder<AbilityType<?>, AbilityType<BastetOutletAbility>>
            REMOVE_OUTLET_ABILITY = outletAbility(REMOVE_OUTLET, Mode.REMOVE);

    public static final DeferredHolder<EntityType<?>, EntityType<BastetEntity>> BASTET_ENTITY =
            ENTITY_TYPES.register(
                    "bastet",
                    key -> EntityType.Builder.<BastetEntity>of(BastetEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<Block, BastetOutletBlock> OUTLET_BLOCK =
            BLOCKS.register("bastet_outlet", () -> new BastetOutletBlock(BastetOutletBlock.outletProperties()));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BastetOutletBlockEntity>>
            OUTLET_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "bastet_outlet",
                    () -> BlockEntityType.Builder.of(BastetOutletBlockEntity::new, OUTLET_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON_VOICELINE =
            SOUND_EVENTS.register("bastet_summon_voiceline", SoundEvent::createVariableRangeEvent);
    public static final OstSoundList OST = new OstSoundList(id("bastet_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType> BASTET =
            STAND_TYPES.register("bastet", BastetAddon::createStand);

    public BastetAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "Bastet requires ROTP addon ABI 1 with namespaced ability resources and Stand visual contexts.");
        }
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(BastetAddon::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 1.16: any Stand whose registry name contains "bastet", summoned or not. */
    public static boolean isBastetUser(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        if (power == null || !power.hasPower()) {
            return false;
        }
        StandType type = power.getPowerType();
        return type != null && type.getId() != null && type.getId().toString().contains("bastet");
    }

    private static DeferredHolder<AbilityType<?>, AbilityType<BastetOutletAbility>> outletAbility(
            String name, Mode mode) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key, (type, abilityId) -> new BastetOutletAbility(type, abilityId, mode)));
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BASTET_ENTITY.get(), StandEntity.createAttributes().build());
    }

    private static EntityStandType createStand(ResourceLocation id) {
        // 1.16: nothing on the left hotbar; the right one holds Place Outlet, Shift: Remove Outlet
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(PLACE_OUTLET, PLACE_OUTLET_ABILITY)
                .addAbility(REMOVE_OUTLET, REMOVE_OUTLET_ABILITY)
                .makeControlScheme("hotbar")
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(PLACE_OUTLET, 0, InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            REMOVE_OUTLET, PLACE_OUTLET, InputKey.Modifier.SHIFT, InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(PLACE_OUTLET))
                .addSkill(StandUnlockableSkill.startingAbility(REMOVE_OUTLET));

        // 1.16 StandStats: tier 4, power 0, speed 0, range 10/20, durability 15, precision 0
        return new EntityStandType(
                new StandStats.Builder()
                        .power(0.0D)
                        .speed(0.0D)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(15.0D)
                        .precision(0.0D)
                        .build(),
                moveset,
                BASTET_ENTITY.get(),
                id)
                // 1.16 disableManualControl
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
