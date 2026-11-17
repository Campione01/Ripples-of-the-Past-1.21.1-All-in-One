package rotp.addons.tog;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import rotp.addons.tog.TowerOfGrayAbility.Move;
import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;

@Mod(TowerOfGrayAddon.MOD_ID)
public final class TowerOfGrayAddon {
    public static final String MOD_ID = "rotp_tog";
    public static final float WIDTH = 0.7F;
    public static final float HEIGHT = 2.1F;

    private static final DeferredRegister<AbilityType<?>> ABILITIES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    private static final DeferredRegister<StandType> STANDS =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    private static final Map<Move, DeferredHolder<AbilityType<?>, AbilityType<TowerOfGrayAbility>>> MOVES =
            new EnumMap<>(Move.class);
    static {
        for (Move move : Move.values()) {
            MOVES.put(move, ABILITIES.register(move.id,
                    key -> new AbilityType<>(key, (type, id) -> new TowerOfGrayAbility(type, id, move))));
        }
    }

    public static final DeferredHolder<EntityType<?>, EntityType<TowerOfGrayEntity>> ENTITY =
            ENTITIES.register("tower_of_gray", key -> EntityType.Builder
                    .<TowerOfGrayEntity>of(TowerOfGrayEntity::new, MobCategory.MISC)
                    .noSave().noSummon().sized(WIDTH, HEIGHT)
                    .clientTrackingRange(32).updateInterval(2).build(key.toString()));
    public static final DeferredHolder<StandType, EntityStandType> STAND =
            STANDS.register("tower_of_gray", TowerOfGrayAddon::createStand);

    public TowerOfGrayAddon(IEventBus bus) {
        if (!RotpAddonApi.supportsAbi(1)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)) {
            throw new IllegalStateException("Tower of Gray requires ROTP addon ABI 1 and namespaced ability resources.");
        }
        ABILITIES.register(bus);
        STANDS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(TowerOfGrayAddon::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ENTITY.get(), TowerOfGrayEntity.createAttributes().build());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder().addManualControl()
                .addSkill(StandUnlockableSkill.startingAbility("manual_control"));
        for (Move move : Move.values()) {
            moveset.addAbility(move.id, MOVES.get(move));
            moveset.addSkill(move.resolve == 0 ? StandUnlockableSkill.startingAbility(move.id)
                    : StandUnlockableSkill.unlockableAbility(move.id, 0));
        }
        var hotbar = moveset.makeControlScheme("hotbar")
                .bind(Move.DASH.id, InputMethod.CLICK, InputKey.LMB)
                .bind(Move.FLY.id, InputMethod.CLICK, InputKey.RMB)
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL);
        for (Move move : Move.values()) {
            hotbar.addToHotbar(move.id, 0, move.hold() ? InputMethod.HOLD : InputMethod.CLICK);
        }
        hotbar.finalizeControlScheme()
                .makeControlScheme("keybinds")
                .bind(Move.DASH.id, InputMethod.CLICK, InputKey.LMB)
                .bind(Move.FLY.id, InputMethod.CLICK, InputKey.RMB)
                .finalizeControlScheme();

        // A manually pilotable body with its own homing and orbit actions, not a humanoid punch rig.
        return new EntityStandType(new StandStats.Builder().power(1).speed(14)
                .range(25, 50).durability(8).precision(1).build(), moveset, ENTITY.get(), id)
                .standControlType(StandControlType.HYBRID_FORM)
                .manualControl(true).distanceStrengthDecay(true).standDimensions(WIDTH, HEIGHT);
    }
}
