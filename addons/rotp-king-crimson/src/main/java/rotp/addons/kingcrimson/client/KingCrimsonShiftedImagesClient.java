package rotp.addons.kingcrimson.client;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.network.TimeEraseShiftPayload;
import rotp.addons.kingcrimson.state.KingCrimsonShiftedImages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * The Time Erase user's view of the other players (owner 2026-11-07: "這裏紅王眼中呈現的就直接是偏移後的結果，不分本體還是虛像").
 * His client moves each such player's own entity to the image the server names, so the model, the name, the hitbox
 * and the crosshair's pick are all in that one place and nowhere else; what he is shown there is the red afterimage
 * with its halo, not the plain model. When the erase ends the server has put the player on that very spot, so nothing
 * on this screen moves; a player the end does not move is brought back to where he is a moment later.
 */
public final class KingCrimsonShiftedImagesClient {
    private static final int LERP_STEPS = 3;
    /** Ids of the afterimages drawn on shifted players: this client's own, below every id a server gives. */
    private static final int IMAGE_ID_BASE = -0x20000000;
    /** Client ticks before a released entity is put where the server last said: its own packet comes first. */
    private static final int RELEASE_TICKS = 3;
    private static final Int2IntMap RELEASED = new Int2IntOpenHashMap();

    private KingCrimsonShiftedImagesClient() {}

    public static void apply(TimeEraseShiftPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }
        Int2ObjectMap<Vec3> before = KingCrimsonShiftedImages.all();
        Int2ObjectMap<Vec3> now = new Int2ObjectOpenHashMap<>();
        for (TimeEraseShiftPayload.Image image : payload.images()) {
            now.put(image.entityId(), image.position());
        }
        KingCrimsonShiftedImages.replace(now);
        for (Int2ObjectMap.Entry<Vec3> image : now.int2ObjectEntrySet()) {
            RELEASED.remove(image.getIntKey());
            Entity entity = level.getEntity(image.getIntKey());
            if (!(entity instanceof LivingEntity living) || living == minecraft.player) {
                continue;
            }
            // TimeEraseShiftLerpMixin turns any position given for him into his image's.
            Vec3 to = image.getValue();
            living.lerpTo(to.x, to.y, to.z, living.lerpTargetYRot(), living.lerpTargetXRot(), LERP_STEPS);
            // The server names an image for a decoy only when it is another eraser's: it is drawn as its player is.
            if (living instanceof Player || living instanceof KingCrimsonTimeEraseDecoyEntity) {
                showAfterimage(level, living, minecraft.player);
            }
        }
        for (int released : before.keySet()) {
            if (!now.containsKey(released)) {
                removeAfterimage(level, released);
                RELEASED.put(released, RELEASE_TICKS);
            }
        }
    }

    /** Every client tick: an entity let go of is put where the server's own packets last said it is. */
    static void tick() {
        if (RELEASED.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            RELEASED.clear();
            return;
        }
        IntList due = new IntArrayList();
        for (Int2IntMap.Entry released : RELEASED.int2IntEntrySet()) {
            if (released.getIntValue() <= 1) {
                due.add(released.getIntKey());
            } else {
                released.setValue(released.getIntValue() - 1);
            }
        }
        for (int id : due) {
            RELEASED.remove(id);
            if (level.getEntity(id) instanceof LivingEntity living && living != minecraft.player) {
                Vec3 real = living.getPositionCodec().decode(0L, 0L, 0L);
                living.lerpTo(real.x, real.y, real.z, living.lerpTargetYRot(), living.lerpTargetXRot(), LERP_STEPS);
            }
        }
    }

    /** The plain model of a shifted player is not drawn: the eraser sees only his afterimage there. */
    static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (KingCrimsonShiftedImages.any()
                && KingCrimsonShiftedImages.imageOf(event.getEntity().getId()) != null
                && !KingCrimsonAfterimageRenderer.isRenderingAfterimage()
                && afterimageOf(event.getEntity()) != null) {
            event.setCanceled(true);
        }
    }

    static void clear() {
        KingCrimsonShiftedImages.clear();
        RELEASED.clear();
    }

    private static KingCrimsonAfterimageEntity afterimageOf(Entity origin) {
        return origin.level().getEntity(IMAGE_ID_BASE - origin.getId()) instanceof KingCrimsonAfterimageEntity image
                && image.followsOrigin() && !image.isRemoved()
                        ? image
                        : null;
    }

    private static void showAfterimage(ClientLevel level, LivingEntity origin, Player observer) {
        if (afterimageOf(origin) != null) {
            return;
        }
        KingCrimsonAfterimageEntity image = KingCrimsonAddon.KC_AFTERIMAGE_ENTITY.get().create(level);
        if (image == null) {
            return;
        }
        image.setId(IMAGE_ID_BASE - origin.getId());
        image.initializeFollowing(origin.getId(), origin.getUUID());
        image.showTimeEraseOutline();
        image.followErasePauseOf(observer);
        image.moveTo(origin.getX(), origin.getY(), origin.getZ(), origin.getYRot(), origin.getXRot());
        level.addEntity(image);
    }

    private static void removeAfterimage(ClientLevel level, int originId) {
        if (level.getEntity(IMAGE_ID_BASE - originId) instanceof KingCrimsonAfterimageEntity) {
            level.removeEntity(IMAGE_ID_BASE - originId, Entity.RemovalReason.DISCARDED);
        }
    }
}
