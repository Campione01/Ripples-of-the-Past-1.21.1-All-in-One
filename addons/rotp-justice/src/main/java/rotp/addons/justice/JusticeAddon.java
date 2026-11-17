package rotp.addons.justice;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;

@Mod(JusticeAddon.MOD_ID)
public final class JusticeAddon {
    public static final String MOD_ID = "rotp_justice";
    public static final float WIDTH = 0.7F;
    public static final float HEIGHT = 2.1F;

    enum Move {
        PUPPET("puppet"), FOG("fog"), ILLUSION("illusion"), DISGUISE("disguise");
        final String id;

        Move(String id) {
            this.id = id;
        }
    }

    private static final DeferredRegister<AbilityType<?>> ABILITIES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<StandType> STANDS =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    private static final Map<Move, DeferredHolder<AbilityType<?>, AbilityType<JusticeAbility>>> MOVES =
            new EnumMap<>(Move.class);
    static {
        for (Move move : Move.values()) {
            MOVES.put(move, ABILITIES.register(move.id,
                    key -> new AbilityType<>(key, (type, id) -> new JusticeAbility(type, id, move))));
        }
    }

    public static final DeferredHolder<EntityType<?>, EntityType<JusticeEntity>> ENTITY =
            ENTITIES.register("justice", key -> EntityType.Builder.<JusticeEntity>of(JusticeEntity::new, MobCategory.MISC)
                    .noSave().noSummon().sized(WIDTH, HEIGHT).clientTrackingRange(32)
                    .updateInterval(2).build(key.toString()));
    public static final DeferredHolder<StandType, EntityStandType> STAND =
            STANDS.register("justice", JusticeAddon::createStand);

    public JusticeAddon(IEventBus bus) {
        if (!RotpAddonApi.supportsAbi(1)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)) {
            throw new IllegalStateException("Justice requires ROTP addon ABI 1 and namespaced ability resources.");
        }
        ABILITIES.register(bus);
        ENTITIES.register(bus);
        STANDS.register(bus);
        bus.addListener(JusticeAddon::registerAttributes);
        bus.addListener(JusticeVisualPayload::register);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ENTITY.get(), JusticeEntity.createAttributes().build());
    }

    public static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moves = new MovesetBuilder().addManualControl()
                .addSkill(StandUnlockableSkill.startingAbility("manual_control"));
        for (Move move : Move.values()) {
            moves.addAbility(move.id, MOVES.get(move));
            moves.addSkill(StandUnlockableSkill.startingAbility(move.id));
        }
        var bar = moves.makeControlScheme("hotbar")
                .bind(Move.PUPPET.id, InputMethod.CLICK, InputKey.LMB)
                .bind(Move.FOG.id, InputMethod.CLICK, InputKey.RMB)
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL);
        for (Move move : Move.values()) {
            bar.addToHotbar(move.id, 0, InputMethod.CLICK);
        }
        bar.finalizeControlScheme().makeControlScheme("keybinds")
                .bind(Move.PUPPET.id, InputMethod.CLICK, InputKey.LMB)
                .bind(Move.FOG.id, InputMethod.CLICK, InputKey.RMB)
                .finalizeControlScheme();

        return new EntityStandType(new StandStats.Builder().power(20).speed(20)
                .range(50, 100).durability(20).precision(20).build(), moves, ENTITY.get(), id)
                .standControlType(StandControlType.HYBRID_FORM)
                .manualControl(true).distanceStrengthDecay(true).standDimensions(WIDTH, HEIGHT);
    }

    private static final class JusticeAbility extends Ability {
        private final Move move;

        private JusticeAbility(AbilityType<?> type, AbilityId id, Move move) {
            super(type, id);
            this.move = move;
        }

        // 1.16 PowerBaseImpl.checkRequirements refused every action of a stunned performer, the Stand or else the caster.
        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            ConditionCheck stun = checkPerformerStun(context);
            return stun.isPositive() ? super.checkSpecificConditions(context) : stun;
        }

        @Override
        public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf input, InputMethod method,
                float clickHoldResolveTime, BufferingState state) {
            if (method == InputMethod.CLICK && user instanceof ServerPlayer player && !level.isClientSide()) {
                JusticeGameEvents.perform(player, move);
                state.isActionSuccess = true;
            }
            return null;
        }
    }
}
