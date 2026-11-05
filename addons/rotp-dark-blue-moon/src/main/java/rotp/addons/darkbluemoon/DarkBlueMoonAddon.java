package rotp.addons.darkbluemoon;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.EnumMap;
import java.util.List;
import java.util.function.BiFunction;

import rotp.addons.darkbluemoon.ability.DbmBarnacleAbility;
import rotp.addons.darkbluemoon.ability.DbmBarnacleCancelAbility;
import rotp.addons.darkbluemoon.ability.DbmBarnaclePullAbility;
import rotp.addons.darkbluemoon.ability.DbmBarnacleSelfAbility;
import rotp.addons.darkbluemoon.ability.DbmBarnacleSelfCancelAbility;
import rotp.addons.darkbluemoon.ability.DbmBarnacleTrapAbility;
import rotp.addons.darkbluemoon.ability.DbmBarnacleTrapCancelAbility;
import rotp.addons.darkbluemoon.ability.DbmBarrageAbility;
import rotp.addons.darkbluemoon.ability.DbmChopAbility;
import rotp.addons.darkbluemoon.ability.DbmMiniTornadoAbility;
import rotp.addons.darkbluemoon.ability.DbmScaleShotAbility;
import rotp.addons.darkbluemoon.ability.DbmWhirlpoolAbility;
import rotp.addons.darkbluemoon.ability.DbmWhirlpoolCancelAbility;
import rotp.addons.darkbluemoon.effect.BarnacleBindEffect;
import rotp.addons.darkbluemoon.effect.DbmBoostEffect;
import rotp.addons.darkbluemoon.effect.VulnerabilityEffect;
import rotp.addons.darkbluemoon.entity.BarnacleEntity;
import rotp.addons.darkbluemoon.entity.BarnaclePullEntity;
import rotp.addons.darkbluemoon.entity.DarkBlueMoonEntity;
import rotp.addons.darkbluemoon.entity.MiniTornadoEntity;
import rotp.addons.darkbluemoon.entity.ScaleShotEntity;
import rotp.addons.darkbluemoon.entity.WhirlpoolEntity;
import rotp.addons.darkbluemoon.item.BarnacleFoodItem;
import rotp.addons.darkbluemoon.item.BarnacleNecklaceItem;
import rotp.addons.darkbluemoon.item.BarnacleShellItem;
import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
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
import rotp.core.util.sound.OstSoundList;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DarkBlueMoonAddon.MOD_ID)
public final class DarkBlueMoonAddon {
    public static final String MOD_ID = "darkbluemoon";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String PUNCH = "dark_blue_moon_punch";
    public static final String BARRAGE = "dark_blue_moon_barrage";
    public static final String HEAVY_PUNCH = "dark_blue_moon_heavy_punch";
    public static final String FINISHER_PUNCH = "dark_blue_moon_finisher_punch";
    public static final String CHARGED_HEAVY = "heavy_charged";
    public static final String BLOCK = "dark_blue_moon_block";
    public static final String CHOP = "dark_blue_moon_chop";
    public static final String SCALE_SHOT = "dark_blue_moon_scale_shot";
    public static final String SCALE_SHOT_FOCUSED = "dark_blue_moon_scale_shot_focused";
    public static final String BARNACLE = "dark_blue_moon_barnacle";
    public static final String BARNACLE_CANCEL = "dark_blue_moon_barnacle_cancel";
    public static final String BARNACLE_SELF = "dark_blue_moon_barnacle_self";
    public static final String BARNACLE_SELF_CANCEL = "dark_blue_moon_barnacle_self_cancel";
    public static final String BARNACLE_PULL = "dark_blue_moon_barnacle_pull";
    public static final String BARNACLE_TRAP = "dark_blue_moon_barnacle_trap";
    public static final String BARNACLE_TRAP_CANCEL = "dark_blue_moon_barnacle_trap_cancel";
    public static final String WHIRLPOOL_NAME = "dark_blue_moon_whirlpool";
    public static final String WHIRLPOOL_CANCEL = "dark_blue_moon_whirlpool_cancel";
    public static final String MANUAL_CONTROL = "manual_control";

    /** 1.16 BarnacleBindEffect put these on a 20-tick cooldown on every bind tick. */
    public static final List<String> BARNACLE_BIND_COOLDOWN_ABILITIES = List.of(PUNCH, BARRAGE, CHOP, SCALE_SHOT, BARNACLE);

    /** 1.16 InitStands: range(5.0, 15.0), StandEntityType(DarkBlueMoonEntity::new, 0.7F, 2.1F). */
    public static final double EFFECTIVE_RANGE = 5.0;
    public static final double MAX_RANGE = 15.0;
    public static final float ENTITY_WIDTH = 0.7F;
    public static final float ENTITY_HEIGHT = 2.1F;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    // abilities
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmBarrageAbility>> BARRAGE_TYPE =
            ability(BARRAGE, DbmBarrageAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmMiniTornadoAbility>> FINISHER_TYPE =
            ability(FINISHER_PUNCH, DbmMiniTornadoAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmChopAbility>> CHOP_TYPE =
            ability(CHOP, DbmChopAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmScaleShotAbility>> SCALE_SHOT_TYPE =
            ability(SCALE_SHOT, (type, id) -> new DbmScaleShotAbility(type, id, false));
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmScaleShotAbility>> SCALE_SHOT_FOCUSED_TYPE =
            ability(SCALE_SHOT_FOCUSED, (type, id) -> new DbmScaleShotAbility(type, id, true));
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmBarnacleAbility>> BARNACLE_TYPE =
            ability(BARNACLE, DbmBarnacleAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmBarnacleCancelAbility>> BARNACLE_CANCEL_TYPE =
            ability(BARNACLE_CANCEL, DbmBarnacleCancelAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmBarnacleSelfAbility>> BARNACLE_SELF_TYPE =
            ability(BARNACLE_SELF, DbmBarnacleSelfAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmBarnacleSelfCancelAbility>> BARNACLE_SELF_CANCEL_TYPE =
            ability(BARNACLE_SELF_CANCEL, DbmBarnacleSelfCancelAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmBarnaclePullAbility>> BARNACLE_PULL_TYPE =
            ability(BARNACLE_PULL, DbmBarnaclePullAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmBarnacleTrapAbility>> BARNACLE_TRAP_TYPE =
            ability(BARNACLE_TRAP, DbmBarnacleTrapAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmBarnacleTrapCancelAbility>> BARNACLE_TRAP_CANCEL_TYPE =
            ability(BARNACLE_TRAP_CANCEL, DbmBarnacleTrapCancelAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmWhirlpoolAbility>> WHIRLPOOL_TYPE =
            ability(WHIRLPOOL_NAME, DbmWhirlpoolAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<DbmWhirlpoolCancelAbility>> WHIRLPOOL_CANCEL_TYPE =
            ability(WHIRLPOOL_CANCEL, DbmWhirlpoolCancelAbility::new);

    // entities
    public static final DeferredHolder<EntityType<?>, EntityType<DarkBlueMoonEntity>> DARK_BLUE_MOON_ENTITY =
            ENTITY_TYPES.register("dark_blue_moon", key -> EntityType.Builder
                    .<DarkBlueMoonEntity>of(DarkBlueMoonEntity::new, MobCategory.MISC)
                    .noSave()
                    .noSummon()
                    .sized(ENTITY_WIDTH, ENTITY_HEIGHT)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<ScaleShotEntity>> SCALE_SHOT_ENTITY =
            ENTITY_TYPES.register("scale_shot", key -> EntityType.Builder
                    .<ScaleShotEntity>of(ScaleShotEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<BarnacleEntity>> BARNACLE_ENTITY =
            ENTITY_TYPES.register("barnacle", key -> EntityType.Builder
                    .<BarnacleEntity>of(BarnacleEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<BarnaclePullEntity>> BARNACLE_PULL_ENTITY =
            ENTITY_TYPES.register("barnacle_pull", key -> EntityType.Builder
                    .<BarnaclePullEntity>of(BarnaclePullEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<WhirlpoolEntity>> WHIRLPOOL =
            ENTITY_TYPES.register("whirlpool", key -> EntityType.Builder
                    .<WhirlpoolEntity>of(WhirlpoolEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<MiniTornadoEntity>> MINI_WATER_TORNADO =
            ENTITY_TYPES.register("mini_water_tornado", key -> EntityType.Builder
                    .<MiniTornadoEntity>of(MiniTornadoEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(key.toString()));

    // sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON_VOICELINE = sound("dark_blue_moon_summon_voiceline");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOP_HIT_WATER = sound("dark_blue_moon_chop_hit_water");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOP_HIT_LAND = sound("dark_blue_moon_chop_hit_land");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOP_USE_WATER_1 = sound("dark_blue_moon_chop_use_water_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOP_USE_WATER_2 = sound("dark_blue_moon_chop_use_water_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOP_USE_WATER_3 = sound("dark_blue_moon_chop_use_water_3");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOP_USE_LAND_1 = sound("dark_blue_moon_chop_use_land_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOP_USE_LAND_2 = sound("dark_blue_moon_chop_use_land_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOP_USE_LAND_3 = sound("dark_blue_moon_chop_use_land_3");
    public static final OstSoundList DARK_BLUE_MOON_OST = new OstSoundList(id("dark_blue_moon_ost"), SOUND_EVENTS);

    // effects
    public static final DeferredHolder<MobEffect, DbmBoostEffect> DARK_BLUE_MOON_WATER_USER = MOB_EFFECTS.register(
            "dark_blue_moon_water_user", () -> new DbmBoostEffect("dbm_water_user", 3.0, 0.01, 0.5));
    public static final DeferredHolder<MobEffect, DbmBoostEffect> DARK_BLUE_MOON_WATER_STAND = MOB_EFFECTS.register(
            "dark_blue_moon_water_stand", () -> new DbmBoostEffect("dbm_water_stand", 5.0, 0.01, 0.5));
    public static final DeferredHolder<MobEffect, DbmBoostEffect> DARK_BLUE_MOON_RAIN_USER = MOB_EFFECTS.register(
            "dark_blue_moon_rain_user", () -> new DbmBoostEffect("dbm_rain", 1.5, 0.005, 0.25));
    public static final DeferredHolder<MobEffect, BarnacleBindEffect> BARNACLE_BIND =
            MOB_EFFECTS.register("barnacle_bind", BarnacleBindEffect::new);
    public static final DeferredHolder<MobEffect, VulnerabilityEffect> VULNERABILITY =
            MOB_EFFECTS.register("vulnerability", VulnerabilityEffect::new);

    // 1.16 BarnacleNecklaceItem: ArmorMaterial.LEATHER on the chest
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> NECKLACE_MATERIAL = ARMOR_MATERIALS.register(
            "barnacle_necklace", () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), defense -> {
                        defense.put(ArmorItem.Type.BOOTS, 1);
                        defense.put(ArmorItem.Type.LEGGINGS, 2);
                        defense.put(ArmorItem.Type.CHESTPLATE, 3);
                        defense.put(ArmorItem.Type.HELMET, 1);
                        defense.put(ArmorItem.Type.BODY, 3);
                    }),
                    15,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(Items.LEATHER),
                    List.of(new ArmorMaterial.Layer(id("barnacle_necklace"))),
                    0.0F,
                    0.0F));

    // items (1.16 InitItems, all in the RotP main tab)
    public static final DeferredHolder<Item, BarnacleFoodItem> BARNACLE_ITEM = ITEMS.register("barnacle",
            key -> new BarnacleFoodItem(new Item.Properties().stacksTo(64), 200, true, DarkBlueMoonAddon::barnacleShell));
    public static final DeferredHolder<Item, BarnacleFoodItem> BARNACLE_MEAT = ITEMS.register("barnacle_meat",
            key -> new BarnacleFoodItem(new Item.Properties().stacksTo(64)
                    .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build()),
                    32, false, null));
    public static final DeferredHolder<Item, BarnacleShellItem> BARNACLE_SHELL = ITEMS.register("barnacle_shell",
            key -> new BarnacleShellItem(new Item.Properties().stacksTo(64), 200));
    public static final DeferredHolder<Item, BarnacleFoodItem> COOKED_BARNACLE = ITEMS.register("cooked_barnacle",
            key -> new BarnacleFoodItem(new Item.Properties().stacksTo(64)
                    .food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.6F).build()),
                    100, true, DarkBlueMoonAddon::cookedBarnacleShell));
    public static final DeferredHolder<Item, BarnacleShellItem> COOKED_BARNACLE_SHELL = ITEMS.register(
            "cooked_barnacle_shell", key -> new BarnacleShellItem(new Item.Properties().stacksTo(64), 400));
    public static final DeferredHolder<Item, Item> COOKED_BARNACLE_MEAT = ITEMS.register("cooked_barnacle_meat",
            key -> new Item(new Item.Properties().stacksTo(64)
                    .food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.8F).build())));
    public static final DeferredHolder<Item, BarnacleNecklaceItem> BARNACLE_NECKLACE = ITEMS.register(
            "barnacle_necklace", key -> new BarnacleNecklaceItem(NECKLACE_MATERIAL,
                    new Item.Properties().stacksTo(1).durability(ArmorItem.Type.CHESTPLATE.getDurability(5))));

    public static final DeferredHolder<StandType, EntityStandType> DARK_BLUE_MOON =
            STAND_TYPES.register("dark_blue_moon", DarkBlueMoonAddon::createStand);

    public DarkBlueMoonAddon(IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "Dark Blue Moon requires ROTP addon ABI 1 with namespaced ability resources and Stand visual contexts.");
        }
        ABILITY_TYPES.register(modEventBus);
        ARMOR_MATERIALS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(DarkBlueMoonAddon::registerAttributes);
        modEventBus.addListener(DarkBlueMoonAddon::addCreativeTabItems);
        modContainer.registerConfig(ModConfig.Type.SERVER, DarkBlueMoonConfig.SPEC, DarkBlueMoonConfig.FILE_NAME);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static Item barnacleShell() {
        return BARNACLE_SHELL.get();
    }

    private static Item cookedBarnacleShell() {
        return COOKED_BARNACLE_SHELL.get();
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DARK_BLUE_MOON_ENTITY.get(), DarkBlueMoonEntity.createAttributes().build());
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModItems.MAIN_TAB.getKey()) {
            event.accept(BARNACLE_ITEM.get());
            event.accept(BARNACLE_MEAT.get());
            event.accept(BARNACLE_SHELL.get());
            event.accept(COOKED_BARNACLE.get());
            event.accept(COOKED_BARNACLE_SHELL.get());
            event.accept(COOKED_BARNACLE_MEAT.get());
            event.accept(BARNACLE_NECKLACE.get());
        }
    }

    private static <A extends Ability> DeferredHolder<AbilityType<?>, AbilityType<A>> ability(
            String name, BiFunction<AbilityType<A>, AbilityId, A> factory) {
        return ABILITY_TYPES.register(name, key -> new AbilityType<>(key, factory));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(name, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(PUNCH, ModStandAbilities.PUNCH)
                // 1.16: standSound(PERFORM, false, STAND_PUNCH_LIGHT) as its cry, barrageHitSound(null)
                .addAbility(BARRAGE, BARRAGE_TYPE,
                        barrage -> barrage.barrageCrySound(ModSoundEvents.STAND_PUNCH_LIGHT))
                .addAbility(HEAVY_PUNCH, ModStandAbilities.HEAVY_PUNCH)
                // 1.16 InitStands: the heavy punch's setFinisherVariation(DARK_BLUE_MOON_FINISHER_PUNCH)
                .addAbility(FINISHER_PUNCH, FINISHER_TYPE, finisher -> finisher.initIsFinisher(HEAVY_PUNCH))
                .addAbility(CHARGED_HEAVY, ModStandAbilities.HEAVY_CHARGED)
                .addAbility(BLOCK, ModStandAbilities.GUARD)
                .addAbility(CHOP, CHOP_TYPE)
                .addAbility(SCALE_SHOT, SCALE_SHOT_TYPE)
                .addAbility(SCALE_SHOT_FOCUSED, SCALE_SHOT_FOCUSED_TYPE)
                .addAbility(BARNACLE, BARNACLE_TYPE)
                .addAbility(BARNACLE_CANCEL, BARNACLE_CANCEL_TYPE)
                .addAbility(BARNACLE_SELF, BARNACLE_SELF_TYPE)
                .addAbility(BARNACLE_SELF_CANCEL, BARNACLE_SELF_CANCEL_TYPE)
                .addAbility(BARNACLE_PULL, BARNACLE_PULL_TYPE)
                .addAbility(BARNACLE_TRAP, BARNACLE_TRAP_TYPE)
                .addAbility(BARNACLE_TRAP_CANCEL, BARNACLE_TRAP_CANCEL_TYPE)
                .addAbility(WHIRLPOOL_NAME, WHIRLPOOL_TYPE)
                .addAbility(WHIRLPOOL_CANCEL, WHIRLPOOL_CANCEL_TYPE)
                .makeControlScheme("hotbar")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY_PUNCH, InputMethod.CLICK, InputKey.RMB)
                    .bind(CHARGED_HEAVY, InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                    // 1.16 hotbars: left (chop, scale shot) and right (barnacle, self, pull, trap, whirlpool), each
                    // cancel and the focused scale shot their base's Shift variation
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(CHOP, 0, InputMethod.CLICK)
                    .addToHotbar(SCALE_SHOT, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(SCALE_SHOT_FOCUSED, SCALE_SHOT, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar(BARNACLE, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(BARNACLE_CANCEL, BARNACLE, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar(BARNACLE_SELF, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(BARNACLE_SELF_CANCEL, BARNACLE_SELF, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar(BARNACLE_PULL, 0, InputMethod.CLICK)
                    .addToHotbar(BARNACLE_TRAP, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(BARNACLE_TRAP_CANCEL, BARNACLE_TRAP, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar(WHIRLPOOL_NAME, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(WHIRLPOOL_CANCEL, WHIRLPOOL_NAME, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY_PUNCH, InputMethod.CLICK, InputKey.RMB)
                    .bind(CHARGED_HEAVY, InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme();
        // 1.16 resolveLevelToUnlock(0) on every action: all unlocked from the start
        for (String ability : List.of(PUNCH, BARRAGE, HEAVY_PUNCH, FINISHER_PUNCH, CHARGED_HEAVY, BLOCK, CHOP,
                SCALE_SHOT, SCALE_SHOT_FOCUSED, BARNACLE, BARNACLE_CANCEL, BARNACLE_SELF, BARNACLE_SELF_CANCEL,
                BARNACLE_PULL, BARNACLE_TRAP, BARNACLE_TRAP_CANCEL, WHIRLPOOL_NAME, WHIRLPOOL_CANCEL, MANUAL_CONTROL)) {
            moveset.addSkill(StandUnlockableSkill.startingAbility(ability));
        }

        // 1.16 InitStands: tier 6, power 10, speed 10, range 5/15, durability 12, precision 10, colour 0x1E3A8A, part 3
        return new EntityStandType(
                new StandStats.Builder()
                        .power(10.0)
                        .speed(10.0)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(12.0)
                        .precision(10.0)
                        .build(),
                moveset,
                DARK_BLUE_MOON_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(ENTITY_WIDTH, ENTITY_HEIGHT)
                .summonShout(() -> SUMMON_VOICELINE)
                .ost(() -> DARK_BLUE_MOON_OST);
    }
}
