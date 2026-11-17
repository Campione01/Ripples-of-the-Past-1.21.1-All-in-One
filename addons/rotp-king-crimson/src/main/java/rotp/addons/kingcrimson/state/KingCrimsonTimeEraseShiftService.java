package rotp.addons.kingcrimson.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.network.TimeEraseShiftPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Owner 2026-11-07, the shifted image: "其他玩家在我們眼裏的位置就應該隨每次移動的矢量方向額外偏移真實玩家位置一點點，這樣我們時刪越久偏移
 * 越大...當我們結束時刪時他會被強制移動到我們時刪虛像的位置處...這個效果應該對整個世界全部玩家生效". For every other player of
 * the eraser's dimension the server keeps an image: his place plus a share of every move he makes during the erase.
 * The eraser's client alone is told where the images are; the other players are told nothing until the erase ends and
 * each is put on his image. Nothing of it is saved.
 * <p>
 * Owner 2026-11-11: "兩個紅王持有者的時間刪除互相獨立，雙方都無法察覺自身在誰的時刪裡". One who erases time himself is out of the
 * other's erase as he is out of the world's reach: he is neither shifted nor put anywhere. His decoy stands in the world
 * for him, so it is shifted and put on its image as he would be.
 * <p>
 * Owner 2026-11-11: "現在多人模式下，非玩家的其他生物在時刪中也按照運動漂移後的假身計算，單人模式普通生物邏輯不變", and on its cost:
 * "允許隨機抽固定數量的玩家周圍最近的生物做運動漂移，其他不變，因為歸根結底上我們的目的是製造玩家的錯愕感". With more than one
 * player online, an erase draws at its first tick, for each player of the level, the setting's number of creatures
 * among the nearest to him; those are shifted and put on their images as players are, and no other creature is.
 */
public final class KingCrimsonTimeEraseShiftService {
    private static final double STAND_NEAR_SQR = 6.0D * 6.0D;
    private static final double NO_SHIFT_SQR = 1.0E-8D;

    private KingCrimsonTimeEraseShiftService() {}

    /**
     * Null when the setting is off: the erase then shows and moves nobody, as before R772.1. The eraser is a player,
     * or a creature that carries King Crimson: every player of the level is another player to its erase, the only
     * one online too (owner 2026-11-11: "如果單人模式有非玩家生物發動時刪，玩家的邏輯按多人遊戲時的來").
     */
    @Nullable
    static Session begin(LivingEntity eraser) {
        return KingCrimsonConfig.timeEraseShiftsPlayers() && eraser.level() instanceof ServerLevel level
                ? new Session(level, KingCrimsonConfig.timeEraseShiftPerMove(), creaturesPerPlayer(level.getServer()))
                : null;
    }

    /**
     * How many creatures an erase that begins now shifts for each player: none with one player online, the test
     * Epitaph makes for its solo mode.
     */
    static int creaturesPerPlayer(MinecraftServer server) {
        return EpitaphSessions.isAlone(server) ? 0 : KingCrimsonConfig.timeEraseShiftCreatures();
    }

    /**
     * The meeting point with Epitaph's prediction (EV1105AC section 3; EPITAPH-ARCH section 4, T6): a player a
     * prediction holds is seen where he is and is not shifted until it lets him go. That is a player a prediction is
     * aimed at, and anyone who is walked along his track: his place is what was foreseen.
     */
    static boolean isHeldByPrediction(ServerPlayer player) {
        return EpitaphPredictions.isPredictedPlayer(player) || EpitaphPredictions.isForcedWalking(player);
    }

    /** Whether an erase leaves this body where it is: a prediction holds it, or it erases time itself. */
    static boolean isHeld(LivingEntity body) {
        if (KingCrimsonEraseReach.isOutOfReach(body)) {
            return true;
        }
        return body instanceof ServerPlayer player ? isHeldByPrediction(player) : EpitaphPredictions.isOnTrack(body);
    }

    /** Where the eraser sees this body now; its own place when no erase of the eraser shifts it. */
    public static Vec3 imageOf(ServerPlayer eraser, LivingEntity other) {
        Session session = KingCrimsonAddon.temporalState(eraser).shiftSession();
        return session != null ? session.imageOf(other) : other.position();
    }

    static final class Session {
        private final ServerLevel level;
        private final double perMove;
        private final int creaturesPerPlayer;
        private final Map<UUID, Shift> shifts = new HashMap<>();
        // The creatures this erase shifts, drawn once.
        private final List<Mob> creatures = new ArrayList<>();
        private boolean drew;
        private boolean toldSomething;
        private boolean paused;

        private Session(ServerLevel level, double perMove, int creaturesPerPlayer) {
            this.level = level;
            this.perMove = perMove;
            this.creaturesPerPlayer = creaturesPerPlayer;
        }

        /** Where this erase sees the body now; its own place when the erase does not shift it. */
        Vec3 imageOf(LivingEntity other) {
            Shift shift = shifts.get(other.getUUID());
            // One who is held is seen where he is now: his image is not kept from the tick before.
            return shift != null && shift.body == other && !isHeld(other) ? shift.seen() : other.position();
        }

        /**
         * The passive erase of a creature (owner 2026-11-11: "強制讓玩家做運動漂移"): every body this erase shifts is taken
         * to have gone on as it last moved for so many ticks more, all at once.
         */
        void driftAhead(int ticks) {
            for (Shift shift : shifts.values()) {
                if (!shift.isGone(level) && !shift.isCarriedOnly() && !isHeld(shift.body)) {
                    shift.pushAhead(level, perMove * ticks);
                }
            }
        }

        /** The eraser stood in stopped time: what the others did meanwhile shifts nothing. */
        void afterPause() {
            paused = true;
        }

        /** One tick of the eraser's own time: each other player's move of this tick goes into his image. */
        void tick(LivingEntity eraser) {
            if (eraser.level() != level) {
                return;
            }
            boolean counts = !paused;
            paused = false;
            if (!drew) {
                drew = true;
                draw(eraser);
            }
            for (ServerPlayer other : level.players()) {
                if (other == eraser || other.isSpectator() || !other.isAlive()) {
                    continue;
                }
                KingCrimsonTimeEraseDecoyEntity decoy = KingCrimsonTimeEraseProjectionService.decoyOf(other);
                if (decoy != null && !shifts.containsKey(decoy.getUUID())) {
                    // The decoy goes on from where its player was last seen: nothing jumps in the eraser's eyes.
                    Shift his = shifts.get(other.getUUID());
                    Shift its = new Shift(decoy);
                    if (his != null && his.body == other) {
                        its.startAhead(his.seen().subtract(his.lastPosition));
                    }
                    shifts.put(decoy.getUUID(), its);
                }
                follow(other, counts);
                if (decoy != null) {
                    follow(decoy, counts);
                }
            }
            for (Mob creature : creatures) {
                follow(creature, counts);
            }
            // Who logged out, died or left the dimension drops his shift.
            shifts.values().removeIf(shift -> shift.isGone(level));
            creatures.removeIf(creature -> !shifts.containsKey(creature.getUUID()));
            if (eraser instanceof ServerPlayer player) {
                tell(player);
            }
        }

        // For each player of the level, the eraser too: so many creatures among twice as many of the nearest to him.
        // Where a player looks is where the shift is seen, and where its end startles.
        private void draw(LivingEntity eraser) {
            if (creaturesPerPlayer <= 0) {
                return;
            }
            RandomSource random = RandomSource.create();
            Set<Mob> drawn = new LinkedHashSet<>();
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !player.isAlive()) {
                    continue;
                }
                List<Mob> nearest = level.getEntitiesOfClass(Mob.class,
                        player.getBoundingBox().inflate(KingCrimsonPhase5Spec.TIME_ERASE_SHIFT_CREATURE_RANGE),
                        mob -> canBeDrawn(mob, eraser));
                nearest.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
                List<Mob> pool = new ArrayList<>(nearest.subList(0, Math.min(nearest.size(), creaturesPerPlayer * 2)));
                for (int left = creaturesPerPlayer; left > 0 && !pool.isEmpty(); left--) {
                    drawn.add(pool.remove(random.nextInt(pool.size())));
                }
            }
            creatures.addAll(drawn);
        }

        // A decoy is no creature here: it stands for its player. A boss keeps its own place. The fake body of a
        // creature's erase is there for nobody.
        private static boolean canBeDrawn(Mob mob, LivingEntity eraser) {
            return mob.isAlive() && mob != eraser && !(mob instanceof KingCrimsonTimeEraseDecoyEntity)
                    && !mob.getType().is(Tags.EntityTypes.BOSSES) && !KingCrimsonEraseReach.isOutOfReach(mob);
        }

        private void follow(LivingEntity body, boolean counts) {
            Shift shift = shifts.get(body.getUUID());
            if (shift == null || shift.body != body) {
                // A player who came into the dimension, or came back from death, starts where he is.
                shift = new Shift(body);
                shifts.put(body.getUUID(), shift);
            }
            if (shift.isCarriedOnly() || isHeld(body)) {
                shift.hold();
            } else {
                shift.advance(level, perMove, counts);
            }
        }

        // The images within the afterimage radius, to the eraser alone.
        private void tell(ServerPlayer eraser) {
            double radius = KingCrimsonPhase5Spec.TIME_ERASE_AFTERIMAGE_RADIUS;
            List<TimeEraseShiftPayload.Image> images = new ArrayList<>();
            for (Shift shift : shifts.values()) {
                // One who erases is not there for the eraser: his client is told nothing of him.
                if (shift.body.distanceToSqr(eraser) > radius * radius || KingCrimsonEraseReach.isOutOfReach(shift.body)) {
                    continue;
                }
                Vec3 seen = shift.seen();
                images.add(new TimeEraseShiftPayload.Image(shift.body.getId(), seen));
                Vec3 offset = seen.subtract(shift.body.position());
                if (offset.lengthSqr() < NO_SHIFT_SQR) {
                    continue;
                }
                StandPower power = StandPower.get(shift.body);
                StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
                // His Stand near him is seen with him; one sent away stays where it is.
                if (stand != null && !stand.isManuallyControlled()
                        && stand.distanceToSqr(shift.body) <= STAND_NEAR_SQR) {
                    images.add(new TimeEraseShiftPayload.Image(stand.getId(), stand.position().add(offset)));
                }
            }
            if (images.isEmpty() && !toldSomething) {
                return;
            }
            toldSomething = !images.isEmpty();
            PacketDistributor.sendToPlayer(eraser, new TimeEraseShiftPayload(images));
        }

        /**
         * The erase is over, by whatever route: every shifted player is put on his image, the eraser's last sight of
         * him, and the eraser's client lets go of the images.
         */
        void finish(LivingEntity eraser) {
            for (Shift shift : shifts.values()) {
                shift.land(level, eraser);
            }
            shifts.clear();
            if (toldSomething && eraser instanceof ServerPlayer player && player.connection != null
                    && !player.hasDisconnected()) {
                toldSomething = false;
                PacketDistributor.sendToPlayer(player, new TimeEraseShiftPayload(List.of()));
            }
        }
    }

    private static final class Shift {
        private final LivingEntity body;
        private Vec3 lastPosition;
        // His move in the last tick that counted.
        private Vec3 lastStep = Vec3.ZERO;
        // The image's place: where its feet stand for a body on the ground, the body's own place in the air.
        private Vec3 image;
        private double aboveGround;
        private boolean grounded;

        private Shift(LivingEntity body) {
            this.body = body;
            hold();
        }

        private Vec3 seen() {
            return grounded ? image.add(0.0D, aboveGround, 0.0D) : image;
        }

        private boolean isGone(ServerLevel level) {
            return body.isRemoved() || !body.isAlive() || body.level() != level || body.isSpectator();
        }

        // A rider's moves are his mount's, and taking him off it would be its own trouble; a sleeper lies in his bed.
        // A creature that carries somebody would take him along, and one in water has no ground to be walked over.
        private boolean isCarriedOnly() {
            return body.isPassenger() || body.isSleeping()
                    || body instanceof Mob && !(body instanceof KingCrimsonTimeEraseDecoyEntity)
                            && (body.isVehicle() || body.isInWater() || body.isInLava());
        }

        /** He is seen where he is. */
        private void hold() {
            lastPosition = body.position();
            lastStep = Vec3.ZERO;
            image = lastPosition;
            aboveGround = 0.0D;
            grounded = false;
        }

        /** He is seen this far from where he is, from his first tick on. */
        private void startAhead(Vec3 offset) {
            image = lastPosition.add(offset);
        }

        private void advance(ServerLevel level, double perMove, boolean counts) {
            Vec3 position = body.position();
            Vec3 step = position.subtract(lastPosition);
            Vec3 offset = seen().subtract(lastPosition);
            lastPosition = position;
            double maxStep = KingCrimsonPhase5Spec.TIME_ERASE_SHIFT_MAX_STEP;
            // A teleport, a pearl or a leap is no move of his own, nor is what he did while the erase stood still: the
            // image goes with him as it is. Time stopped for him adds nothing either.
            boolean carried = step.lengthSqr() > maxStep * maxStep || !counts;
            boolean moved = !carried && !TimeStopState.shouldFreezeOnServer(body);
            lastStep = moved ? step : Vec3.ZERO;
            if (moved) {
                offset = offset.add(step.scale(perMove));
            }
            place(level, position, offset, carried);
        }

        /** He is taken to have gone on as he last moved, for so many shares of a move more. */
        private void pushAhead(ServerLevel level, double shares) {
            if (lastStep.lengthSqr() >= NO_SHIFT_SQR) {
                Vec3 position = body.position();
                place(level, position, seen().subtract(position).add(lastStep.scale(shares)), false);
            }
        }

        // His image this far from him: walked there over the ground, or moved there through free space.
        private void place(ServerLevel level, Vec3 position, Vec3 offset, boolean carried) {
            if (offset.lengthSqr() < NO_SHIFT_SQR) {
                image = position;
                aboveGround = 0.0D;
                grounded = false;
                return;
            }
            EntityDimensions size = body.getDimensions(body.getPose());
            double ground = isGroundBound()
                    ? KingCrimsonShiftWalk.groundBelow(level, body, size.makeBoundingBox(position))
                    : -1.0D;
            Vec3 target = position.add(offset);
            if (ground < 0.0D) {
                // In the air, in water, on a ladder: the image goes on in a straight line through free space.
                Vec3 from = grounded || carried ? position : image;
                Vec3 flown = KingCrimsonShiftWalk.fly(level, body, size, from, target);
                if (from != position && !KingCrimsonShiftWalk.isLineClear(level, body, size, position, flown)) {
                    flown = KingCrimsonShiftWalk.fly(level, body, size, position, target);
                }
                image = flown;
                aboveGround = 0.0D;
                grounded = false;
                return;
            }
            Vec3 standing = position.subtract(0.0D, ground, 0.0D);
            // The image walks on from where it stood, if that is still ground; else it sets out from him again.
            Vec3 from = standing;
            if (grounded && !carried) {
                double under = KingCrimsonShiftWalk.groundBelow(level, body, size.makeBoundingBox(image));
                if (under >= 0.0D) {
                    Vec3 settled = under > 0.0D ? image.subtract(0.0D, under, 0.0D) : image;
                    if (KingCrimsonShiftWalk.canStand(level, body, size, settled)) {
                        from = settled;
                    }
                }
            }
            Vec3 walked = KingCrimsonShiftWalk.walk(level, body, size, from, target.x, target.z);
            // Nothing solid may come between him and his image: then it is walked out from him, and stops at the wall.
            if (from != standing && !KingCrimsonShiftWalk.isLineClear(level, body, size, standing, walked)) {
                walked = KingCrimsonShiftWalk.walk(level, body, size, standing, target.x, target.z);
            }
            image = walked;
            // He is seen as high above the ground as he is, if there is room for it.
            aboveGround = ground > 0.0D
                    && level.noCollision(body, size.makeBoundingBox(image.add(0.0D, ground, 0.0D)))
                            ? ground
                            : 0.0D;
            grounded = true;
        }

        private boolean isGroundBound() {
            return !(body instanceof Player player && player.getAbilities().flying) && !body.isFallFlying()
                    && !body.isInWater() && !body.isInLava() && !body.onClimbable();
        }

        private void land(ServerLevel level, LivingEntity eraser) {
            if (isGone(level) || isCarriedOnly() || isHeld(body)) {
                return;
            }
            // A witness who keeps his place (Gold Experience Requiem's holder) was seen shifted and is not moved.
            if (body instanceof ServerPlayer player
                    && (player.connection == null || KingCrimsonEraseWitnesses.keepsPlace(player, eraser))) {
                return;
            }
            Vec3 to = seen();
            if (to.distanceToSqr(body.position()) < NO_SHIFT_SQR) {
                return;
            }
            EntityDimensions size = body.getDimensions(body.getPose());
            if (!KingCrimsonShiftWalk.canStand(level, body, size, to)) {
                to = image;
                if (!KingCrimsonShiftWalk.canStand(level, body, size, to)) {
                    return;
                }
            }
            if (body instanceof ServerPlayer player) {
                // A move relative to where he is: his client keeps his motion, his rotation and his keys.
                player.connection.teleport(to.x, to.y, to.z, player.getYRot(), player.getXRot(),
                        EnumSet.allOf(RelativeMovement.class));
            } else {
                body.moveTo(to.x, to.y, to.z, body.getYRot(), body.getXRot());
                if (body instanceof Mob mob) {
                    // Its way was planned from where it stood.
                    mob.getNavigation().recomputePath();
                }
            }
            // Into stopped time he freezes where he lands, as after a Time Skip.
            TimeStopState.repinOrReleaseOnServer(body);
        }
    }
}
