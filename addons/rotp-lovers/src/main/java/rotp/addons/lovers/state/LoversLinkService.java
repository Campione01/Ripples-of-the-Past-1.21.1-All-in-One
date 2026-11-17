package rotp.addons.lovers.state;

import java.util.UUID;

import rotp.addons.lovers.LoversAddon;
import rotp.addons.lovers.entity.LoversStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class LoversLinkService {
    public enum LinkResult {
        ATTACHED,
        RETRACTED,
        TARGET_OCCUPIED,
        INVALID
    }

    private LoversLinkService() {}

    public static LinkResult toggleLink(
            ServerPlayer owner,
            LoversStandEntity stand,
            LivingEntity target) {
        LivingEntity existing = validate(owner);
        if (existing != null) {
            clearOwner(owner);
            return LinkResult.RETRACTED;
        }
        if (stand != LoversAddon.activeStand(owner)
                || !isAttachCandidate(owner, target)) {
            return LinkResult.INVALID;
        }
        if (targetOccupied(owner, target.getUUID())) {
            return LinkResult.TARGET_OCCUPIED;
        }

        UUID generation = stand.beginLink(target.getUUID());
        LoversLinkState state = LoversAddon.linkState(owner);
        state.link(
                owner.getUUID(),
                target.getUUID(),
                stand.getUUID(),
                generation,
                owner.level().dimension().location());
        owner.syncData(LoversAddon.LINK_STATE);
        return LinkResult.ATTACHED;
    }

    public static LivingEntity validate(ServerPlayer owner) {
        LoversLinkState state = LoversAddon.linkState(owner);
        LoversStandEntity stand = LoversAddon.activeStand(owner);
        if (!state.isActive()) {
            if (stand != null && stand.hasLinkData()) {
                stand.clearLinkData();
            }
            return null;
        }

        if (!(owner.level() instanceof ServerLevel level)
                || !owner.isAlive()
                || owner.isRemoved()
                || owner.isSpectator()
                || !owner.getUUID().equals(state.ownerUuid())
                || !level.dimension().location().equals(
                        state.dimension())
                || !LoversAddon.isLoversUser(owner)
                || stand != null
                        && (!stand.getUUID().equals(state.standUuid())
                                || !stand.matchesLink(
                                        state.targetUuid(),
                                        state.generationUuid()))) {
            clearOwner(owner);
            return null;
        }

        Entity entity = level.getEntity(state.targetUuid());
        if (!(entity instanceof LivingEntity target)
                || !target.isAlive()
                || target.isRemoved()
                || target == owner
                || target.level() != owner.level()
                || owner.distanceToSqr(target)
                        > LoversAddon.MAX_RANGE
                                * LoversAddon.MAX_RANGE) {
            clearOwner(owner);
            return null;
        }
        return target;
    }

    public static boolean clearOwner(ServerPlayer owner) {
        if (!owner.hasData(LoversAddon.LINK_STATE)) {
            return false;
        }
        LoversLinkState state = LoversAddon.linkState(owner);
        UUID standUuid = state.standUuid();
        UUID generationUuid = state.generationUuid();
        if (!state.clear()) {
            return false;
        }

        LoversStandEntity stand = LoversAddon.activeStand(owner);
        if (stand != null
                && stand.getUUID().equals(standUuid)) {
            stand.clearLinkData(generationUuid);
        }
        owner.syncData(LoversAddon.LINK_STATE);
        return true;
    }

    public static void clearByTarget(
            MinecraftServer server, UUID targetUuid) {
        if (server == null || targetUuid == null) {
            return;
        }
        for (ServerPlayer player
                : server.getPlayerList().getPlayers()) {
            if (player.hasData(LoversAddon.LINK_STATE)
                    && LoversAddon.linkState(player)
                            .matchesTarget(targetUuid)) {
                clearOwner(player);
            }
        }
    }

    // The owner's rule of 2026-11-06: the link goes on with the Stand recalled, and the next summon takes it over, so
    // that its keys end it.
    public static void takeOver(
            ServerPlayer owner, LoversStandEntity stand) {
        if (!owner.hasData(LoversAddon.LINK_STATE)) {
            return;
        }
        LoversLinkState state = LoversAddon.linkState(owner);
        if (state.isActive()) {
            UUID generation = stand.beginLink(state.targetUuid());
            state.link(
                    state.ownerUuid(),
                    state.targetUuid(),
                    stand.getUUID(),
                    generation,
                    state.dimension());
            owner.syncData(LoversAddon.LINK_STATE);
        }
    }

    private static boolean isAttachCandidate(
            ServerPlayer owner, LivingEntity target) {
        return target != null
                && target != owner
                && target.isAlive()
                && !target.isRemoved()
                && target.level() == owner.level()
                && owner.distanceToSqr(target)
                        <= LoversAddon.EFFECTIVE_RANGE
                                * LoversAddon.EFFECTIVE_RANGE;
    }

    private static boolean targetOccupied(
            ServerPlayer owner, UUID targetUuid) {
        for (ServerPlayer other
                : owner.getServer().getPlayerList().getPlayers()) {
            if (other == owner
                    || !other.hasData(LoversAddon.LINK_STATE)) {
                continue;
            }
            LoversLinkState state = LoversAddon.linkState(other);
            if (state.matchesTarget(targetUuid)
                    && validate(other) != null) {
                return true;
            }
        }
        return false;
    }
}
