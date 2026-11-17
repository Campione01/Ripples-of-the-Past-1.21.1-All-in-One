package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.human.PhantomAbilityAllowList;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.record.TrackFrame;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.core.gametest.GameTestChunkSending;
import rotp.core.gametest.GameTestTimeStops;
import rotp.core.powersystem.standpower.StandPower;

/**
 * What the Epitaph tests share, on top of the Time Erase fixture: a King Crimson user on a stone floor, creatures that
 * hunt him, the press, and the rules a test gathers before it ends. Every prediction of a test is called off when the
 * test closes, and the settings a test moved are put back.
 */
final class KingCrimsonEpitaphFixture {
    final GameTestHelper helper;
    final KingCrimsonEraseFixture base;
    final ServerLevel level;
    final ServerPlayer user;
    final StandPower power;
    private final List<String> broken = new ArrayList<>();

    KingCrimsonEpitaphFixture(GameTestHelper helper, String name, int resolveLevel, int length, int width) {
        this(helper, name, resolveLevel, length, width, false);
    }

    /**
     * With a wired user his packets leave by the own send of the server, so that what a room or a muted tick holds back does
     * not reach his connection. The user of the other tests is sent whatever is sent, whoever would have held it back.
     */
    KingCrimsonEpitaphFixture(GameTestHelper helper, String name, int resolveLevel, int length, int width, boolean wiredUser) {
        this.helper = helper;
        this.level = helper.getLevel();
        base = new KingCrimsonEraseFixture(helper);
        base.floor(length, width);
        user = wiredUser ? base.wiredPlayer(name, 2.5D, 2.0D, 2.5D) : base.player(name, 2.5D, 2.0D, 2.5D);
        base.tickAsConnection(user);
        power = base.kingCrimson(user, resolveLevel);
        int budget = KingCrimsonConfig.EPITAPH_SIM_BUDGET_MILLIS.get();
        AABB arena = AABB.encapsulatingFullBlocks(absolute(-1, 0, -1), absolute(length, 8, width)).inflate(12.0D);
        base.onClose(() -> {
            KingCrimsonConfig.EPITAPH_SIM_BUDGET_MILLIS.set(budget);
            for (EpitaphSession session : EpitaphSessions.all()) {
                session.end(EpitaphEnd.CANCELLED);
            }
            // What a fight leaves lying about is not left for the test that is placed here next.
            for (Entity loose : level.getEntities((Entity) null, arena, entity -> entity instanceof Projectile
                    || entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof FallingBlockEntity
                    || entity instanceof PrimedTnt)) {
                loose.discard();
            }
        });
        EpitaphSandbox.resetLeaks();
        // The tests of the room, the view and the user are fist fights: no phantom uses its Stand unless a test says so.
        List<String> table = PhantomAbilityAllowList.replace(List.of());
        base.onClose(() -> PhantomAbilityAllowList.replace(table));
    }

    /** In this test the phantoms use their Stands as they do in play: what the table of the module lets them press. */
    void phantomsUseStands() {
        PhantomAbilityAllowList.replace(KingCrimsonEpitaphForesightSpec.PHANTOM_ABILITIES);
    }

    /** In this test the phantoms press these abilities and no others. */
    void phantomsUse(String... names) {
        PhantomAbilityAllowList.replace(List.of(names));
    }

    /**
     * The user of this test is not walked (the setting): his phantom has no mind and stands where he stood, and what
     * hunts him comes at a still man.
     */
    void userKeepsControl() {
        boolean follows = KingCrimsonConfig.EPITAPH_USER_FOLLOWS_PHANTOM.get();
        base.onClose(() -> KingCrimsonConfig.EPITAPH_USER_FOLLOWS_PHANTOM.set(follows));
        KingCrimsonConfig.EPITAPH_USER_FOLLOWS_PHANTOM.set(false);
    }

    /**
     * Nobody heals by himself in this test. A walked player lives on at the pace of the server while his phantom lived
     * at the pace of the future, so with one player online he would heal more than his phantom did between two hits.
     */
    void steadyHealth() {
        var healing = level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);
        boolean heals = healing.get();
        base.onClose(() -> healing.set(heals, level.getServer()));
        healing.set(false, level.getServer());
    }

    /** No blow pushes this creature back or wears it down: it comes on and strikes, whoever fights it. */
    <T extends Mob> T sturdy(T mob) {
        mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        mob.setHealth(200.0F);
        return mob;
    }

    /** What the client of a player answers to the last teleport he was sent: without it the server waits for it. */
    static void acceptTeleport(ServerPlayer player) {
        ClientboundPlayerPositionPacket last = null;
        for (Packet<?> packet : flat(KingCrimsonEraseFixture.listener(player).sentSince(0))) {
            if (packet instanceof ClientboundPlayerPositionPacket position) {
                last = position;
            }
        }
        if (last != null) {
            player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(last.getId()));
        }
    }

    /** A second real player on the server: with him online nobody slows. */
    ServerPlayer other(String name, double x, double z) {
        return base.player(name, x, 2.0D, z);
    }

    /**
     * A second real player whose packets leave by the server's own send, so that what a room lets out reaches his
     * connection and what it holds back does not. The chunks around him count as sent: he tracks what is added there.
     */
    ServerPlayer witness(String name, double x, double z) {
        ServerPlayer witness = base.wiredPlayer(name, x, 2.0D, z);
        GameTestChunkSending.markSentAround(witness);
        // What a client that reports a move sets off: the server looks at what he sees now. A fixture player reports none.
        level.getChunkSource().move(witness);
        return witness;
    }

    /** Whether the player was told that this entity exists: he tracks it. */
    static boolean tracks(ServerPlayer player, Entity entity) {
        for (Packet<?> packet : flat(KingCrimsonEraseFixture.listener(player).sentSince(0))) {
            if (packet instanceof ClientboundAddEntityPacket added && added.getId() == entity.getId()) {
                return true;
            }
        }
        return false;
    }

    /** The packets with every bundle taken apart. */
    static List<Packet<?>> flat(List<Packet<?>> packets) {
        List<Packet<?>> flat = new ArrayList<>();
        for (Packet<?> packet : packets) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(flat::add);
            } else {
                flat.add(packet);
            }
        }
        return flat;
    }

    /** An entity of the test that is no creature, placed at a spot above the floor. */
    <T extends Entity> T thing(T entity) {
        helper.assertTrue(level.addFreshEntity(entity), "Setup: the " + entity.getType() + " was not added");
        return base.track(entity);
    }

    Vec3 spot(double x, double y, double z) {
        return helper.absoluteVec(new Vec3(x, y, z));
    }

    <T extends Mob> T hunter(EntityType<T> type, double x, double z) {
        T mob = mob(type, x, z);
        mob.setTarget(user);
        return mob;
    }

    /** A creature on the floor that is after nobody, kept from burning in the test's daylight. */
    <T extends Mob> T mob(EntityType<T> type, double x, double z) {
        T mob = type.create(level);
        helper.assertTrue(mob != null, "Setup: no " + type);
        Vec3 at = helper.absoluteVec(new Vec3(x, 2.0D, z));
        mob.moveTo(at.x, at.y, at.z, 90.0F, 0.0F);
        mob.setPersistenceRequired();
        if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
        }
        helper.assertTrue(level.addFreshEntity(mob), "Setup: the " + type + " was not added");
        return base.track(mob);
    }

    /** The press, with what is under the crosshair. */
    EpitaphSession press(@Nullable Entity crosshair) {
        EpitaphSession session = EpitaphSessions.begin(user, crosshair);
        helper.assertTrue(session != null, "Setup: the press began no prediction");
        return session;
    }

    /** Stops time around a place, held by nobody (the helper of the core); what is returned lets it go on again. */
    Runnable stopTime(ChunkPos center) {
        GameTestTimeStops.Held held = GameTestTimeStops.hold(helper, center, 3, 1200);
        base.onClose(held::release);
        return held::release;
    }

    /** A listener for the length of the test, called after the handlers of the mods. */
    <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, type, listener);
        base.onClose(() -> NeoForge.EVENT_BUS.unregister(listener));
    }

    /** A prediction of exactly these entities, whoever they are after. */
    EpitaphSession predict(Entity... targets) {
        EpitaphSession session = EpitaphSessions.predict(user, List.of(targets));
        helper.assertTrue(session != null, "Setup: no prediction began");
        return session;
    }

    /** Every op of one kind in the record, in the order of their ticks. */
    static <T extends RecordedOp> List<T> ops(EpitaphSession session, Class<T> kind) {
        List<T> found = new ArrayList<>();
        for (int tick = 0; tick <= session.record().foresight(); tick++) {
            for (RecordedOp op : session.record().ops(tick)) {
                if (kind.isInstance(op)) {
                    found.add(kind.cast(op));
                }
            }
        }
        return found;
    }

    /** The tick of the future an op was recorded at, -1 when it is not in the record. */
    static int tickOf(EpitaphSession session, RecordedOp op) {
        for (int tick = 0; tick <= session.record().foresight(); tick++) {
            if (session.record().ops(tick).contains(op)) {
                return tick;
            }
        }
        return -1;
    }

    /** How much of a server tick a prediction may run ahead in this test: 0 keeps the room just ahead of the bodies. */
    void budget(int millis) {
        KingCrimsonConfig.EPITAPH_SIM_BUDGET_MILLIS.set(millis);
    }

    /** Where the track has a body at a tick of the future, whole or not. */
    static Vec3 on(EntityTrack track, double tick) {
        double at = Math.max(track.firstTick(), Math.min(tick, track.lastTick()));
        int from = (int) Math.floor(at);
        TrackFrame a = track.frame(from);
        TrackFrame b = track.frame(Math.min(from + 1, track.lastTick()));
        return a.position().lerp(b.position(), at - from);
    }

    Vec3 relative(Entity entity) {
        return entity.position().subtract(helper.absoluteVec(Vec3.ZERO));
    }

    BlockPos absolute(int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y, z));
    }

    void rule(boolean holds, String brokenRule) {
        if (!holds && !broken.contains(brokenRule)) {
            broken.add(brokenRule);
        }
    }

    void guarded(Runnable check) {
        base.guarded(check);
    }

    void finish(String title) {
        // The report of a failed test is kept in a book: a long list of broken rules is cut to its first ones.
        int shown = Math.min(5, broken.size());
        String rules = String.join("; ", broken.subList(0, shown))
                + (broken.size() > shown ? "; and " + (broken.size() - shown) + " more" : "");
        base.guarded(() -> helper.assertTrue(broken.isEmpty(),
                title + ": " + (rules.length() > 1200 ? rules.substring(0, 1200) + "..." : rules)));
        base.succeed();
    }

    static String at(Vec3 position) {
        return String.format("(%.3f, %.3f, %.3f)", position.x, position.y, position.z);
    }

    static boolean alive(LivingEntity entity) {
        return entity.isAlive() && !entity.isRemoved();
    }
}
