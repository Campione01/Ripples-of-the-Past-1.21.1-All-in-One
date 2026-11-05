package rotp.addons.novemberrain;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.List;
import java.util.function.BiFunction;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.novemberrain.ability.BarrageRainAbility;
import rotp.addons.novemberrain.ability.DestroyItemAbility;
import rotp.addons.novemberrain.ability.HeavyRainAbility;
import rotp.addons.novemberrain.ability.LightRainAbility;
import rotp.addons.novemberrain.ability.PrecisionDropAbility;
import rotp.addons.novemberrain.ability.RainBarrierAbility;
import rotp.addons.novemberrain.ability.RainShieldAbility;
import rotp.addons.novemberrain.effect.CutEyelidEffect;
import rotp.addons.novemberrain.entity.RainDropEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(NovemberRainAddon.MOD_ID)
public final class NovemberRainAddon {
    public static final String MOD_ID = "rotp_november_rain";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String LIGHT_RAIN_NAME = "light_rain";
    public static final String HEAVY_RAIN_NAME = "heavy_rain";
    public static final String RAIN_SHIELD_NAME = "rain_shield";
    public static final String RAIN_BARRIER_NAME = "rain_barrier";
    public static final String DESTROY_ITEM_NAME = "destroy_item";
    public static final String BARRAGE_RAIN_NAME = "barrage_rain";
    public static final String PRECISION_DROP_NAME = "precision_drop";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<AbilityType<?>, AbilityType<LightRainAbility>>
            LIGHT_RAIN = ability(LIGHT_RAIN_NAME, LightRainAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<HeavyRainAbility>>
            HEAVY_RAIN = ability(HEAVY_RAIN_NAME, HeavyRainAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<RainShieldAbility>>
            RAIN_SHIELD = ability(RAIN_SHIELD_NAME, RainShieldAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<RainBarrierAbility>>
            RAIN_BARRIER = ability(RAIN_BARRIER_NAME, RainBarrierAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DestroyItemAbility>>
            DESTROY_ITEM = ability(DESTROY_ITEM_NAME, DestroyItemAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<BarrageRainAbility>>
            BARRAGE_RAIN = ability(BARRAGE_RAIN_NAME, BarrageRainAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<PrecisionDropAbility>>
            PRECISION_DROP = ability(PRECISION_DROP_NAME, PrecisionDropAbility::new);

    public static final DeferredHolder<EntityType<?>, EntityType<NovemberRainStandEntity>>
            NOVEMBER_RAIN_ENTITY = ENTITY_TYPES.register(
                    "november_rain",
                    key -> EntityType.Builder.<NovemberRainStandEntity>of(
                                    NovemberRainStandEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.7F, 2.1F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    // the remake's InitEntities: sized(0.25, 0.25), trackingRange(4), updateInterval(10)
    public static final DeferredHolder<EntityType<?>, EntityType<RainDropEntity>>
            RAIN_DROP = ENTITY_TYPES.register(
                    "rain_drop",
                    key -> EntityType.Builder.<RainDropEntity>of(RainDropEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build(key.toString()));

    public static final DeferredHolder<MobEffect, CutEyelidEffect> CUT_EYELID =
            MOB_EFFECTS.register("cut_eyelid", CutEyelidEffect::new);

    public static final DeferredHolder<SoundEvent, SoundEvent> NOVEMBER_RAIN_SUMMON =
            SOUND_EVENTS.register("november_rain_summon", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<StandType, EntityStandType> NOVEMBER_RAIN =
            STAND_TYPES.register("november_rain", NovemberRainAddon::createStand);

    public NovemberRainAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "RotP November Rain requires ROTP addon ABI 1 with namespaced "
                            + "ability resources and stand visual contexts.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static <A extends Ability> DeferredHolder<AbilityType<?>, AbilityType<A>> ability(
            String name, BiFunction<AbilityType<A>, AbilityId, A> factory) {
        return ABILITY_TYPES.register(name, key -> new AbilityType<>(key, factory));
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(LIGHT_RAIN_NAME, LIGHT_RAIN)
                .addAbility(HEAVY_RAIN_NAME, HEAVY_RAIN)
                .addAbility(RAIN_SHIELD_NAME, RAIN_SHIELD)
                .addAbility(RAIN_BARRIER_NAME, RAIN_BARRIER)
                .addAbility(DESTROY_ITEM_NAME, DESTROY_ITEM)
                .addAbility(BARRAGE_RAIN_NAME, BARRAGE_RAIN)
                .addAbility(PRECISION_DROP_NAME, PRECISION_DROP);
        // 1.0.4 remake InitStands: LMB hotbar (light_rain, barrage_rain), RMB hotbar (rain_shield, destroy_item),
        // heavy_rain and rain_barrier the Shift variations of light_rain and rain_shield, precision_drop on no
        // hotbar. Core standard: each hotbar's first action on its mouse button, the rest and the Shift variations
        // in their own slots of the skill bar (C selects, X uses), no other key bound by default.
        moveset.makeControlScheme("hotbar")
                .bind(LIGHT_RAIN_NAME, InputMethod.HOLD, InputKey.LMB)
                .bind(RAIN_SHIELD_NAME, InputMethod.HOLD, InputKey.RMB)
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                .addToHotbar(BARRAGE_RAIN_NAME, 0, InputMethod.CLICK)
                .addToHotbar(HEAVY_RAIN_NAME, 0, InputMethod.HOLD)
                .addToHotbar(DESTROY_ITEM_NAME, 0, InputMethod.HOLD)
                .addToHotbar(RAIN_BARRIER_NAME, 0, InputMethod.HOLD)
                .addToHotbar(PRECISION_DROP_NAME, 0, InputMethod.CLICK)
                .finalizeControlScheme();
        moveset.makeControlScheme("keybinds")
                .bind(LIGHT_RAIN_NAME, InputMethod.HOLD, InputKey.LMB)
                .bind(RAIN_SHIELD_NAME, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme();
        // every remake action is unlocked from the start; the Resolve levels gate them in the abilities
        for (String ability : List.of(LIGHT_RAIN_NAME, HEAVY_RAIN_NAME, RAIN_SHIELD_NAME, RAIN_BARRIER_NAME,
                DESTROY_ITEM_NAME, BARRAGE_RAIN_NAME, PRECISION_DROP_NAME)) {
            moveset.addSkill(StandUnlockableSkill.startingAbility(ability));
        }

        return new EntityStandType(
                new StandStats.Builder()
                        .power(1.7)
                        .speed(9.0)
                        .range(9.0, 9.0)
                        .durability(14.0)
                        .precision(13.6)
                        .build(),
                moveset,
                NOVEMBER_RAIN_ENTITY.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(0.7F, 2.1F)
                .summonSound(() -> NOVEMBER_RAIN_SUMMON);
    }
}
