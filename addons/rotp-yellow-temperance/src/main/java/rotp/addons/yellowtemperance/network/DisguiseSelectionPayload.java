package rotp.addons.yellowtemperance.network;

import java.util.UUID;

import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.standpower.entity.EntityStandType;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.disguise.DisguisePolicy;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DisguiseSelectionPayload(
        byte modeId,
        String selection) implements CustomPacketPayload {
    public static final int MAX_SELECTION_LENGTH = 256;
    public static final Type<DisguiseSelectionPayload> TYPE =
            new Type<>(YellowTemperanceAddon.id("select_disguise"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf, DisguiseSelectionPayload>
            STREAM_CODEC = StreamCodec.ofMember(
                    DisguiseSelectionPayload::write,
                    DisguiseSelectionPayload::new);

    public DisguiseSelectionPayload {
        if (selection == null
                || selection.length() > MAX_SELECTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Invalid disguise selection length");
        }
    }

    private DisguiseSelectionPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readByte(),
                buffer.readUtf(MAX_SELECTION_LENGTH));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(modeId);
        buffer.writeUtf(selection, MAX_SELECTION_LENGTH);
    }

    public static void handle(
            DisguiseSelectionPayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            select(player, payload.modeId(), payload.selection());
        }
    }

    public static void select(
            ServerPlayer player, byte modeId, String selection) {
        DisguiseMode mode = DisguiseMode.byId(modeId);
        YellowTemperanceControllerEntity controller =
                YellowTemperanceAddon.controller(player);
        long gameTime = player.level().getGameTime();
        if (controller == null
                || !controller.canAcceptSelection(mode, gameTime)
                || !controller.acquireSelectionPermit(gameTime)) {
            return;
        }
        switch (mode) {
            case ENTITY -> applyEntity(
                    controller, player, selection);
            case PLAYER -> applyPlayer(
                    controller, player, selection);
            case STAND, STAND_SELF -> applyStand(
                    controller, mode, selection);
            case NONE -> {
            }
        }
    }

    private static void applyEntity(
            YellowTemperanceControllerEntity controller,
            ServerPlayer player,
            String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        EntityType<?> type = DisguisePolicy.entityType(
                id, player.level());
        if (id != null && type != null
                && id.equals(EntityType.getKey(type))) {
            controller.applyEntityOrStandDisguise(
                    DisguiseMode.ENTITY,
                    id,
                    type.getDimensions());
        }
    }

    private static void applyPlayer(
            YellowTemperanceControllerEntity controller,
            ServerPlayer player,
            String value) {
        try {
            ServerPlayer target = player.server
                    .getPlayerList()
                    .getPlayer(UUID.fromString(value));
            if (target != null) {
                controller.applyPlayerDisguise(target);
            }
        }
        catch (IllegalArgumentException ignored) {
        }
    }

    private static void applyStand(
            YellowTemperanceControllerEntity controller,
            DisguiseMode mode,
            String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        Object stand = id != null
                ? JojoRegistries.DEFAULT_STANDS_REG
                        .getOptional(id).orElse(null)
                : null;
        if (DisguisePolicy.isAllowedStand(id, stand)
                && stand instanceof EntityStandType entityStandType) {
            if (mode == DisguiseMode.STAND) {
                controller.applyBesideStand(id);
            }
            else {
                controller.applyEntityOrStandDisguise(
                        mode,
                        id,
                        entityStandType.getEntityType().getDimensions());
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
