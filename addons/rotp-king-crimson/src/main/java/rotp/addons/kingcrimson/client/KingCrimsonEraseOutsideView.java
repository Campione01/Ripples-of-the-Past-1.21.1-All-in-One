package rotp.addons.kingcrimson.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.init.ModParticles;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;
import rotp.addons.kingcrimson.state.KingCrimsonEraseOutsideRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The erasing user's sight of what moves and of the skills at work outside his erase (EV1105AC section 7), on his
 * client alone and only while his erase runs.
 * <ul>
 * <li><b>What moves</b> (projectiles, falling blocks, primed TNT, a Stand's and a mob's skill entities) is carried by
 * the path the creatures' afterimages already use: each gets a dark red afterimage of its own model that trails it by
 * two ticks, with the red outline, or the halo under a shader pack. The afterimages are this client's own entities;
 * the server sends nothing for them.</li>
 * <li><b>Skill effects</b> are particles. The erase used to draw none; now it draws those that appear by a creature
 * or by a moving thing of the outside world, in the afterimages' dark red, and no more than a tick's allowance of
 * them. The world's own dust (weather, lava, portals, the eraser's own steps) stays out of the void.</li>
 * </ul>
 * Thrown blood (Blood Throw) is let through as it is.
 */
@OnlyIn(Dist.CLIENT)
public final class KingCrimsonEraseOutsideView {
    /** Ids of the afterimages of moving things: this client's own, below the shifted players' images. */
    private static final int IMAGE_ID_BASE = -0x30000000;
    private static final List<AABB> SOURCES = new ArrayList<>();
    private static final KingCrimsonEraseOutsideRules.Budget BUDGET = new KingCrimsonEraseOutsideRules.Budget();
    private static boolean active;
    @Nullable
    private static ParticleOptions creating;

    private KingCrimsonEraseOutsideView() {}

    /** Every client tick. */
    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        creating = null;
        if (level == null || player == null || !KingCrimsonTemporalClientState.localTimeErase()) {
            // The afterimages go by themselves when their erase is over.
            clear();
            return;
        }
        active = true;
        BUDGET.refill();
        SOURCES.clear();
        double radius = KingCrimsonPhase5Spec.TIME_ERASE_AFTERIMAGE_RADIUS;
        int moving = 0;
        List<Entity> unseen = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof KingCrimsonAfterimageEntity image) {
                if (image.isMovingThing()) {
                    moving++;
                }
                continue;
            }
            if (entity.distanceToSqr(player) > radius * radius) {
                continue;
            }
            if (KingCrimsonEraseOutsideRules.isEffectSource(entity, player)) {
                SOURCES.add(entity.getBoundingBox().inflate(KingCrimsonEraseOutsideRules.PARTICLE_REACH));
            }
            if (KingCrimsonEraseOutsideRules.isShownMoving(entity, player)
                    && !(level.getEntity(IMAGE_ID_BASE - entity.getId()) instanceof KingCrimsonAfterimageEntity)) {
                unseen.add(entity);
            }
        }
        for (Entity entity : unseen) {
            if (moving >= KingCrimsonEraseOutsideRules.MAX_MOVING) {
                break;
            }
            show(level, entity, player);
            moving++;
        }
    }

    static void clear() {
        active = false;
        creating = null;
        SOURCES.clear();
    }

    /** The kind of particle the client is making now, or null when it is done making it. */
    public static void creating(@Nullable ParticleOptions kind) {
        creating = kind;
    }

    /**
     * Whether a particle the client is about to add may be: all of them outside an erase of the local player; inside
     * one, those by something of the outside world, within the tick's allowance, and they turn the afterimages' red.
     */
    public static boolean admits(Particle particle) {
        if (!active) {
            return true;
        }
        ParticleOptions kind = creating;
        if (kind != null && kind.getType() == ModParticles.BLOOD.get()) {
            return true;
        }
        if (!BUDGET.hasLeft()) {
            return false;
        }
        Vec3 at = particle.getPos();
        for (AABB source : SOURCES) {
            if (source.contains(at)) {
                BUDGET.take();
                particle.setColor(0.5F, 0.0F, 0.0F);
                return true;
            }
        }
        return false;
    }

    private static void show(ClientLevel level, Entity origin, LocalPlayer observer) {
        KingCrimsonAfterimageEntity image = KingCrimsonAddon.KC_AFTERIMAGE_ENTITY.get().create(level);
        if (image == null) {
            return;
        }
        image.setId(IMAGE_ID_BASE - origin.getId());
        image.initializeMoving(origin.getId(), origin.getUUID(), KingCrimsonEraseOutsideRules.MOVING_DELAY_TICKS);
        image.showTimeEraseOutline();
        image.followErasePauseOf(observer);
        image.moveTo(origin.getX(), origin.getY(), origin.getZ(), origin.getYRot(), origin.getXRot());
        level.addEntity(image);
    }
}
