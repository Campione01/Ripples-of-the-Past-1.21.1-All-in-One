package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.PhantomIdentity;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxPackets;

/**
 * Epitaph, the room (EPITAPH-ARCH 1.4 to 1.8): the future is lived ahead by copies that stand in the real level
 * without being in it, and nothing they do gets out of the room. Each test lives a whole prediction at the press and
 * calls it off before any body has moved, so whatever differs afterwards got out.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphSandboxGameTests {
    private KingCrimsonEpitaphSandboxGameTests() {}

    // A1
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_untouched")
    public static void sandboxLeavesTheWorldUntouched(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-world", 4, 24, 14);
        ServerLevel level = fixture.level;
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 14.5D, 2.5D);
        Skeleton skeleton = fixture.hunter(EntityType.SKELETON, 12.5D, 10.5D);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        // It stands idle until the press: an arrow of the real one before that would set off the passive erase of the user,
        // and the prediction would begin with a user nothing can aim at.
        skeleton.setNoAi(true);
        Creeper creeper = fixture.mob(EntityType.CREEPER, 7.5D, 11.5D);
        Chicken hen = fixture.mob(EntityType.CHICKEN, 3.5D, 11.5D);
        // Beside the TNT, at its last health: it dies in the room, which rolls its loot there.
        Zombie weak = fixture.mob(EntityType.ZOMBIE, 19.5D, 5.5D);
        weak.setNoAi(true);
        weak.setHealth(1.0F);
        BlockPos chest = fixture.absolute(17, 2, 6);
        BlockPos jobSite = fixture.absolute(1, 2, 8);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ((ChestBlockEntity) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 3));
        level.setBlock(jobSite, Blocks.COMPOSTER.defaultBlockState(), 3);
        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("kc_ep_world");
        scoreboard.addPlayerToTeam(zombie.getScoreboardName(), team);
        // A health objective: a player whose health it has not seen yet writes his score at his next tick.
        Objective health = scoreboard.addObjective("kc_ep_health", ObjectiveCriteria.HEALTH, Component.literal("health"),
                ObjectiveCriteria.RenderType.HEARTS, true, null);
        fixture.base.onClose(() -> {
            scoreboard.removeObjective(health);
            scoreboard.removePlayerTeam(team);
            Clearable.tryClear(level.getBlockEntity(chest));
        });
        helper.runAfterDelay(50, () -> fixture.guarded(() -> {
            skeleton.setNoAi(false);
            creeper.ignite();
            hen.eggTime = 20;
            Vec3 fuse = fixture.spot(18.5D, 2.0D, 4.5D);
            PrimedTnt tnt = fixture.thing(new PrimedTnt(level, fuse.x, fuse.y, fuse.z, null));
            tnt.setFuse(40);
            FallingBlockEntity anvil = fixture.base.track(
                    FallingBlockEntity.fall(level, fixture.absolute(21, 6, 11), Blocks.ANVIL.defaultBlockState()));
            boolean sequencesDirty = level.getRandomSequences().isDirty();
            level.getRandomSequences().setDirty(false);
            BlockPos from = fixture.absolute(-1, 0, -1);
            BlockPos to = fixture.absolute(24, 8, 14);
            KingCrimsonEpitaphWorldPicture before = KingCrimsonEpitaphWorldPicture.of(level, from, to);

            EpitaphSession session = fixture.predict(zombie, skeleton, creeper, hen, weak, tnt, anvil);
            session.foreseeAll();
            // Room code that takes the ticket of a job site, as a villager of a room would.
            Optional<BlockPos> taken;
            EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
            try {
                taken = level.getPoiManager().take(type -> true, (type, pos) -> pos.equals(jobSite), jobSite, 2);
            }
            finally {
                EpitaphSandbox.exit();
            }
            List<RecordedOp.Explode> blasts = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Explode.class);
            List<RecordedOp.Spawn> spawns = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Spawn.class);
            fixture.rule(blasts.size() >= 2 && blasts.stream().anyMatch(blast -> blast.blocks().containsKey(chest)),
                    "Setup: the room saw " + blasts.size() + " explosions, and none that took the chest beside the TNT");
            fixture.rule(KingCrimsonEpitaphFixture.ops(session, RecordedOp.SetBlock.class).stream()
                    .anyMatch(set -> set.state().getBlock() instanceof AnvilBlock), "Setup: no anvil landed in the room");
            fixture.rule(spawns.stream().anyMatch(spawn -> spawn.type() == EntityType.ITEM
                    && spawn.data().getCompound("Item").getString("id").equals("minecraft:egg")),
                    "Setup: the hen laid no egg in the room");
            EntityTrack archer = session.record().track(skeleton.getId());
            fixture.rule(spawns.stream().anyMatch(spawn -> spawn.type() == EntityType.ARROW),
                    "Setup: the skeleton shot no arrow in the room (its track ended " + (archer == null ? "nowhere: it is not foreseen"
                            : archer.end() + " at tick " + archer.lastTick()) + "; its body is after "
                            + (skeleton.getTarget() == null ? "nobody" : skeleton.getTarget().getType().toShortString())
                            + ", has " + skeleton.getHealth() + " health and holds " + skeleton.getMainHandItem().getItem()
                            + "; the user has " + fixture.user.getHealth() + " health; the room lived " + session.room().tick()
                            + " ticks and recorded " + KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).size()
                            + " hits)");
            fixture.rule(KingCrimsonEpitaphFixture.ops(session, RecordedOp.Death.class).stream()
                    .anyMatch(death -> death.victim().id() == weak.getId()), "Setup: the zombie beside the TNT did not die in the room");
            fixture.rule(taken.isPresent(), "Setup: room code found no job site to take");
            session.end(EpitaphEnd.CANCELLED);

            KingCrimsonEpitaphWorldPicture after = KingCrimsonEpitaphWorldPicture.of(level, from, to);
            if (sequencesDirty) {
                level.getRandomSequences().setDirty();
            }
            List<String> changed = before.differences(after);
            fixture.rule(changed.isEmpty(), "a prediction that was called off before any body moved left " + changed.size()
                    + " of " + before.size() + " things changed: " + String.join(" | ", changed.subList(0, Math.min(4, changed.size()))));
            fixture.rule(EpitaphSandbox.leaks() == 0, EpitaphSandbox.leaks() + " things reached past the room, the first: "
                    + EpitaphSandbox.firstLeak());
            fixture.finish("A prediction leaves the world untouched");
        }));
    }

    // A2
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_unsent")
    public static void sandboxSendsNothingToAnyoneElse(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-unsent", 4, 16, 10);
        ServerLevel level = fixture.level;
        ServerPlayer witness = fixture.witness("kc-ep-witness", 6.5D, 6.5D);
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        helper.runAfterDelay(50, () -> fixture.guarded(() -> {
            // A falling block tells its own trackers where it landed, past every broadcast of the level.
            FallingBlockEntity anvil = fixture.base.track(
                    FallingBlockEntity.fall(level, fixture.absolute(8, 6, 4), Blocks.ANVIL.defaultBlockState()));
            helper.assertTrue(KingCrimsonEpitaphFixture.tracks(witness, anvil) && KingCrimsonEpitaphFixture.tracks(witness, zombie),
                    "Setup: the witness does not track the anvil and the zombie");
            int from = KingCrimsonEraseFixture.listener(witness).sentCount();
            int held = SandboxPackets.dropped();
            EpitaphSession session = fixture.predict(zombie, anvil);
            session.foreseeAll();
            List<Packet<?>> got = new ArrayList<>(KingCrimsonEpitaphFixture.flat(KingCrimsonEraseFixture.listener(witness).sentSince(from)));
            // The one thing others are told: that the user foresees, for the face they see on him (ruling R3). Nothing of the room.
            int marks = got.size();
            got.removeIf(packet -> packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof SyncAttachmentsPayload sync
                    && sync.types().equals(List.of(KingCrimsonAddon.EPITAPH_FORESEEING.get())));
            marks -= got.size();
            fixture.rule(got.isEmpty() && marks == 1, "a player beside a prediction was sent " + got.size() + " packets by it"
                    + (got.isEmpty() ? "" : ", the first a " + got.get(0).getClass().getSimpleName()) + ", and was told " + marks
                    + " times that its user foresees");
            fixture.rule(KingCrimsonEpitaphFixture.ops(session, RecordedOp.SetBlock.class).stream()
                    .anyMatch(set -> set.state().getBlock() instanceof AnvilBlock), "Setup: no anvil landed in the room");
            fixture.rule(SandboxPackets.dropped() > held,
                    "Setup: nothing of the room got as far as a connection, so nothing was there to hold back");
            fixture.rule(EpitaphSandbox.leaks() == 0, "something reached past the room: " + EpitaphSandbox.firstLeak());
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A prediction sends nothing to anyone else");
        }));
    }

    // A3
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_no_event")
    public static void noEventForAPhantom(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-events", 4, 24, 10);
        ServerLevel level = fixture.level;
        // The game itself posts the tick events of a rider and of a player, in the middle of their ticks.
        Chicken mount = fixture.mob(EntityType.CHICKEN, 8.5D, 7.5D);
        Zombie rider = fixture.hunter(EntityType.ZOMBIE, 8.5D, 7.5D);
        rider.setBaby(true);
        helper.runAfterDelay(3, () -> fixture.guarded(
                () -> helper.assertTrue(rider.startRiding(mount, true), "Setup: the zombie did not mount the hen")));
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 9.5D, 2.5D);
        Skeleton skeleton = fixture.hunter(EntityType.SKELETON, 12.5D, 8.5D);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Zombie weak = fixture.mob(EntityType.ZOMBIE, 19.5D, 5.5D);
        weak.setNoAi(true);
        weak.setHealth(1.0F);
        helper.runAfterDelay(50, () -> fixture.guarded(() -> {
            Vec3 fuse = fixture.spot(18.5D, 2.0D, 4.5D);
            PrimedTnt tnt = fixture.thing(new PrimedTnt(level, fuse.x, fuse.y, fuse.z, null));
            tnt.setFuse(40);
            PhantomEvents events = new PhantomEvents(level);
            fixture.base.onClose(events::stop);
            EpitaphSession session = fixture.predict(zombie, skeleton, weak, tnt, mount, rider);
            session.foreseeAll();
            events.stop();
            EntityTrack ridden = session.record().track(rider.getId());
            EntityTrack human = session.record().track(fixture.user.getId());
            fixture.rule(ridden != null && ridden.lastTick() >= 20 && human != null && human.lastTick() >= 20
                    && KingCrimsonEpitaphFixture.ops(session, RecordedOp.Mount.class).isEmpty(),
                    "Setup: the copy of the rider did not ride through the room, or no copy of the user lived in it");
            fixture.rule(KingCrimsonEpitaphFixture.ops(session, RecordedOp.Spawn.class).stream()
                    .anyMatch(spawn -> spawn.type() == EntityType.ARROW), "Setup: nothing joined the room: the skeleton shot no arrow");
            fixture.rule(KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).size() > 0
                    && KingCrimsonEpitaphFixture.ops(session, RecordedOp.Death.class).stream()
                            .anyMatch(death -> death.victim().id() == weak.getId()),
                    "Setup: nobody was hurt and nobody died in the room");
            fixture.rule(events.seen.isEmpty(), events.count + " events were posted for entities that are not in the world: "
                    + String.join(", ", events.seen));
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("No event is posted for a phantom");
        }));
    }

    // Ruling R7: the game calls two entities equal when their int ids are, and a phantom has the id of its body.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_identity")
    public static void aPhantomAndItsBodyAreTwoKeys(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-keys", 2, 16, 8);
        fixture.budget(0);
        fixture.userKeepsControl();
        ServerPlayer user = fixture.user;
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 9.5D, 2.5D);
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            // A table as other code keeps them: weak, and found by equals.
            Map<Entity, String> table = new WeakHashMap<>();
            table.put(zombie, "body");
            table.put(user, "body");
            EpitaphSession session = fixture.predict(zombie);
            Entity copy = session.room().phantom(zombie.getId());
            Entity own = session.room().phantom(user.getId());
            helper.assertTrue(copy != null && own != null && copy != zombie && own != user && copy.getId() == zombie.getId()
                    && own.getId() == user.getId(), "Setup: the zombie or the user has no copy with its own int id");
            // What code of a room does with such a table: it files the copy it was handed.
            table.put(copy, "copy");
            table.put(own, "copy");
            session.foreseeAll();
            fixture.rule("body".equals(table.get(zombie)) && "body".equals(table.get(user)), "after the copies were filed in the same table the"
                    + " entry of the zombie reads " + table.get(zombie) + " and that of the user " + table.get(user));
            fixture.rule("copy".equals(table.get(copy)) && "copy".equals(table.get(own)) && table.size() == 4, "the table holds " + table.size()
                    + " entries for two bodies and their two copies, and the copy of the zombie reads " + table.get(copy));
            fixture.rule(!copy.equals(zombie) && !zombie.equals(copy) && copy.equals(copy) && zombie.equals(zombie)
                    && copy.hashCode() == zombie.hashCode(), "a copy and its body: copy equals body " + copy.equals(zombie) + ", body equals copy "
                            + zombie.equals(copy) + ", each equals itself " + (copy.equals(copy) && zombie.equals(zombie)));
            fixture.rule(PhantomIdentity.isMarked(copy) && PhantomIdentity.isMarked(own) && !PhantomIdentity.isMarked(zombie)
                    && !PhantomIdentity.isMarked(user), "the copies are not marked, or a body is");
            session.end(EpitaphEnd.CANCELLED);
            // A copy can sit in the table of somebody else long after its prediction.
            fixture.rule(!copy.equals(zombie) && "body".equals(table.get(zombie)), "after the end of the prediction the copy equals its body again");
            fixture.finish("A phantom and its body are two keys");
        }));
    }

    // Ruling R7: the impact of a projectile is posted for a phantom as for any entity, and a listener has its say.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_impact")
    public static void aListenerThatStopsAnImpactLeavesNoHitInTheRecord(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-impact", 2, 24, 8);
        fixture.userKeepsControl();
        ServerLevel level = fixture.level;
        Zombie zombie = fixture.mob(EntityType.ZOMBIE, 6.5D, 4.5D);
        zombie.setNoAi(true);
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            Vec3 from = fixture.spot(14.5D, 3.2D, 4.5D);
            Arrow arrow = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            arrow.shoot(-1.0D, 0.03D, 0.0D, 1.6F, 0.0F);
            fixture.thing(arrow);
            ActorKey struck = ActorKey.of(zombie);
            EpitaphSession plain = fixture.predict(zombie, arrow);
            plain.foreseeAll();
            long landed = KingCrimsonEpitaphFixture.ops(plain, RecordedOp.Damage.class).stream().filter(hit -> hit.victim().equals(struck)).count();
            plain.end(EpitaphEnd.CANCELLED);
            helper.assertTrue(landed >= 1L, "Setup: with nobody to object, the arrow of the room does not hit the zombie");
            Set<Entity> real = Collections.newSetFromMap(new IdentityHashMap<>());
            level.getAllEntities().forEach(real::add);
            int[] asked = new int[1];
            Consumer<ProjectileImpactEvent> objector = event -> {
                if (!real.contains(event.getProjectile()) && event.getRayTraceResult() instanceof EntityHitResult) {
                    asked[0]++;
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ProjectileImpactEvent.class, objector);
            fixture.base.onClose(() -> NeoForge.EVENT_BUS.unregister(objector));
            EpitaphSession session = fixture.predict(zombie, arrow);
            session.foreseeAll();
            NeoForge.EVENT_BUS.unregister(objector);
            long hits = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).stream().filter(hit -> hit.victim().equals(struck)).count();
            fixture.rule(asked[0] > 0, "nobody was asked about the impact of the arrow of the room on the zombie");
            fixture.rule(hits == 0L, "a listener stopped the impact of the arrow, and the record holds " + hits + " hits on the zombie");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A listener that stops an impact leaves no hit in the record");
        }));
    }

    /**
     * Counts the events other mods listen to that are posted for an entity that was not in the level when the count
     * began: a join, a tick, the tick of a player, a hit at each of its steps, a death, its drops, its experience. (The
     * impact of a projectile is posted on purpose: ruling R7.)
     */
    private static final class PhantomEvents {
        private final java.util.Set<Entity> real = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private final List<Consumer<? extends Event>> listeners = new ArrayList<>();
        final List<String> seen = new ArrayList<>();
        int count;

        PhantomEvents(ServerLevel level) {
            level.getAllEntities().forEach(real::add);
            listen(EntityJoinLevelEvent.class, event -> note("join", event.getEntity()));
            listen(EntityTickEvent.Pre.class, event -> note("tick", event.getEntity()));
            listen(EntityTickEvent.Post.class, event -> note("tick end", event.getEntity()));
            listen(LivingIncomingDamageEvent.class, event -> note("incoming damage", event.getEntity()));
            listen(LivingDamageEvent.Pre.class, event -> note("damage", event.getEntity()));
            listen(LivingDamageEvent.Post.class, event -> note("damage taken", event.getEntity()));
            listen(LivingDeathEvent.class, event -> note("death", event.getEntity()));
            listen(LivingDropsEvent.class, event -> note("drops", event.getEntity()));
            listen(LivingExperienceDropEvent.class, event -> note("experience", event.getEntity()));
            listen(PlayerTickEvent.Pre.class, event -> note("player tick", event.getEntity()));
            listen(PlayerTickEvent.Post.class, event -> note("player tick end", event.getEntity()));
        }

        private <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
            listeners.add(listener);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, type, listener);
        }

        private void note(String what, Entity entity) {
            if (!real.contains(entity)) {
                count++;
                String line = what + " of a " + entity.getType().toShortString();
                if (!seen.contains(line)) {
                    seen.add(line);
                }
            }
        }

        void stop() {
            listeners.forEach(NeoForge.EVENT_BUS::unregister);
            listeners.clear();
        }
    }
}
