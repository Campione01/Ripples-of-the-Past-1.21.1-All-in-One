package rotp.addons.highwaystar.gametest;

import static rotp.addons.highwaystar.gametest.RoomTestScene.ROOM;
import static rotp.addons.highwaystar.gametest.RoomTestScene.entry;
import static rotp.addons.highwaystar.gametest.RoomTestScene.move;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.HighwayStarRoomConfig;
import rotp.addons.highwaystar.entity.HighwayStarRoomEntity;
import rotp.addons.highwaystar.room.HighwayStarRoomLure.Kind;
import rotp.addons.highwaystar.room.HighwayStarRoomService;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder(HighwayStarAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HighwayStarRoomGameTests {
    private static final String NAMESPACE = HighwayStarAddon.MOD_ID;

    private HighwayStarRoomGameTests() {}

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
    public static void roomPlacementRequiresSideRangeAndSpace(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer owner = s.owner();
            s.reached("placement");
            var room = s.cast(owner, 0);
            BlockPos wall = room.getDoorBlockPos();
            s.check(room.getFacing() == Direction.WEST && wall.equals(helper.absolutePos(new BlockPos(2, 6, 2))),
                    "HS_ROOM_PLACEMENT: Room is not on the aimed west face");
            s.check(s.level.getBlockState(wall).is(Blocks.STONE) && s.level.getBlockState(wall.west()).isAir()
                            && s.level.getBlockState(wall.west().above()).isAir(),
                    "HS_ROOM_PLACEMENT: placing an illusion changed world blocks");
            var power = StandPower.get(owner);
            power.setAbilityCooldown(ROOM, 0);
            float stamina = power.getStamina();
            var lease = HighwayStarRoomService.getRoomRef(owner);

            owner.moveTo(wall.getX() + 0.5, wall.getY() + 3.0, wall.getZ() + 0.5, 0, 90);
            s.check(!s.click(owner, ROOM), "HS_ROOM_PLACEMENT: top face admitted");
            owner.moveTo(wall.getX() + 0.5, wall.getY() - owner.getEyeHeight() - 2, wall.getZ() + 0.5, 0, -90);
            s.check(!s.click(owner, ROOM), "HS_ROOM_PLACEMENT: bottom face admitted");

            s.aim(owner, wall, 3);
            for (BlockPos front : new BlockPos[] { wall.west(), wall.west().above() }) {
                // Structure void is non-air but leaves the collider ray to the actual wall unobstructed.
                s.setBlock(front, Blocks.STRUCTURE_VOID);
                s.check(!s.click(owner, ROOM), "HS_ROOM_PLACEMENT: occupied front block admitted: " + front);
                s.setBlock(front, Blocks.AIR);
            }
            BlockPos distantWall = s.wall(7);
            for (int chunk = (distantWall.getX() - 33) >> 4; chunk <= distantWall.getX() >> 4; chunk++) {
                s.level.getChunk(chunk, distantWall.getZ() >> 4);
            }
            s.aim(owner, distantWall, 31.99);
            var inRange = HighwayStarRoomService.findPlacement(owner);
            s.check(inRange != null && inRange.wallPos().equals(distantWall),
                    "HS_ROOM_PLACEMENT: a clear 31.99 block ray did not reach the wall");
            s.aim(owner, distantWall, 32.25);
            s.check(!s.click(owner, ROOM), "HS_ROOM_PLACEMENT: a wall beyond 32 blocks admitted");
            s.check(lease.equals(HighwayStarRoomService.getRoomRef(owner)) && !room.isRemoved()
                            && power.getStamina() == stamina && power.getAbilityCooldown(ROOM) == 0,
                    "HS_ROOM_PLACEMENT: a rejected placement changed the live room, stamina or cooldown");
            s.finish();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
    public static void roomCreationPaysOnlyOnSuccess(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer owner = s.owner();
            StandPower power = StandPower.get(owner);
            BlockPos wall = s.wall(0);
            s.aim(owner, wall, 3);
            s.check(power.getMaxStamina() > 300, "fixture: Survival max stamina cannot distinguish a 300 debit");
            s.reached("creation_cost");
            for (float stamina : new float[] { power.getMaxStamina(), 299, 0 }) {
                power.setStamina(stamina);
                power.setAbilityCooldown(ROOM, 0);
                s.check(s.click(owner, ROOM), "HS_ROOM_COST: valid placement refused at stamina " + stamina);
                var room = s.placedRoom(owner);
                s.check(room != null, "HS_ROOM_COST: no Room at stamina " + stamina);
                s.check(power.getStamina() == Math.max(0, stamina - 300),
                        "HS_ROOM_COST: incorrect paid stamina from " + stamina + ": " + power.getStamina());
                s.check(power.getAbilityCooldown(ROOM) == 600 && power.getAbilityCooldownTotal(ROOM) == 600,
                        "HS_ROOM_COST: successful creation did not start exactly 600 cooldown ticks");
                room.discard();
            }
            power.setStamina(power.getMaxStamina());
            power.setAbilityCooldown(ROOM, 200);
            s.check(!s.click(owner, ROOM) && s.placedRoom(owner) == null && power.getAbilityCooldown(ROOM) == 200
                            && power.getStamina() == power.getMaxStamina(),
                    "HS_ROOM_COST: creation on cooldown spawned, debited or refreshed");

            var mob = s.spawn(EntityType.PIG, s.parked());
            var mobPower = s.grant(mob);
            mobPower.setStamina(mobPower.getMaxStamina());
            s.aim(mob, wall, 3);
            s.check(!s.click(mob, ROOM), "HS_ROOM_COST: fixed-cost Room was admitted for a non-player Stand user");
            s.finish();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
    public static void roomReplacementAndShiftAreOwnerScoped(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer owner = s.owner();
            var first = s.cast(owner, 0);
            ServerPlayer otherOwner = s.owner();
            var other = s.cast(otherOwner, 3);
            s.reached("replacement_shift");
            var replacement = s.cast(owner, 1);
            s.check(!first.getUUID().equals(replacement.getUUID()) && first.isRemoved()
                            && s.level.getEntity(first.getUUID()) == null && isCurrent(owner, replacement),
                    "HS_ROOM_REPLACEMENT: second cast did not replace exactly the old Room and its token");
            var power = StandPower.get(owner);
            var shift = s.shiftBinding(power);
            s.check(shift != null, "HS_ROOM_SHIFT: no SHIFT click in the registered Room slot");
            power.setStamina(21);
            int cooldown = power.getAbilityCooldown(ROOM);
            owner.setShiftKeyDown(true);
            s.check(cooldown == 600 && s.click(owner, shift), "HS_ROOM_SHIFT: remover refused during creation cooldown");
            s.check(replacement.isRemoved() && HighwayStarRoomService.getRoomRef(owner) == null,
                    "HS_ROOM_SHIFT: owner's current Room was not removed");
            s.click(owner, shift);
            s.check(power.getStamina() == 21 && power.getAbilityCooldown(ROOM) == cooldown
                            && power.getAbilityCooldown(shift) == 0 && isCurrent(otherOwner, other),
                    "HS_ROOM_SHIFT: removal charged/refreshed or affected another owner");
            s.finish();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 85)
    public static void roomMobEntryMarksScentAndSpawnsSolesOnce(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer owner = s.owner();
            var room = s.cast(owner, 0);
            var first = s.spawn(EntityType.PIG, entry(room, 0.55));
            var second = s.spawn(EntityType.PIG, s.parked());
            s.check(room.getBoundingBox().intersects(first.getBoundingBox()) && !first.isInWall(),
                    "fixture: the first pig must touch the illusion without suffocating in the actual wall");

            ServerPlayer luring = s.owner();
            BlockPos wall = s.wall(2);
            s.setBlock(wall.east(), Blocks.AIR);
            s.setBlock(wall.east().above(), Blocks.AIR);
            for (int x = 0; x <= 4; x++) {
                for (int z = -1; z <= 1; z++) {
                    s.setBlock(wall.offset(x, -1, z), Blocks.STONE);
                }
            }
            luring.moveTo(wall.getX() + 4, wall.getY() + 0.5 - luring.getEyeHeight(), wall.getZ() + 0.5, 90, 0);
            s.check(s.click(luring, ROOM), "fixture: east-facing lure Room press was refused");
            var lureRoom = s.placedRoom(luring);
            s.check(lureRoom != null && lureRoom.getFacing() == Direction.EAST,
                    "fixture: the lure Room was not placed on the east face");
            var lured = s.spawn(EntityType.PIG, new Vec3(wall.getX() + 1.9, wall.getY(), wall.getZ() + 0.5));
            lured.removeFreeWill();
            lured.setNoAi(false);
            lured.setNoGravity(false);
            lured.setOnGround(true);
            s.check(lured.blockPosition().equals(wall.east()) && !lured.getBoundingBox().intersects(lureRoom.getBoundingBox()),
                    "fixture: the goal-free pig must start in the approach block but outside the entry box");

            s.reached("mob_entry");
            s.after(1, () -> pendingAndScent(s, owner, room, first, "HS_ROOM_MOB_ENTRY"));
            s.after(2, () -> {
                move(second, entry(room, 0.55).add(0, 0, 0.92));
                s.check(room.getBoundingBox().intersects(second.getBoundingBox()) && !second.isInWall(),
                        "fixture: the second pig did not touch the consumed door from the side");
            });
            List<String> steps = new ArrayList<>();
            for (int tick : new int[] { 1, 2, 10, 11, 12, 19 }) {
                s.after(tick, () -> steps.add("t" + tick + " pos=" + lured.position().subtract(lureRoom.position())
                        + " motion=" + lured.getDeltaMovement() + " ground=" + lured.onGround()
                        + " navigationDone=" + lured.getNavigation().isDone() + " speed=" + lured.getSpeed()
                        + " wanted=" + lured.getMoveControl().hasWanted() + " mobTicks=" + lured.tickCount));
            }
            s.after(20, () -> pendingAndScent(s, luring, lureRoom, lured, "HS_ROOM_MOB_LURE_FINAL_STEP " + steps));
            s.after(69, () -> {
                s.check(room.isRemoved() && s.soles(owner, room).size() == 8 && s.attached(owner, first) == 8,
                        "HS_ROOM_MOB_ENTRY: expected one burst of eight soles attached to the first entrant");
                s.check(!s.scent(owner).getTargets().contains(second.getUUID()) && s.attached(owner, second) == 0,
                        "HS_ROOM_MOB_ENTRY: consumed Room also triggered for a second entrant");
                s.finish();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
    public static void roomRejectsOwnerStandSolesAndOwnedCreatures(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer owner = s.owner();
            var room = s.cast(owner, 0);
            var power = StandPower.get(owner);
            s.check(HighwayStarAddon.HIGHWAY_STAR.get().summon(owner, power), "fixture: owner Stand could not summon");
            var stand = power.getSummonedStandEntity();
            s.check(stand != null, "fixture: summoned Stand is missing");
            var sole = s.spawn(HighwayStarAddon.SOLE_ENTITY.get(), s.parked());
            sole.setOwnerUUID(owner.getUUID());
            var wolf = s.spawn(EntityType.WOLF, s.parked());
            wolf.setOwnerUUID(owner.getUUID());
            s.reached("entry_exclusions");
            for (LivingEntity excluded : new LivingEntity[] { owner, stand, sole, wolf }) {
                move(excluded, entry(room));
                s.check(excluded.getBoundingBox().intersects(room.getBoundingBox()),
                        "fixture: exclusion candidate did not overlap the real door: " + excluded.getType());
                // Tick the real entity now so the summoned Stand cannot follow its owner out of the volume first.
                room.tick();
                s.check(!room.isConsumed() && !room.isRemoved() && s.scent(owner).getTargets().isEmpty()
                                && !excluded.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                        "HS_ROOM_EXCLUSIONS: Room triggered for " + excluded.getType());
                move(excluded, s.parked());
            }
            var neutral = s.spawn(EntityType.PIG, entry(room));
            room.tick();
            pendingAndScent(s, owner, room, neutral, "HS_ROOM_EXCLUSIONS_CONTROL");
            s.finish();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 85)
    public static void roomPlayerViewLastsSixtyTicksThenHandsOff(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer owner = s.owner();
            var viewer = s.player();
            var room = s.cast(owner, 0);
            move(viewer.player, entry(room));
            s.reached("view_duration");
            s.after(1, () -> {
                pendingAndScent(s, owner, room, viewer.player, "HS_ROOM_VIEW_START");
                var starts = viewer.starts(room);
                s.check(starts.size() == 1 && starts.getFirst().durationTicks() == 60
                                && starts.getFirst().dimension().equals(s.level.dimension().location())
                                && starts.getFirst().doorBlockPos().equals(room.getDoorBlockPos()),
                        "HS_ROOM_VIEW_START: server did not send exactly one matching 60-tick view start");
                var slow = viewer.player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                s.check(slow != null && slow.getDuration() >= 59 && slow.getDuration() <= 60,
                        "HS_ROOM_VIEW_START: Slowness did not begin with the 60-tick view");
            });
            s.after(60, () -> s.check(room.hasPendingVictim(viewer.player.getUUID()) && viewer.ends(room) == 0
                            && s.soles(owner, room).isEmpty(),
                    "HS_ROOM_VIEW_DURATION: view or attack ended before 60 elapsed ticks"));
            s.after(61, () -> {
                s.check(room.isRemoved() && viewer.ends(room) == 1 && s.soles(owner, room).size() == 8,
                        "HS_ROOM_VIEW_DURATION: expected one end and eight soles at 60 elapsed ticks");
                s.finish();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 100)
    public static void roomPlayerDamageOrEscapeEndsViewEarly(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer hurtOwner = s.owner();
            var hurtRoom = s.cast(hurtOwner, 0);
            ServerPlayer escapeOwner = s.owner();
            var escapeRoom = s.cast(escapeOwner, 2);
            var hurt = s.player();
            var escape = s.player();
            var attacker = s.spawn(EntityType.PIG, s.parked().add(0, 0, 2));
            s.reached("view_interruptions");
            // Real ServerPlayers have a 60-tick login damage shield.
            s.after(63, () -> {
                move(hurt.player, entry(hurtRoom));
                move(escape.player, entry(escapeRoom));
            });
            s.after(64, () -> {
                pendingAndScent(s, hurtOwner, hurtRoom, hurt.player, "HS_ROOM_DAMAGE_SETUP");
                pendingAndScent(s, escapeOwner, escapeRoom, escape.player, "HS_ROOM_ESCAPE_SETUP");
                move(escape.player, escapeRoom.position().add(-2.99, 0, 0));
            });
            s.after(65, () -> {
                s.check(escapeRoom.hasPendingVictim(escape.player.getUUID()),
                        "HS_ROOM_ESCAPE: a position still within three blocks ended the view");
                move(escape.player, escapeRoom.position().add(-3.5, 0, 0));
                float before = hurt.player.getHealth();
                boolean accepted = hurt.player.hurt(s.level.damageSources().mobAttack(attacker), 2);
                hurt.player.setDeltaMovement(Vec3.ZERO);
                s.check(accepted && hurt.player.getHealth() < before && hurt.player.distanceToSqr(hurtRoom) <= 9,
                        "fixture: damage was not applied to the victim inside the door radius");
            });
            s.after(66, () -> {
                assertEarlyEnd(s, hurtOwner, hurtRoom, hurt, "HS_ROOM_DAMAGE");
                assertEarlyEnd(s, escapeOwner, escapeRoom, escape, "HS_ROOM_ESCAPE");
                s.finish();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
    public static void roomScentSurvivesFailedLaterAttack(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer owner = s.owner();
            var viewer = s.player();
            var room = s.cast(owner, 0);
            move(viewer.player, entry(room));
            var victimId = viewer.player.getUUID();
            s.reached("scent_survives");
            s.after(1, () -> {
                pendingAndScent(s, owner, room, viewer.player, "HS_ROOM_SCENT_COMMIT");
                s.logout(viewer.player);
                s.check(s.level.getEntity(victimId) == null, "fixture: victim did not really leave the level");
            });
            s.after(3, () -> {
                s.check(room.isRemoved() && s.soles(owner, room).isEmpty(),
                        "HS_ROOM_SCENT_FAILURE: invalid later victim did not cancel the burst");
                s.check(s.scent(owner).getTargets().contains(victimId)
                                && victimId.equals(s.scent(owner).getSelectedTrackingTarget()),
                        "HS_ROOM_SCENT_FAILURE: failed later attack removed the acquired scent");
                s.finish();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 85)
    public static void roomSurvivesRecallAndAttacksWithoutStand(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer owner = s.owner();
            var power = StandPower.get(owner);
            var type = HighwayStarAddon.HIGHWAY_STAR.get();
            s.check(type.summon(owner, power) && power.isSummoned(), "fixture: owner Stand could not summon");
            var room = s.cast(owner, 0);
            s.reached("recall");
            type.forceUnsummon(owner, power);
            s.check(!power.isSummoned() && HighwayStarRoomService.hasHighwayStar(owner),
                    "fixture: recall did not leave an unmanifested Highway Star power");
            var victim = s.spawn(EntityType.PIG, entry(room, 0.55));
            s.check(room.getBoundingBox().intersects(victim.getBoundingBox()) && !victim.isInWall(),
                    "fixture: the recalled Stand's victim must touch the illusion without suffocating");
            s.after(1, () -> {
                s.check(isCurrent(owner, room), "HS_ROOM_RECALL: recalling the Stand invalidated the room token");
                pendingAndScent(s, owner, room, victim, "HS_ROOM_RECALL_ENTRY");
            });
            s.after(60, () -> s.check(room.hasPendingVictim(victim.getUUID()) && s.soles(owner, room).isEmpty(),
                    "HS_ROOM_RECALL: recalled Stand released early or lost its pending victim; health=" + victim.getHealth()));
            s.after(69, () -> {
                int soles = s.soles(owner, room).size();
                long attached = s.attached(owner, victim);
                s.check(!power.isSummoned() && room.isRemoved() && soles == 8 && attached == 8,
                        "HS_ROOM_RECALL: expected eight attached soles with Stand recalled; summoned=" + power.isSummoned()
                                + " removed=" + room.isRemoved() + " soles=" + soles + " attached=" + attached
                                + " victimHealth=" + victim.getHealth());
                s.finish();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
    public static void roomInvalidatesOnOwnerDeathLogoutLevelChangeOrStandLoss(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            var controlOwner = s.owner();
            var control = s.cast(controlOwner, 0);
            s.reached("owner_invalidation");

            var dying = s.owner();
            var deathRoom = s.cast(dying, 1);
            dying.hurt(s.level.damageSources().genericKill(), Float.MAX_VALUE);
            s.check(!dying.isAlive(), "fixture: lethal damage did not kill the owner");
            assertInvalid(s, dying, deathRoom, "HS_ROOM_OWNER_DEATH");
            s.logout(dying);

            var leaving = s.owner();
            var logoutRoom = s.cast(leaving, 1);
            s.logout(leaving);
            assertInvalid(s, leaving, logoutRoom, "HS_ROOM_OWNER_LOGOUT");

            var traveler = s.owner();
            var changeRoom = s.cast(traveler, 1);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(traveler, s.level.dimension(),
                    s.level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER));
            assertInvalid(s, traveler, changeRoom, "HS_ROOM_OWNER_DIMENSION_EVENT");
            s.logout(traveler);

            var losesStand = s.owner();
            var lostStandRoom = s.cast(losesStand, 1);
            StandPower.get(losesStand).setStand(null);
            s.check(!HighwayStarRoomService.hasHighwayStar(losesStand), "fixture: Stand loss did not take effect");
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(losesStand));
            assertInvalid(s, losesStand, lostStandRoom, "HS_ROOM_OWNER_STAND_LOSS");
            s.check(isCurrent(controlOwner, control), "HS_ROOM_OWNER_SCOPE: another owner's Room was invalidated");
            s.finish();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
    public static void roomSingleHitAndDefaultTenMinuteExpiry(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer owner = s.owner();
            var room = s.cast(owner, 0);
            var hitter = s.spawn(EntityType.PIG, s.parked());
            s.reached("hit_expiry");
            s.check(room.hurt(s.level.damageSources().mobAttack(hitter), 1), "HS_ROOM_HIT: one valid hit refused");
            assertInvalid(s, owner, room, "HS_ROOM_HIT");
            s.check(!HighwayStarRoomConfig.isExpired(11_999) && HighwayStarRoomConfig.isExpired(12_000),
                    "HS_ROOM_EXPIRY_DEFAULT: default threshold is not 12000 ticks, enabled");
            s.check(HighwayStarRoomConfig.SPEC.isLoaded(), "fixture: Room server config was not loaded");

            // Use a short live threshold and the saved age, without changing the shared world clock.
            int configured = HighwayStarRoomConfig.EXPIRY_TICKS.get();
            try {
                HighwayStarRoomConfig.EXPIRY_TICKS.set(2);
                var fresh = s.cast(owner, 0);
                var tag = s.save(fresh);
                fresh.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                tag.putLong("CreatedAt", s.level.getGameTime() - 1);
                var justBeforeExpiry = s.load(tag);
                justBeforeExpiry.tick();
                s.check(isCurrent(owner, justBeforeExpiry), "HS_ROOM_EXPIRY_LIVE: Room expired before threshold");
                justBeforeExpiry.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                tag.putLong("CreatedAt", s.level.getGameTime() - 2);
                var atExpiry = s.load(tag);
                atExpiry.tick();
                assertInvalid(s, owner, atExpiry, "HS_ROOM_EXPIRY_LIVE");
            }
            finally {
                HighwayStarRoomConfig.EXPIRY_TICKS.set(configured);
            }
            s.finish();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
    public static void roomReloadRejectsDeadOrStaleOwner(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer owner = s.owner();
            var original = s.cast(owner, 0);
            var oldTag = s.save(original);
            s.reached("reload_owner_token");
            original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var live = s.load(oldTag);
            live.tick();
            s.check(isCurrent(owner, live) && live.getDoorBlockPos().equals(original.getDoorBlockPos())
                            && live.getFacing() == original.getFacing(),
                    "HS_ROOM_RELOAD_LIVE: matching live Room failed to survive save/load");

            var newer = s.cast(owner, 1);
            var stale = s.load(oldTag);
            stale.tick();
            s.check(stale.isRemoved() && isCurrent(owner, newer),
                    "HS_ROOM_RELOAD_STALE: old saved entity remained or invalidated the new token");

            var currentTag = s.save(newer);
            newer.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            owner.setHealth(0);
            var withDeadOwner = s.load(currentTag);
            s.check(!owner.isAlive() && !owner.isRemoved() && isCurrent(owner, withDeadOwner)
                            && s.level.getServer().getPlayerList().getPlayer(owner.getUUID()) == owner,
                    "fixture: dead owner was not online with the matching loaded token");
            withDeadOwner.tick();
            assertInvalid(s, owner, withDeadOwner, "HS_ROOM_RELOAD_DEAD");
            s.finish();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 100)
    public static void roomLureKindUsesViewerSpecificPriority(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerPlayer incoming = s.player().player;
            ServerPlayer outgoing = s.player().player;
            var attacker = s.spawn(EntityType.PIG, s.parked().add(0, 0, 2));
            var attacked = s.spawn(EntityType.COW, s.parked().add(0, 0, 4));
            s.reached("lure_priority");
            s.after(63, () -> {
                float viewerHealth = incoming.getHealth();
                float mobHealth = attacked.getHealth();
                s.check(incoming.hurt(s.level.damageSources().mobAttack(attacker), 2)
                                && incoming.getHealth() < viewerHealth,
                        "fixture: incoming combat did not apply damage");
                s.check(attacked.hurt(s.level.damageSources().playerAttack(outgoing), 2)
                                && attacked.getHealth() < mobHealth,
                        "fixture: outgoing combat did not apply damage");
                incoming.setHealth(incoming.getMaxHealth() * 0.5F);
                incoming.getFoodData().setFoodLevel(5);
                outgoing.getFoodData().setFoodLevel(5);
                long now = s.level.getGameTime();
                var incomingLure = HighwayStarRoomService.chooseLure(incoming, now + 101);
                var outgoingLure = HighwayStarRoomService.chooseLure(outgoing, now + 6_000);
                s.check(incomingLure.kind() == Kind.COMBAT && incomingLure.entityType().orElseThrow()
                                .equals(BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType())),
                        "HS_ROOM_LURE_INCOMING: applied attacker history did not outlast vanilla memory or beat rest/food");
                s.check(outgoingLure.kind() == Kind.COMBAT && outgoingLure.entityType().orElseThrow()
                                .equals(BuiltInRegistries.ENTITY_TYPE.getKey(attacked.getType())),
                        "HS_ROOM_LURE_OUTGOING: applied attacked history did not retain the correct type for 6000 ticks");
                s.check(HighwayStarRoomService.chooseLure(incoming, now + 6_001).kind() == Kind.REST,
                        "HS_ROOM_LURE_REST: expired combat did not fall through to rest before food");
                s.check(HighwayStarRoomService.chooseLure(outgoing, now + 6_001).kind() == Kind.FOOD,
                        "HS_ROOM_LURE_FOOD: hungry, healthy viewer did not get food");
                outgoing.getFoodData().setFoodLevel(20);
                s.check(HighwayStarRoomService.chooseLure(outgoing, now + 6_001).kind() == Kind.CHEST,
                        "HS_ROOM_LURE_CHEST: full, healthy viewer did not get the chest fallback");
                s.finish();
            });
        });
    }

    private static boolean isCurrent(ServerPlayer owner, HighwayStarRoomEntity room) {
        var ref = HighwayStarRoomService.getRoomRef(owner);
        return !room.isRemoved() && ref != null && ref.roomId().equals(room.getUUID())
                && ref.dimension().equals(room.level().dimension());
    }

    private static void assertInvalid(RoomTestScene s, ServerPlayer owner, HighwayStarRoomEntity room, String label) {
        s.check(room.isRemoved() && HighwayStarRoomService.getRoomRef(owner) == null,
                label + ": Room entity or matching owner token survived");
    }

    private static void pendingAndScent(RoomTestScene s, ServerPlayer owner, HighwayStarRoomEntity room,
            LivingEntity victim, String label) {
        var id = victim.getUUID();
        s.check(room.hasPendingVictim(id) && victim.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                label + ": actual door entry did not consume the Room and slow the victim");
        s.check(s.scent(owner).getTargets().contains(id) && id.equals(s.scent(owner).getSelectedTrackingTarget()),
                label + ": scent and tracking selection were not committed on entry");
    }

    private static void assertEarlyEnd(RoomTestScene s, ServerPlayer owner, HighwayStarRoomEntity room,
            RoomTestScene.Participant victim, String label) {
        s.check(victim.starts(room).size() == 1 && victim.ends(room) == 1 && room.isRemoved()
                        && s.soles(owner, room).size() == 8 && s.scent(owner).getTargets().contains(victim.player.getUUID()),
                label + ": interruption did not send one matching end, spawn eight soles and retain the scent");
    }
}
