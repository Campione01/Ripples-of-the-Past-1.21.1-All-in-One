package rotp.addons.beachboy;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import rotp.addons.beachboy.ability.BeachBoyHeartCrushAbility;
import rotp.addons.beachboy.ability.BeachBoyRetractAbility;
import rotp.addons.beachboy.ability.BeachBoyRipAbility;
import rotp.addons.beachboy.ability.BeachBoyToggleAbility;
import rotp.addons.beachboy.ability.BeachBoyTrapAbility;
import rotp.addons.beachboy.ability.BeachBoyWhipAbility;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.addons.beachboy.item.BeachBoyRodItem;
import rotp.addons.beachboy.network.BeachBoyPayloads;
import rotp.addons.beachboy.stand.BeachBoyStandType;
import rotp.addons.beachboy.util.BeachBoySettings;
import rotp.addons.beachboy.util.BeachBoyStaminaPause;
import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandStaminaModifiers;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Beach Boy from Next Album 0.1.13 (InitStands, InitEntities, InitItems, InitSounds, InitStandEffects). */
@Mod(BeachBoyAddon.MOD_ID)
public final class BeachBoyAddon {
    public static final String MOD_ID = "rotp_beach_boy";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String WHIP = "beach_boy_whip";
    public static final String SLASH = "beach_boy_slash";
    public static final String RIP = "beach_boy_rip";
    public static final String HEART_CRUSH = "beach_boy_heart_crush";
    public static final String TRAP = "beach_boy_trap";
    public static final String TRAP_RETRACT = "beach_boy_trap_retract";
    public static final String RETRACT_HOOK = "beach_boy_retract_hook";
    public static final String QUICK_REEL = "beach_boy_quick_reel";
    public static final String LINE_COLLISION = "beach_boy_line_collision";
    public static final String CRUSH_DAMAGE = "beach_boy_crush_damage";
    public static final String SOUND_SENSE = "beach_boy_sound_sense";
    public static final String MANUAL_CONTROL = "manual_control";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final ResourceKey<DamageType> CRUSH_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, id("beach_boy_crush"));
    public static final ResourceKey<DamageType> RIP_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, id("beach_boy_rip"));
    public static final ResourceKey<DamageType> HEART_CRUSH_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, id("beach_boy_heart_crush"));

    // abilities (1.16 InitStands BEACH_BOY_*)
    public static final DeferredHolder<AbilityType<?>, AbilityType<BeachBoyWhipAbility>> WHIP_TYPE =
            ability(WHIP, (type, id) -> new BeachBoyWhipAbility(type, id, false));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BeachBoyWhipAbility>> SLASH_TYPE =
            ability(SLASH, (type, id) -> new BeachBoyWhipAbility(type, id, true));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BeachBoyRipAbility>> RIP_TYPE =
            ability(RIP, BeachBoyRipAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<BeachBoyHeartCrushAbility>> HEART_CRUSH_TYPE =
            ability(HEART_CRUSH, BeachBoyHeartCrushAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<BeachBoyTrapAbility>> TRAP_TYPE =
            ability(TRAP, BeachBoyTrapAbility::new);
    public static final DeferredHolder<AbilityType<?>, AbilityType<BeachBoyRetractAbility>> TRAP_RETRACT_TYPE =
            ability(TRAP_RETRACT, (type, id) -> new BeachBoyRetractAbility(type, id, false, 5.0F));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BeachBoyRetractAbility>> RETRACT_HOOK_TYPE =
            ability(RETRACT_HOOK, (type, id) -> new BeachBoyRetractAbility(type, id, true, 0.0F));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BeachBoyRetractAbility>> QUICK_REEL_TYPE =
            ability(QUICK_REEL, (type, id) -> new BeachBoyRetractAbility(type, id, false, 0.0F));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BeachBoyToggleAbility>> LINE_COLLISION_TYPE =
            ability(LINE_COLLISION, (type, id) -> new BeachBoyToggleAbility(type, id, BeachBoySettings.Toggle.PATH_LINE));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BeachBoyToggleAbility>> CRUSH_DAMAGE_TYPE_ABILITY =
            ability(CRUSH_DAMAGE, (type, id) -> new BeachBoyToggleAbility(type, id, BeachBoySettings.Toggle.CRUSH_DAMAGE));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BeachBoyToggleAbility>> SOUND_SENSE_TYPE =
            ability(SOUND_SENSE, (type, id) -> new BeachBoyToggleAbility(type, id, BeachBoySettings.Toggle.SOUND_SENSE));

    // 1.16 InitStandEffects BEACH_BOY_SETTINGS: kept on the user across summons, deaths and logouts
    public static final Supplier<AttachmentType<BeachBoySettings.State>> SETTINGS = ATTACHMENT_TYPES.register(
            "beach_boy_settings", () -> AttachmentType.builder(BeachBoySettings.State::new)
                    .serialize(BeachBoySettings.State.CODEC)
                    .copyOnDeath()
                    .build());

    // entities: 1.16 StandEntityType(BeachBoyStandEntity::new, 0, 0); hook size 0.25, tracking 64, interval 1
    public static final DeferredHolder<EntityType<?>, EntityType<BeachBoyStandEntity>> BEACH_BOY_ENTITY =
            ENTITY_TYPES.register("beach_boy", key -> EntityType.Builder
                    .<BeachBoyStandEntity>of(BeachBoyStandEntity::new, MobCategory.MISC)
                    .noSave()
                    .noSummon()
                    .sized(0.0F, 0.0F)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<BeachBoyHookEntity>> BEACH_BOY_HOOK =
            ENTITY_TYPES.register("beach_boy_hook", key -> EntityType.Builder
                    .<BeachBoyHookEntity>of(BeachBoyHookEntity::new, MobCategory.MISC)
                    .noSummon()
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(key.toString()));

    // 1.16 rod NBT: Cast, Reel, StandScaledDamage
    public static final Supplier<DataComponentType<Boolean>> ROD_CAST = DATA_COMPONENTS.registerComponentType(
            "rod_cast", builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final Supplier<DataComponentType<Float>> ROD_REEL = DATA_COMPONENTS.registerComponentType(
            "rod_reel", builder -> builder.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));
    public static final Supplier<DataComponentType<Double>> ROD_DAMAGE = DATA_COMPONENTS.registerComponentType(
            "rod_damage", builder -> builder.persistent(Codec.DOUBLE).networkSynchronized(ByteBufCodecs.DOUBLE));

    public static final DeferredHolder<Item, BeachBoyRodItem> BEACH_BOY_ROD = ITEMS.register("beach_boy_rod",
            key -> new BeachBoyRodItem(new Item.Properties().stacksTo(1)));

    // sounds (InitSounds); the three MultiSoundEvents are sounds.json events with both lines
    public static final DeferredHolder<SoundEvent, SoundEvent> PESCI_BEACH_BOY = sound("pesci_beach_boy");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEACH_BOY_SUMMON = sound("beach_boy_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEACH_BOY_UNSUMMON = sound("beach_boy_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEACH_BOY_REEL_BACK = sound("beach_boy_reel_back");
    public static final DeferredHolder<SoundEvent, SoundEvent> PESCI_GET_MOB = sound("pesci_get_mob_shout");
    public static final DeferredHolder<SoundEvent, SoundEvent> PESCI_REEL_IN = sound("pesci_reel_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> PESCI_BROTHER = sound("pesci_brother");
    public static final DeferredHolder<SoundEvent, SoundEvent> PESCI_KILL_YOU = sound("pesci_kill_you");
    public static final DeferredHolder<SoundEvent, SoundEvent> PESCI_LOST_TARGET = sound("pesci_lost_target");
    static {
        for (String single : List.of("pesci_summon", "pesci_summon_2", "pesci_get_mob", "pesci_get_mob_full",
                "pesci_reel_you_in", "pesci_wont_let_it_happen")) {
            sound(single);
        }
    }

    public static final DeferredHolder<StandType, BeachBoyStandType> BEACH_BOY =
            STAND_TYPES.register("beach_boy", BeachBoyAddon::createStand);

    public BeachBoyAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_RANDOM_WEIGHTS_V1)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_STAMINA_MODIFIERS_V1)) {
            throw new IllegalStateException(
                    "RotP Beach Boy requires ROTP addon ABI 1 with namespaced ability resources, Stand visual "
                            + "contexts, Stand random weights and Stand stamina modifiers.");
        }
        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(BeachBoyAddon::registerAttributes);
        modEventBus.addListener(BeachBoyPayloads::register);
        StandStaminaModifiers.registerTickGain(id("stamina_pause"), BeachBoyStaminaPause::tickGainFactor);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BEACH_BOY_ENTITY.get(), BeachBoyStandEntity.createAttributes().build());
    }

    private static <A extends Ability> DeferredHolder<AbilityType<?>, AbilityType<A>> ability(
            String name, BiFunction<AbilityType<A>, AbilityId, A> factory) {
        return ABILITY_TYPES.register(name, key -> new AbilityType<>(key, factory));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(name, SoundEvent::createVariableRangeEvent);
    }

    private static BeachBoyStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(WHIP, WHIP_TYPE, a -> a.resolveLevelToUnlock(1))
                .addAbility(SLASH, SLASH_TYPE, a -> a.resolveLevelToUnlock(2))
                .addAbility(RIP, RIP_TYPE, a -> a.resolveLevelToUnlock(3))
                .addAbility(HEART_CRUSH, HEART_CRUSH_TYPE, a -> a.resolveLevelToUnlock(4))
                .addAbility(TRAP, TRAP_TYPE, a -> a.resolveLevelToUnlock(2))
                .addAbility(TRAP_RETRACT, TRAP_RETRACT_TYPE, a -> a.resolveLevelToUnlock(2))
                .addAbility(RETRACT_HOOK, RETRACT_HOOK_TYPE, a -> a.resolveLevelToUnlock(0))
                .addAbility(QUICK_REEL, QUICK_REEL_TYPE, a -> a.resolveLevelToUnlock(0))
                .addAbility(LINE_COLLISION, LINE_COLLISION_TYPE, a -> a.resolveLevelToUnlock(0))
                .addAbility(CRUSH_DAMAGE, CRUSH_DAMAGE_TYPE_ABILITY, a -> a.resolveLevelToUnlock(0))
                .addAbility(SOUND_SENSE, SOUND_SENSE_TYPE, a -> a.resolveLevelToUnlock(2));
        // 1.16: LMB hotbar (lash, tear, trap hook) and RMB hotbar (detach, path line, crush damage, sound sense),
        // each Shift variation on its base slot; here one skill bar (C selects, X uses), the rod keeps the mouse
        moveset.makeControlScheme("hotbar")
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                .addToHotbar(WHIP, 0, InputMethod.CLICK)
                .addHotbarSlotVariation(SLASH, WHIP, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                .addToHotbar(RIP, 0, InputMethod.CLICK)
                .addHotbarSlotVariation(HEART_CRUSH, RIP, InputKey.Modifier.SHIFT, InputMethod.HOLD)
                .addToHotbar(TRAP, 0, InputMethod.CLICK)
                .addHotbarSlotVariation(TRAP_RETRACT, TRAP, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                .addToHotbar(RETRACT_HOOK, 0, InputMethod.CLICK)
                .addHotbarSlotVariation(QUICK_REEL, RETRACT_HOOK, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                .addToHotbar(LINE_COLLISION, 0, InputMethod.CLICK)
                .addToHotbar(CRUSH_DAMAGE, 0, InputMethod.CLICK)
                .addToHotbar(SOUND_SENSE, 0, InputMethod.CLICK)
                .finalizeControlScheme();
        moveset.makeControlScheme("keybinds")
                .finalizeControlScheme();
        for (String ability : List.of(WHIP, SLASH, RIP, HEART_CRUSH, TRAP, TRAP_RETRACT, RETRACT_HOOK, QUICK_REEL,
                LINE_COLLISION, CRUSH_DAMAGE, SOUND_SENSE, MANUAL_CONTROL)) {
            moveset.addSkill(StandUnlockableSkill.startingAbility(ability));
        }

        // 1.16: tier 5, power 8/10, speed 14/16, range 100/100, durability 8/10, precision 16/18, weight 2,
        // colour 0x7EB3D6, part 5, no Stand leap, summon shout PESCI_BEACH_BOY; a remote Stand: manual control moves
        // its hook, with no strength decay over distance
        return new BeachBoyStandType(
                new StandStats.Builder()
                        .power(8.0, 10.0)
                        .speed(14.0, 16.0)
                        .range(100.0, 100.0)
                        .durability(8.0, 10.0)
                        .precision(16.0, 18.0)
                        .randomWeight(2.0)
                        .build(),
                moveset,
                BEACH_BOY_ENTITY.get(),
                id)
                .standControlType(StandControlType.LONG_DISTANCE_OPERATION)
                .manualControl(true)
                .distanceStrengthDecay(false)
                .standDimensions(0.0F, 0.0F)
                .summonShout(() -> PESCI_BEACH_BOY)
                .summonSound(() -> BEACH_BOY_SUMMON)
                .unsummonSound(() -> BEACH_BOY_UNSUMMON);
    }
}
