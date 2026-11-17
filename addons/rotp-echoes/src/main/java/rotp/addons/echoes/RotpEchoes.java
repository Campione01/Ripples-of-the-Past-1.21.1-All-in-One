package rotp.addons.echoes;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.leap.LeapAccessPolicies;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.init.EchoesAbilities;
import rotp.addons.echoes.init.EchoesAttachments;
import rotp.addons.echoes.init.EchoesBlocks;
import rotp.addons.echoes.init.EchoesDataComponents;
import rotp.addons.echoes.init.EchoesEntities;
import rotp.addons.echoes.init.EchoesMobEffects;
import rotp.addons.echoes.init.EchoesSounds;
import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.network.EchoesOnomatopoeiaPayloads;
import rotp.addons.echoes.network.EchoesSelectActPayload;
import rotp.addons.echoes.network.EchoesSoundPayloads;
import rotp.addons.echoes.state.EchoesFreezeState;
import rotp.addons.echoes.state.EchoesGravityService;
import rotp.addons.echoes.state.EchoesUserState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(RotpEchoes.MOD_ID)
public final class RotpEchoes {
    public static final String MOD_ID = "rotp_echoes";
    public static final int REQUIRED_CORE_ABI_VERSION = 1;

    public RotpEchoes(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI_VERSION)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_RANDOM_WEIGHTS_V1)) {
            throw new IllegalStateException(
                    "RotP Echoes requires ROTP addon ABI 1 with namespaced "
                            + "ability resources, stand visual contexts, "
                            + "and random weights.");
        }

        LeapAccessPolicies.register(id("gravity_leap_access"), query -> query.user() != null
                && EchoesGravityService.keptFromLeaping(query.user()));
        EchoesAbilities.ABILITY_TYPES.register(modEventBus);
        EchoesAttachments.ATTACHMENT_TYPES.register(modEventBus);
        EchoesBlocks.BLOCKS.register(modEventBus);
        EchoesBlocks.BLOCK_ENTITY_TYPES.register(modEventBus);
        EchoesDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        EchoesEntities.ENTITY_TYPES.register(modEventBus);
        EchoesMobEffects.MOB_EFFECTS.register(modEventBus);
        EchoesSounds.SOUNDS.register(modEventBus);
        EchoesStands.STANDS.register(modEventBus);
        modEventBus.addListener(RotpEchoes::registerAttributes);
        modEventBus.addListener(EchoesSelectActPayload::register);
        modEventBus.addListener(EchoesOnomatopoeiaPayloads::register);
        modEventBus.addListener(EchoesSoundPayloads::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean isEchoesUser(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        StandPower power = StandPower.get(entity);
        return power != null
                && (power.getPowerType() == EchoesStands.ECHOES_ACT2.get()
                        || power.getPowerType()
                                == EchoesStands.ECHOES_ACT3.get());
    }

    /**
     * The Stand power of the online Echoes user with this id: what he does by a word, a thrown item or a counter is his
     * Stand's work, so it takes the Stand's damage share. Null when he is offline or holds no Echoes.
     */
    @javax.annotation.Nullable
    public static StandPower powerOf(@javax.annotation.Nullable net.minecraft.world.level.Level level,
            @javax.annotation.Nullable java.util.UUID owner) {
        Player user = level != null && owner != null ? level.getPlayerByUUID(owner) : null;
        return isEchoesUser(user) ? StandPower.get(user) : null;
    }

    public static EchoesUserState userState(Player player) {
        return player.getData(EchoesAttachments.USER_STATE);
    }

    public static EchoesFreezeState freezeState(LivingEntity entity) {
        return entity.getData(EchoesAttachments.FREEZE_STATE);
    }

    private static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                EchoesEntities.ACT0.get(),
                StandEntity.createAttributes().build());
        event.put(
                EchoesEntities.ACT1.get(),
                StandEntity.createAttributes().build());
        event.put(
                EchoesEntities.ACT2.get(),
                StandEntity.createAttributes().build());
        event.put(
                EchoesEntities.ACT3.get(),
                StandEntity.createAttributes().build());
    }
}
