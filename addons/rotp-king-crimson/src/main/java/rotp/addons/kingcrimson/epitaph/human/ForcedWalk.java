package rotp.addons.kingcrimson.epitaph.human;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeEraseAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeEraseStepAbility;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.mixin.epitaph.GameListenerStateAccessor;
import rotp.addons.kingcrimson.network.EpitaphForcedWalkPayload;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;

/**
 * A real player on a foreseen track (EPITAPH-ARCH 3.4). He cannot be held as a creature is, by not ticking: his body is
 * ticked from his connection and lives on (hunger, air, effects). So he is put where his track has him before each
 * tick of the level, which his connection then takes for his place; what his client sends of moves, hands and
 * inventory is dropped (GamePacketGateMixin), his abilities are refused but the erase that frees him, and he picks
 * nothing up, so that what he carries changes only as it was foreseen. A blow does not knock him off his track.
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class ForcedWalk {
    /** On a client: whether its own player is being walked. Set by the client, read where both sides ask. */
    public static boolean localPlayerWalked;
    /** On a client: whether its own player is held to his track in the slow phase of his own prediction. */
    public static boolean localPlayerSlowed;

    private final ServerPlayer player;
    private boolean begun;
    private boolean ended;

    public ForcedWalk(ServerPlayer player) {
        this.player = player;
    }

    /** One teleport to where he stands, so that his client starts from the place his body has, and the word to it. */
    public void begin() {
        if (begun || ended || !reachable()) {
            return;
        }
        begun = true;
        snap();
        tell(EpitaphForcedWalkPayload.Kind.BEGIN);
    }

    /** Before the level ticks: his body where the track has it, turned as his phantom was. */
    public void place(Vec3 to, float yRot, float xRot, float yHeadRot, boolean onGround) {
        player.absMoveTo(to.x, to.y, to.z, yRot, xRot);
        player.setYHeadRot(yHeadRot);
        player.setOnGround(onGround);
        player.resetFallDistance();
        player.serverLevel().getChunkSource().move(player);
        if (reachable()) {
            // His client last said where it stood before the walk began: in mid-air it would be kicked for floating.
            ((GameListenerStateAccessor) player.connection).kingCrimson$setClientIsFloating(false);
            tell(EpitaphForcedWalkPayload.Kind.STEP);
        }
    }

    /** What his phantom was doing with its legs at this tick: he is seen to do the same. */
    public void pose(boolean crouching, boolean sprinting, boolean swimming) {
        player.setShiftKeyDown(crouching);
        player.setSprinting(sprinting);
        player.setSwimming(swimming);
    }

    /** Control back: one teleport to where he stands, and what his client did with keys that did nothing is put right. */
    public void end() {
        if (ended) {
            return;
        }
        ended = true;
        if (!begun || !reachable() || player.isRemoved()) {
            return;
        }
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        player.setSwimming(false);
        snap();
        tell(EpitaphForcedWalkPayload.Kind.RELEASE);
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        player.inventoryMenu.sendAllDataToRemote();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.sendAllDataToRemote();
        }
    }

    private boolean reachable() {
        return player.connection != null && !player.hasDisconnected();
    }

    // The camera is his: the turn is sent as no turn at all.
    private void snap() {
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                EnumSet.of(RelativeMovement.Y_ROT, RelativeMovement.X_ROT));
    }

    private void tell(EpitaphForcedWalkPayload.Kind kind) {
        PacketDistributor.sendToPlayer(player, new EpitaphForcedWalkPayload(kind, player.getX(), player.getY(), player.getZ()));
    }

    /**
     * The answer to the core for an ability of a walked player, on the server and on his own client: refused, but the
     * erase that frees him, whoever foresaw; and, for the one who foresees, the step when the server is set to let it free
     * him (EPITAPH-ARCH 3.4, section 4 T8; the decision of the owner of 2026-11-11).
     */
    @Nullable
    public static ConditionCheck refuse(Ability ability, Power<?> context, LivingEntity user) {
        boolean client = user.level().isClientSide();
        boolean walked = client ? localPlayerWalked && user instanceof Player player && player.isLocalPlayer()
                : EpitaphSessions.isWalked(user);
        if (!walked) {
            return null;
        }
        // The way out is for the one who foresees: a player his prediction is aimed at has none. Who walks whom is known to
        // the server alone: a client lets the press through, and the server answers it.
        boolean own = client || EpitaphSessions.walksItsUser(user);
        if (ability instanceof KingCrimsonTimeEraseAbility) {
            // The erase frees whoever is walked and holds King Crimson, the one a prediction is aimed at too (the owner).
            return null;
        }
        if (ability instanceof KingCrimsonTimeEraseStepAbility) {
            // The setting is the server's as well.
            return client || own && KingCrimsonConfig.epitaphStepFreesUser() ? null : ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.NEGATIVE;
    }

    /** Whether what the client of this player sends of moves and hands is dropped now. */
    public static boolean drops(@Nullable ServerPlayer sender) {
        return EpitaphSessions.isWalked(sender);
    }

    /**
     * A click of a walked player was dropped. His client took it for done: it is told that the click was seen, and
     * what the blocks it touched really are.
     */
    public static void answerDroppedClick(ServerPlayer sender, int sequence, @Nullable BlockPos pos, @Nullable Direction face) {
        if (sequence >= 0) {
            sender.connection.ackBlockChangesUpTo(sequence);
        }
        if (pos != null && sender.serverLevel().isLoaded(pos)) {
            sender.connection.send(new ClientboundBlockUpdatePacket(sender.serverLevel(), pos));
            if (face != null) {
                sender.connection.send(new ClientboundBlockUpdatePacket(sender.serverLevel(), pos.relative(face)));
            }
        }
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (EpitaphSessions.any && EpitaphSessions.isWalked(event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onExperiencePickup(PlayerXpEvent.PickupXp event) {
        if (EpitaphSessions.any && EpitaphSessions.isWalked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        Entity struck = event.getEntity();
        if (EpitaphSessions.any && EpitaphSessions.isWalked(struck)) {
            event.setCanceled(true);
        }
    }
}
