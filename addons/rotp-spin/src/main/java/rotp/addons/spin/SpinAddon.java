package rotp.addons.spin;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.BiFunction;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import rotp.addons.spin.ability.SpinBallChargeAbility;
import rotp.addons.spin.ability.SpinBallSteerAbility;
import rotp.addons.spin.ability.SpinBallStrikeAbility;
import rotp.addons.spin.ability.SpinBallThrowAbility;
import rotp.addons.spin.ability.SpinBlockHandThrowAbility;
import rotp.addons.spin.ability.SpinBlockThrowAbility;
import rotp.addons.spin.ability.SpinItemThrowAbility;
import rotp.addons.spin.ability.SpinLessonAbilities;
import rotp.addons.spin.breaker.BallBreakerAddon;
import rotp.addons.spin.client.SpinClient;
import rotp.addons.spin.compat.tusk.SpinTuskCompat;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.effect.DesiccationEffect;
import rotp.addons.spin.effect.HemispatialNeglectEffect;
import rotp.addons.spin.effect.SenescenceEffect;
import rotp.addons.spin.entity.GyroTeacherEntity;
import rotp.addons.spin.entity.SpunBlockEntity;
import rotp.addons.spin.entity.SpunItemEntity;
import rotp.addons.spin.entity.SteelBallEntity;
import rotp.addons.spin.equipment.GyrosCloakItem;
import rotp.addons.spin.equipment.GyrosHolsterItem;
import rotp.addons.spin.equipment.SpinEquipment;
import rotp.addons.spin.item.SteelBallItem;
import rotp.addons.spin.item.WreckingBallItem;
import rotp.addons.spin.power.SpinEnergy;
import rotp.addons.spin.power.SpinPowerType;
import rotp.addons.spin.world.GyroSpawns;
import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.unlockableskill.UnlockableSkill;

@Mod(SpinAddon.MOD_ID)
public final class SpinAddon {
    public static final String MOD_ID = "rotp_spin";
    public static final String BALL_THROW_NAME = "spin_ball_throw";
    public static final String BALL_STRIKE_NAME = "spin_ball_strike";
    public static final String BALL_STEER_NAME = "spin_ball_steer";
    public static final String BALL_CHARGE_NAME = "spin_ball_charge";
    public static final String HIJACK_NAME = "spin_muscle_hijack";
    public static final String MEDICINE_NAME = "spin_healing";
    public static final String FRAME_NAME = "spin_golden_frame";
    public static final String BRACE_NAME = "spin_body_brace";
    public static final String ITEM_THROW_NAME = "spin_item_throw";
    public static final String BLOCK_THROW_NAME = "spin_block_throw";
    public static final String HAND_BLOCK_THROW_NAME = "spin_block_hand_throw";

    public static final DeferredRegister<AbilityType<?>> ABILITIES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<PlayerPowerType<?>> POWERS =
            DeferredRegister.create(JojoRegistries.PLAYER_POWER_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, MOD_ID);
    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<GyroSpawns.Natural>> GYRO_SPAWNS =
            BIOME_MODIFIERS.register("gyro_spawns", () -> GyroSpawns.Natural.CODEC);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpinEnergy>> ENERGY =
            ATTACHMENTS.register("spin_power", () -> AttachmentType.serializable(SpinEnergy::new).copyOnDeath().build());
    public static final DeferredHolder<MobEffect, SenescenceEffect> SENESCENCE =
            EFFECTS.register("senescence", SenescenceEffect::new);
    public static final DeferredHolder<MobEffect, HemispatialNeglectEffect> NEGLECT =
            EFFECTS.register("hemispatial_neglect", HemispatialNeglectEffect::new);
    public static final DeferredHolder<MobEffect, DesiccationEffect> DESICCATION =
            EFFECTS.register("desiccation", DesiccationEffect::new);

    public static final DeferredHolder<AbilityType<?>, AbilityType<SpinBallThrowAbility>> BALL_THROW =
            ability(BALL_THROW_NAME, SpinBallThrowAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpinBallStrikeAbility>> BALL_STRIKE =
            ability(BALL_STRIKE_NAME, SpinBallStrikeAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpinBallSteerAbility>> BALL_STEER =
            ability(BALL_STEER_NAME, SpinBallSteerAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpinBallChargeAbility>> BALL_CHARGE =
            ability(BALL_CHARGE_NAME, SpinBallChargeAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpinLessonAbilities.Hijack>> HIJACK =
            ability(HIJACK_NAME, SpinLessonAbilities.Hijack::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpinLessonAbilities.Medicine>> MEDICINE =
            ability(MEDICINE_NAME, SpinLessonAbilities.Medicine::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpinLessonAbilities.GoldenFrame>> FRAME =
            ability(FRAME_NAME, SpinLessonAbilities.GoldenFrame::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpinLessonAbilities.Brace>> BRACE =
            ability(BRACE_NAME, SpinLessonAbilities.Brace::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpinItemThrowAbility>> ITEM_THROW =
            ability(ITEM_THROW_NAME, SpinItemThrowAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpinBlockThrowAbility>> BLOCK_THROW =
            ability(BLOCK_THROW_NAME, SpinBlockThrowAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpinBlockHandThrowAbility>> HAND_BLOCK_THROW =
            ability(HAND_BLOCK_THROW_NAME, SpinBlockHandThrowAbility::new);
    public static final DeferredHolder<PlayerPowerType<?>, SpinPowerType> SPIN =
            POWERS.register("spin", key -> new SpinPowerType(key, moveset()));

    public static final DeferredHolder<EntityType<?>, EntityType<SteelBallEntity>> STEEL_BALL_ENTITY =
            ENTITIES.register("steel_ball", key -> EntityType.Builder.<SteelBallEntity>of(SteelBallEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(20).build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<GyroTeacherEntity>> GYRO_TEACHER =
            ENTITIES.register("gyro_teacher", key -> EntityType.Builder.<GyroTeacherEntity>of(GyroTeacherEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).updateInterval(3).build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<SpunItemEntity>> SPUN_ITEM =
            ENTITIES.register("spun_item", key -> EntityType.Builder.<SpunItemEntity>of(SpunItemEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(20).build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<SpunBlockEntity>> SPUN_BLOCK =
            ENTITIES.register("spun_block", key -> EntityType.Builder.<SpunBlockEntity>of(SpunBlockEntity::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F).clientTrackingRange(4).updateInterval(20).build(key.toString()));

    public static final DeferredHolder<Item, SteelBallItem> STEEL_BALL =
            ITEMS.register("steel_ball", () -> new SteelBallItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, WreckingBallItem> WRECKING_BALL =
            ITEMS.register("wrecking_ball", () -> new WreckingBallItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> CALIBRATION_BUCKLE =
            ITEMS.register("calibration_buckle", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, GyrosHolsterItem> GYROS_HOLSTER =
            ITEMS.register("gyros_holster", () -> new GyrosHolsterItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, GyrosCloakItem> GYROS_CLOAK =
            ITEMS.register("gyros_cloak", () -> new GyrosCloakItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, SpawnEggItem> GYRO_TEACHER_EGG =
            ITEMS.register("gyro_teacher_egg", () -> new SpawnEggItem(GYRO_TEACHER.get(), 4863526, 14205562, new Item.Properties()));

    public SpinAddon(IEventBus bus, ModContainer container) {
        if (!RotpAddonApi.supportsAbi(1)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)) {
            throw new IllegalStateException("RotP Spin requires ROTP ABI 1 with namespaced ability resources");
        }
        ABILITIES.register(bus);
        POWERS.register(bus);
        ATTACHMENTS.register(bus);
        ENTITIES.register(bus);
        ITEMS.register(bus);
        EFFECTS.register(bus);
        BIOME_MODIFIERS.register(bus);
        BallBreakerAddon.register(bus);
        SpinEquipment.register(bus);
        SpinTuskCompat.register(bus);
        SteelBallEntity.setImpactHooks(BallBreakerAddon.impactHooks());
        bus.addListener(SpinModEvents::registerAttributes);
        bus.addListener(SpinModEvents::creativeTab);
        bus.addListener(SpinModEvents::spawnPlacement);
        container.registerConfig(ModConfig.Type.COMMON, SpinConfig.SPEC);
        if (FMLEnvironment.dist.isClient()) SpinClient.register(bus, container);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static MovesetBuilder moveset() {
        MovesetBuilder moves = new MovesetBuilder()
                .addAbility(BALL_THROW_NAME, BALL_THROW)
                .addAbility(BALL_STRIKE_NAME, BALL_STRIKE)
                .addAbility(HIJACK_NAME, HIJACK)
                .addAbility(BALL_STEER_NAME, BALL_STEER)
                .addAbility(MEDICINE_NAME, MEDICINE)
                .addAbility(FRAME_NAME, FRAME)
                .addAbility(BRACE_NAME, BRACE)
                .addAbility(BALL_CHARGE_NAME, BALL_CHARGE)
                .addAbility(ITEM_THROW_NAME, ITEM_THROW)
                .addAbility(BLOCK_THROW_NAME, BLOCK_THROW)
                .addAbility(HAND_BLOCK_THROW_NAME, HAND_BLOCK_THROW);
        for (String action : new String[]{BALL_THROW_NAME, BALL_STRIKE_NAME, HIJACK_NAME, BALL_STEER_NAME,
                MEDICINE_NAME, FRAME_NAME, BRACE_NAME, BALL_CHARGE_NAME,
                ITEM_THROW_NAME, BLOCK_THROW_NAME, HAND_BLOCK_THROW_NAME}) {
            moves.addSkill(new SpinSkill(action).setIsStartingSkill().withAbility(action).setHidden());
        }
        return moves.makeControlScheme("default")
                .makeMovesetGroup("moveset_default_group", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
                .bind(BALL_THROW_NAME, InputMethod.CLICK, InputKey.LMB)
                .bind(BALL_STEER_NAME, InputMethod.HOLD, InputKey.RMB)
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                .addToHotbar(BALL_THROW_NAME, 0, InputMethod.CLICK)
                .addToHotbar(BALL_STRIKE_NAME, 0, InputMethod.CLICK)
                .addToHotbar(HIJACK_NAME, 0, InputMethod.CLICK)
                .addToHotbar(MEDICINE_NAME, 0, InputMethod.HOLD)
                .addToHotbar(FRAME_NAME, 0, InputMethod.HOLD)
                .addToHotbar(BRACE_NAME, 0, InputMethod.HOLD)
                .addToHotbar(BALL_CHARGE_NAME, 0, InputMethod.HOLD)
                .addToHotbar(ITEM_THROW_NAME, 0, InputMethod.CLICK)
                .addToHotbar(BLOCK_THROW_NAME, 0, InputMethod.CLICK)
                .addHotbarSlotVariation(HAND_BLOCK_THROW_NAME, BLOCK_THROW_NAME, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                .finalizeControlScheme();
    }

    private static <T extends Ability> DeferredHolder<AbilityType<?>, AbilityType<T>> ability(
            String name, BiFunction<AbilityType<T>, AbilityId, T> factory) {
        return ABILITIES.register(name, key -> new AbilityType<>(key, factory));
    }

    private static final class SpinSkill extends UnlockableSkill {
        private SpinSkill(String action) {
            super(MOD_ID + "_" + action);
        }
    }
}
