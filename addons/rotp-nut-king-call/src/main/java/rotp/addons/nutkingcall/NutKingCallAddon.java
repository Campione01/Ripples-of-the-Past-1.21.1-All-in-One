package rotp.addons.nutkingcall;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
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
import rotp.core.util.sound.OstSoundList;

import rotp.addons.nutkingcall.ability.NutKingCallAbility;
import rotp.addons.nutkingcall.ability.NutKingCallAbility.Mode;
import rotp.addons.nutkingcall.ability.NutKingCallFlyingKickAbility;
import rotp.addons.nutkingcall.block.NutKingCallNailBlock;
import rotp.addons.nutkingcall.block.NutKingCallNailBlockEntity;
import rotp.addons.nutkingcall.entity.NutKingCallAttachedNailEntity;
import rotp.addons.nutkingcall.entity.NutKingCallEntity;
import rotp.addons.nutkingcall.entity.NutKingCallNailEntity;
import rotp.addons.nutkingcall.entity.NutKingCallNutEntity;
import rotp.addons.nutkingcall.network.NailStatePayload;
import rotp.addons.nutkingcall.state.NailState;
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
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(NutKingCallAddon.MOD_ID)
public final class NutKingCallAddon {
    public static final String MOD_ID = "nutkingcall";
    private static final int REQUIRED_CORE_ABI = 1;
    private static final String NETWORK_VERSION = "1";

    public static final float STAND_WIDTH = 0.7F;
    public static final float STAND_HEIGHT = 2.1F;
    public static final double EFFECTIVE_RANGE = 5.0D;
    public static final double MAX_RANGE = 10.0D;

    public static final String PUNCH = "nut_king_call_punch";
    public static final String BARRAGE = "nut_king_call_barrage";
    public static final String HEAVY_PUNCH = "nut_king_call_heavy_punch";
    public static final String BLOCK = "nut_king_call_block";
    public static final String QUICK_ASSEMBLY = "nut_king_call_quick_assembly";
    public static final String TOGGLE_MODE = "nut_king_call_toggle_mode";
    public static final String FIRE_NAIL = "nut_king_call_fire_nail";
    public static final String DISMANTLE = "nut_king_call_dismantle";
    public static final String GIVE_NAIL = "nut_king_call_give_nail";
    public static final String PIN = "nut_king_call_pin";
    public static final String UNPIN_TARGET = "nut_king_call_unpin_target";
    public static final String PIN_SELF = "nut_king_call_pin_self";
    public static final String UNPIN_SELF = "nut_king_call_unpin_self";
    public static final String FLYING_KICK = "nut_king_call_flying_kick";
    public static final String MANUAL_CONTROL = "manual_control";

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
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<AbilityType<?>, AbilityType<NutKingCallAbility>>
            QUICK_ASSEMBLY_ABILITY = special(QUICK_ASSEMBLY, Mode.QUICK_ASSEMBLY);
    public static final DeferredHolder<AbilityType<?>, AbilityType<NutKingCallAbility>>
            TOGGLE_MODE_ABILITY = special(TOGGLE_MODE, Mode.TOGGLE_MODE);
    public static final DeferredHolder<AbilityType<?>, AbilityType<NutKingCallAbility>>
            FIRE_NAIL_ABILITY = special(FIRE_NAIL, Mode.FIRE_NAIL);
    public static final DeferredHolder<AbilityType<?>, AbilityType<NutKingCallAbility>>
            DISMANTLE_ABILITY = special(DISMANTLE, Mode.DISMANTLE);
    public static final DeferredHolder<AbilityType<?>, AbilityType<NutKingCallAbility>>
            GIVE_NAIL_ABILITY = special(GIVE_NAIL, Mode.GIVE_NAIL);
    public static final DeferredHolder<AbilityType<?>, AbilityType<NutKingCallAbility>>
            PIN_ABILITY = special(PIN, Mode.PIN);
    public static final DeferredHolder<AbilityType<?>, AbilityType<NutKingCallAbility>>
            UNPIN_TARGET_ABILITY = special(UNPIN_TARGET, Mode.UNPIN_TARGET);
    public static final DeferredHolder<AbilityType<?>, AbilityType<NutKingCallAbility>>
            PIN_SELF_ABILITY = special(PIN_SELF, Mode.PIN_SELF);
    public static final DeferredHolder<AbilityType<?>, AbilityType<NutKingCallAbility>>
            UNPIN_SELF_ABILITY = special(UNPIN_SELF, Mode.UNPIN_SELF);
    public static final DeferredHolder<AbilityType<?>, AbilityType<NutKingCallFlyingKickAbility>>
            FLYING_KICK_ABILITY = ABILITY_TYPES.register(
                    FLYING_KICK, key -> new AbilityType<>(key, NutKingCallFlyingKickAbility::new));

    public static final DeferredHolder<EntityType<?>, EntityType<NutKingCallEntity>>
            NUT_KING_CALL_ENTITY = ENTITY_TYPES.register(
                    "nut_king_call",
                    key -> EntityType.Builder.<NutKingCallEntity>of(
                                    NutKingCallEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<NutKingCallNailEntity>>
            NAIL_ENTITY = ENTITY_TYPES.register(
                    "nut_king_call_nail",
                    key -> EntityType.Builder.<NutKingCallNailEntity>of(
                                    NutKingCallNailEntity::new, MobCategory.MISC)
                            .sized(0.3F, 0.3F)
                            .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<NutKingCallNutEntity>>
            NUT_ENTITY = ENTITY_TYPES.register(
                    "nut_king_call_nut",
                    key -> EntityType.Builder.<NutKingCallNutEntity>of(
                                    NutKingCallNutEntity::new, MobCategory.MISC)
                            .sized(0.3F, 0.3F)
                            .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<NutKingCallAttachedNailEntity>>
            ATTACHED_NAIL_ENTITY = ENTITY_TYPES.register(
                    "nut_king_call_attached_nail",
                    key -> EntityType.Builder.<NutKingCallAttachedNailEntity>of(
                                    NutKingCallAttachedNailEntity::new, MobCategory.MISC)
                            .sized(0.3F, 0.3F)
                            .build(key.toString()));

    public static final DeferredHolder<Block, NutKingCallNailBlock> NAIL_BLOCK =
            BLOCKS.register("nut_king_call_nail_block", NutKingCallNailBlock::new);
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NutKingCallNailBlockEntity>>
            NAIL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "nut_king_call_nail",
                    () -> BlockEntityType.Builder.of(
                                    NutKingCallNailBlockEntity::new, NAIL_BLOCK.get())
                            .build(null));

    public static final Supplier<AttachmentType<NailState>> NAIL_STATE =
            ATTACHMENTS.register(
                    "nail_capability",
                    () -> AttachmentType.serializable(
                                    holder -> new NailState(
                                            holder instanceof LivingEntity living ? living : null))
                            .build());

    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON_VOICELINE =
            SOUND_EVENTS.register(
                    "nut_king_call_summon_voiceline", SoundEvent::createVariableRangeEvent);
    public static final OstSoundList OST =
            new OstSoundList(id("nut_king_call_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType> NUT_KING_CALL =
            STAND_TYPES.register("nut_king_call", NutKingCallAddon::createStand);

    public NutKingCallAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "Nut King Call requires ROTP addon ABI 1 with namespaced ability resources and Stand visual contexts.");
        }
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(NutKingCallEvents::registerAttributes);
        modEventBus.addListener(NutKingCallAddon::registerPayloads);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<AbilityType<?>, AbilityType<NutKingCallAbility>> special(
            String name, Mode mode) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key, (type, abilityId) -> new NutKingCallAbility(type, abilityId, mode)));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_VERSION).playToClient(
                NailStatePayload.TYPE,
                NailStatePayload.STREAM_CODEC,
                NailStatePayload::handle);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(PUNCH, ModStandAbilities.PUNCH)
                .addAbility(BARRAGE, ModStandAbilities.BARRAGE)
                .addAbility(HEAVY_PUNCH, ModStandAbilities.HEAVY_PUNCH)
                .addAbility(BLOCK, ModStandAbilities.GUARD)
                .addAbility(FIRE_NAIL, FIRE_NAIL_ABILITY)
                .addAbility(DISMANTLE, DISMANTLE_ABILITY)
                .addAbility(FLYING_KICK, FLYING_KICK_ABILITY)
                .addAbility(QUICK_ASSEMBLY, QUICK_ASSEMBLY_ABILITY)
                .addAbility(TOGGLE_MODE, TOGGLE_MODE_ABILITY)
                .addAbility(GIVE_NAIL, GIVE_NAIL_ABILITY)
                .addAbility(PIN, PIN_ABILITY)
                .addAbility(UNPIN_TARGET, UNPIN_TARGET_ABILITY)
                .addAbility(PIN_SELF, PIN_SELF_ABILITY)
                .addAbility(UNPIN_SELF, UNPIN_SELF_ABILITY)
                .makeControlScheme("hotbar")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY_PUNCH, InputMethod.CLICK, InputKey.RMB)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    // 1.16 left hotbar after the melee pair: fire nail (Shift: dismantle), flying kick
                    .addToHotbar(FIRE_NAIL, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            DISMANTLE, FIRE_NAIL, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar(FLYING_KICK, 0, InputMethod.CLICK)
                    // 1.16 right hotbar after the block: quick assembly (Shift: toggle mode), give nail,
                    // pin (Shift: unpin target), pin self (Shift: unpin self)
                    .addToHotbar(QUICK_ASSEMBLY, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            TOGGLE_MODE, QUICK_ASSEMBLY, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar(GIVE_NAIL, 0, InputMethod.CLICK)
                    .addToHotbar(PIN, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            UNPIN_TARGET, PIN, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                    .addToHotbar(PIN_SELF, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            UNPIN_SELF, PIN_SELF, InputKey.Modifier.SHIFT, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY_PUNCH, InputMethod.CLICK, InputKey.RMB)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(BARRAGE))
                .addSkill(StandUnlockableSkill.startingAbility(HEAVY_PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(BLOCK))
                .addSkill(StandUnlockableSkill.startingAbility(FIRE_NAIL))
                .addSkill(StandUnlockableSkill.startingAbility(DISMANTLE))
                .addSkill(StandUnlockableSkill.startingAbility(FLYING_KICK))
                .addSkill(StandUnlockableSkill.startingAbility(QUICK_ASSEMBLY))
                .addSkill(StandUnlockableSkill.startingAbility(TOGGLE_MODE))
                .addSkill(StandUnlockableSkill.startingAbility(GIVE_NAIL))
                .addSkill(StandUnlockableSkill.startingAbility(PIN))
                .addSkill(StandUnlockableSkill.startingAbility(UNPIN_TARGET))
                .addSkill(StandUnlockableSkill.startingAbility(PIN_SELF))
                .addSkill(StandUnlockableSkill.startingAbility(UNPIN_SELF))
                .addSkill(StandUnlockableSkill.startingAbility(MANUAL_CONTROL));

        // 1.16 StandStats: tier 6, power 10, speed 5, range 5/10, durability 17, precision 1
        return new EntityStandType(
                new StandStats.Builder()
                        .power(10.0D)
                        .speed(5.0D)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(17.0D)
                        .precision(1.0D)
                        .build(),
                moveset,
                NUT_KING_CALL_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonShout(() -> SUMMON_VOICELINE)
                .summonSound(() -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> OST);
    }
}
