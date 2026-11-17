package rotp.addons.kingcrimson.gametest;

import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphMode;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.window.PullBack;
import rotp.addons.kingcrimson.epitaph.window.WindowBoundary;
import rotp.addons.kingcrimson.epitaph.window.WindowCover;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;
import rotp.addons.kingcrimson.epitaph.window.WindowSnapshot;
import rotp.addons.kingcrimson.mixin.epitaph.LivingStateAccessor;
import rotp.core.api.control.PlayerOperation;
import rotp.core.api.control.PlayerOperationPolicies;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

/**
 * Epitaph aimed at a player with the pull-back switched on (EPITAPH-ARCH 3.5, 5.4): for the phantom phase everybody
 * plays on, and what a participant does to the world is written down; then all are put back exactly as they were at the
 * press, with what they did undone, and walked. Who cannot be put back whole is a hole: nothing of his is undone, so
 * that nothing is ever doubled.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphWindowGameTests {
    private static final int PRESS_AT = 5;
    private static final short STAND_KEY = 33;

    private KingCrimsonEpitaphWindowGameTests() {}

    // The pull-back is on in this test and the phantoms run for the whole phase of the table, so that a window is long
    // enough to act in; the passive erase is off and nobody heals by himself.
    private static void window(KingCrimsonEpitaphFixture fixture) {
        boolean pullBack = KingCrimsonConfig.EPITAPH_PULL_BACK.get();
        double scale = KingCrimsonConfig.EPITAPH_MULTIPLAYER_PHASE_SCALE.get();
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        MinecraftServer server = fixture.level.getServer();
        boolean fights = server.isPvpAllowed();
        fixture.base.onClose(() -> {
            KingCrimsonConfig.EPITAPH_PULL_BACK.set(pullBack);
            KingCrimsonConfig.EPITAPH_MULTIPLAYER_PHASE_SCALE.set(scale);
            KingCrimsonConfig.REFLEX_ERASE.set(reflex);
            server.setPvpAllowed(fights);
            WindowBoundary.wallsOn = true;
            PullBack.failAtStep = 0;
        });
        KingCrimsonConfig.EPITAPH_PULL_BACK.set(true);
        KingCrimsonConfig.EPITAPH_MULTIPLAYER_PHASE_SCALE.set(1.0D);
        KingCrimsonConfig.REFLEX_ERASE.set(false);
        server.setPvpAllowed(true);
        fixture.steadyHealth();
    }

    // A second player, ticked as a connection ticks one: he stays where the server last put him.
    private static ServerPlayer player(KingCrimsonEpitaphFixture fixture, String name, double x, double z) {
        ServerPlayer other = fixture.other(name, x, z);
        fixture.base.tickAsConnection(other);
        return other;
    }

    // The press at this player. A window opens; the room is lived to its end at once, so that the window can close when
    // the test asks for it.
    private static EpitaphSession open(KingCrimsonEpitaphFixture fixture, ServerPlayer enemy) {
        EpitaphSession session = fixture.press(enemy);
        fixture.helper.assertTrue(session.mode() == EpitaphMode.MULTI_WINDOW && session.window() != null && WindowJournal.active,
                "Setup: the press at a player with the pull-back on opened no window: the prediction is " + session.mode());
        session.foreseeAll();
        return session;
    }

    // The window closes now; the teleports of the pull-back and of the walks are answered as clients answer them.
    private static void close(KingCrimsonEpitaphFixture fixture, EpitaphSession session, ServerPlayer... players) {
        session.closeWindowNow();
        KingCrimsonEpitaphFixture.acceptTeleport(fixture.user);
        for (ServerPlayer player : players) {
            if (!player.isRemoved()) {
                KingCrimsonEpitaphFixture.acceptTeleport(player);
            }
        }
    }

    private static AABB arena(KingCrimsonEpitaphFixture fixture) {
        return AABB.encapsulatingFullBlocks(fixture.absolute(-2, 0, -2), fixture.absolute(30, 10, 16));
    }

    private static int has(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item) + (player.inventoryMenu.getCarried().is(item)
                ? player.inventoryMenu.getCarried().getCount() : 0);
    }

    private static int lying(KingCrimsonEpitaphFixture fixture, Item item) {
        int count = 0;
        for (ItemEntity entity : fixture.level.getEntitiesOfClass(ItemEntity.class, arena(fixture), Entity::isAlive)) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    // What his client sends when he throws away one of what he holds.
    private static ItemEntity drop(KingCrimsonEpitaphFixture fixture, ServerPlayer player, Item item) {
        player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.DROP_ITEM,
                BlockPos.ZERO, Direction.DOWN));
        ItemEntity found = null;
        for (ItemEntity entity : fixture.level.getEntitiesOfClass(ItemEntity.class, arena(fixture), Entity::isAlive)) {
            if (entity.getItem().is(item) && WindowJournal.isMade(entity)) {
                found = entity;
            }
        }
        fixture.helper.assertTrue(found != null, "Setup: the player dropped nothing that the journal of the window took for his");
        return found;
    }

    private static String place(Entity entity, Vec3 press) {
        return entity.position().distanceTo(press) < 1.0E-6D ? "where he stood at the press"
                : entity.position().distanceTo(press) + " blocks from where he stood at the press";
    }

    // 3.5, 5.2 and 5.4: the window, its end by its own clock, and the walk after it
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_window_flow")
    public static void inAWindowEverybodyPlaysOnAndIsThenPutBackAndWalked(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-win", 2, 24, 8);
        window(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = player(fixture, "kc-ep-win-him", 12.5D, 2.5D);
        // Nearer to the user than to the other player: a zombie turns on the nearest.
        Zombie zombie = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 6.5D, 6.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        Vec3[] press = new Vec3[3];
        int[] seen = new int[2];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            press[0] = user.position();
            press[1] = enemy.position();
            press[2] = zombie.position();
            EpitaphSession session = open(fixture, enemy);
            fixture.rule(session.binder().isEmpty() && !EpitaphPredictions.isForcedWalking(user) && !EpitaphPredictions.isForcedWalking(enemy),
                    "at the press of a window " + session.binder().all().size() + " bodies are bound, or a player is walked");
            fixture.rule(EpitaphPredictions.isPredictedPlayer(enemy) && !EpitaphPredictions.isPredictedPlayer(user), "in the window the"
                    + " player under the crosshair " + (EpitaphPredictions.isPredictedPlayer(enemy) ? "counts" : "does not count")
                    + " as predicted");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            if (session.window() != null) {
                seen[0]++;
                // Everybody plays on: a move of his client is taken, and the zombie hunts for real.
                Vec3 at = enemy.position();
                enemy.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(at.x, at.y,
                        at.z + 0.2D, true));
                fixture.rule(enemy.position().distanceTo(at) > 0.1D, "in the window a move of the client of the player was not taken");
                fixture.rule(session.passed() == 0 && session.binder().isEmpty(), "in the window the bodies are at tick " + session.passed()
                        + " of the record and " + session.binder().all().size() + " are bound");
                return;
            }
            if (seen[1] == 0) {
                seen[1] = 1;
                KingCrimsonEpitaphFixture.acceptTeleport(user);
                KingCrimsonEpitaphFixture.acceptTeleport(enemy);
                int phase = session.clock().phase();
                fixture.rule(seen[0] >= phase - 1 && seen[0] <= phase + 2, "the window was open for " + seen[0] + " server ticks, and the"
                        + " phantoms run for " + phase);
                fixture.rule(session.passed() <= 1, "right after the pull-back the bodies are at tick " + session.passed()
                        + " of their tracks, not at their start");
                fixture.rule(EpitaphPredictions.isForcedWalking(user) && EpitaphPredictions.isForcedWalking(enemy)
                        && session.binder().of(zombie) != null, "after the pull-back the user is "
                                + (EpitaphPredictions.isForcedWalking(user) ? "walked" : "not walked") + ", the player "
                                + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked") + " and the zombie "
                                + (session.binder().of(zombie) != null ? "on its track" : "on no track"));
                if (session.passed() == 0) {
                    fixture.rule(user.position().distanceTo(press[0]) < 1.0E-6D && enemy.position().distanceTo(press[1]) < 1.0E-6D
                            && zombie.position().distanceTo(press[2]) < 1.0E-6D, "pulled back, the user stands " + place(user, press[0])
                                    + ", the player " + place(enemy, press[1]) + " and the zombie " + place(zombie, press[2]));
                }
            }
            if (!session.ended()) {
                return;
            }
            running[0] = null;
            int whole = session.clock().phase() + session.clock().foresight();
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE && Math.abs(session.clock().elapsed() - whole) <= 2, "the prediction ended as "
                    + session.endedAs() + " after " + session.clock().elapsed() + " server ticks: a window and a walk are " + whole);
            fixture.rule(!WindowJournal.active && !EpitaphPredictions.isForcedWalking(enemy), "after the end a window is still open, or the"
                    + " player still walked");
            fixture.finish("In a window everybody plays on, and is then put back and walked");
        }));
    }

    // Section 6: a room that cannot keep up stretches the window to twice its length; then the prediction is cut to what was lived
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_window_cut")
    public static void aWindowWhoseRoomCannotKeepUpIsCutAtTwiceItsLength(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-cut", 4, 24, 8);
        window(fixture);
        // A quarter of the phase of the table, and a room that lives one tick for each tick of the server: 100 ticks of the
        // future do not fit into twice the 15 of the phase.
        KingCrimsonConfig.EPITAPH_MULTIPLAYER_PHASE_SCALE.set(0.25D);
        fixture.budget(0);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = player(fixture, "kc-ep-cut-him", 12.5D, 2.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.press(enemy);
            helper.assertTrue(session.mode() == EpitaphMode.MULTI_WINDOW && session.window() != null && !session.foreseen(),
                    "Setup: no window opened, or the room lived its whole future at the press");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.window() != null && !session.ended()) {
                return;
            }
            running[0] = null;
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            int phase = session.clock().phase();
            EntityTrack track = session.record().track(enemy.getId());
            int lived = track != null ? track.lastTick() : -1;
            fixture.rule(Math.abs(session.clock().elapsed() - 2 * phase) <= 1, "with a room that cannot keep up the window closed after "
                    + session.clock().elapsed() + " server ticks; twice its phase is " + (2 * phase));
            fixture.rule(lived >= 2 * phase - 2 && lived <= 2 * phase + 1 && lived < session.clock().foresight(), "the track of the player"
                    + " ends at tick " + lived + " of a foresight of " + session.clock().foresight() + ": the prediction is cut to what the"
                    + " room lived in " + (2 * phase) + " server ticks");
            fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(enemy), "after the cut the prediction ended as "
                    + session.endedAs() + ", or the player is not walked along what was lived");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A window whose room cannot keep up is cut at twice its length");
        }));
    }

    // A player is given The World and has it out.
    private static StandEntity theWorld(KingCrimsonEpitaphFixture fixture, LivingEntity bearer) {
        StandPower power = PowerClass.STAND.attachGet(bearer);
        StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
        fixture.helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
                == StandPowerTransitions.Status.APPLIED, "Setup: The World could not be given");
        power.skipProgression();
        fixture.helper.assertTrue(type.summon(bearer, power) && power.getSummonedStandEntity() != null, "Setup: no Stand was summoned");
        StandEntity stand = power.getSummonedStandEntity();
        stand.summonLockTicks = 0;
        power.setStamina(power.getMaxStamina());
        return fixture.base.track(stand);
    }

    private static String brief(Tag tag) {
        String text = String.valueOf(tag);
        return text.length() > 160 ? text.substring(0, 160) + "..." : text;
    }

    // A list whose entries have an id each, as a compound by id; null for any other list.
    private static CompoundTag named(ListTag list) {
        CompoundTag byId = new CompoundTag();
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag one) || !one.contains("id", Tag.TAG_STRING) || byId.contains(one.getString("id"))) {
                return null;
            }
            byId.put(one.getString("id"), one);
        }
        return list.isEmpty() ? null : byId;
    }

    // Where two saved states differ, by path.
    private static void differences(String path, Tag before, Tag after, List<String> out) {
        if (before instanceof CompoundTag was && after instanceof CompoundTag is) {
            for (String key : was.getAllKeys()) {
                if (!is.contains(key)) {
                    out.add(path + key + " is gone (was " + brief(was.get(key)) + ")");
                } else {
                    differences(path + key + "/", was.get(key), is.get(key), out);
                }
            }
            for (String key : is.getAllKeys()) {
                if (!was.contains(key)) {
                    out.add(path + key + " is new (" + brief(is.get(key)) + ")");
                }
            }
        } else if (before instanceof ListTag was && after instanceof ListTag is && named(was) != null && named(is) != null) {
            differences(path, named(was), named(is), out);
        } else if (before instanceof ListTag was && after instanceof ListTag is && was.size() == is.size()) {
            for (int index = 0; index < was.size(); index++) {
                differences(path + index + "/", was.get(index), is.get(index), out);
            }
        } else if (!before.equals(after)) {
            out.add(path + " was " + brief(before) + " and is " + brief(after));
        }
    }

    // E1
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_exact")
    public static void pullBackRestoresThePlayerExactly(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-exact", 4, 24, 8);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-exact-him", 12.5D, 2.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        CompoundTag[] before = new CompoundTag[1];
        Map<Item, Integer> waits = new HashMap<>();
        int[] arm = new int[1];
        float[] stamina = new float[1];
        StandEntity[] out = new StandEntity[1];
        helper.runAfterDelay(PRESS_AT - 3, () -> fixture.guarded(() -> {
            out[0] = theWorld(fixture, enemy);
            // A player who ever logged in has a seed for the enchanting table; the players of a test never did.
            enemy.onEnchantmentPerformed(ItemStack.EMPTY, 0);
            StandPower.get(enemy).setStamina(500.0F);
            enemy.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            enemy.getInventory().setItem(1, new ItemStack(Items.ARROW, 12));
            enemy.getInventory().setItem(2, new ItemStack(Items.DIAMOND, 5));
            enemy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            enemy.getInventory().selected = 2;
            enemy.inventoryMenu.setCarried(new ItemStack(Items.EMERALD, 3));
            enemy.inventoryMenu.getCraftSlots().setItem(0, new ItemStack(Items.STICK, 2));
            enemy.getCooldowns().addCooldown(Items.ENDER_PEARL, 100);
            enemy.setHealth(14.0F);
            enemy.getFoodData().setFoodLevel(15);
            enemy.giveExperiencePoints(20);
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
            enemy.getPersistentData().putInt("kc_window_mark", 7);
        }));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            before[0] = enemy.saveWithoutId(new CompoundTag());
            WindowSnapshot.cooldownsOf(enemy, waits);
            arm[0] = ((LivingStateAccessor) enemy).kingCrimson$attackStrengthTicker();
            stamina[0] = StandPower.get(enemy).getStamina();
            running[0] = open(fixture, enemy);
        }));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            // Everything of him changes in the window.
            enemy.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(5));
            enemy.inventoryMenu.setCarried(new ItemStack(Items.GOLD_INGOT));
            enemy.inventoryMenu.getCraftSlots().setItem(0, ItemStack.EMPTY);
            enemy.getInventory().setItem(2, ItemStack.EMPTY);
            enemy.getInventory().setItem(7, new ItemStack(Items.COBBLESTONE, 4));
            enemy.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            enemy.getCooldowns().addCooldown(Items.SHIELD, 60);
            enemy.getCooldowns().removeCooldown(Items.ENDER_PEARL);
            enemy.hurt(enemy.damageSources().magic(), 4.0F);
            enemy.getFoodData().setFoodLevel(8);
            enemy.giveExperiencePoints(50);
            enemy.removeEffect(MobEffects.MOVEMENT_SPEED);
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 1));
            enemy.getPersistentData().putInt("kc_window_mark", 99);
            enemy.getPersistentData().putInt("kc_window_new", 1);
            StandPower.get(enemy).setStamina(100.0F);
            enemy.absMoveTo(enemy.getX() + 3.0D, enemy.getY(), enemy.getZ() + 2.0D, 45.0F, 10.0F);
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(session.window() != null && has(enemy, Items.DIAMOND) == 0 && enemy.getHealth() < 14.0F,
                    "Setup: the window is closed already, or what the player changed in it did not change");
            close(fixture, session, enemy);
            CompoundTag after = enemy.saveWithoutId(new CompoundTag());
            // An attribute nobody asked for before the press is saved from the first time it is asked, at its default.
            CompoundTag had = named(before[0].getList("attributes", Tag.TAG_COMPOUND));
            after.getList("attributes", Tag.TAG_COMPOUND).removeIf(entry -> entry instanceof CompoundTag one && had != null
                    && !had.contains(one.getString("id")) && !one.contains("modifiers")
                    && BuiltInRegistries.ATTRIBUTE.get(ResourceLocation.parse(one.getString("id"))) != null && one.getDouble("base")
                            == BuiltInRegistries.ATTRIBUTE.get(ResourceLocation.parse(one.getString("id"))).getDefaultValue());
            List<String> differ = new java.util.ArrayList<>();
            differences("", before[0], after, differ);
            // His walk has begun: from here he moves as his phantom did.
            differ.removeIf(where -> where.startsWith("Motion/"));
            String where = differ.toString();
            fixture.rule(differ.isEmpty(), "after the pull-back the saved data of the player differs from that of the press in "
                    + differ.size() + " places: " + (where.length() > 1500 ? where.substring(0, 1500) : where));
            ItemStack carried = enemy.inventoryMenu.getCarried();
            fixture.rule(carried.is(Items.EMERALD) && carried.getCount() == 3, "the stack on his cursor is " + carried
                    + ", not the 3 emeralds of the press");
            ItemStack grid = enemy.inventoryMenu.getCraftSlots().getItem(0);
            fixture.rule(grid.is(Items.STICK) && grid.getCount() == 2, "his crafting grid holds " + grid + ", not the 2 sticks of the press");
            Map<Item, Integer> now = new HashMap<>();
            WindowSnapshot.cooldownsOf(enemy, now);
            fixture.rule(now.equals(waits), "his items wait for " + now + ", and waited for " + waits + " at the press");
            int swung = ((LivingStateAccessor) enemy).kingCrimson$attackStrengthTicker();
            fixture.rule(enemy.getInventory().selected == 2 && swung == arm[0], "he holds slot " + enemy.getInventory().selected
                    + " (2 at the press) and his arm is " + swung + " ticks into its swing back (" + arm[0] + " at the press)");
            // What a plain load would lose: his power would be a new one that does not know the Stand he has out.
            StandPower power = StandPower.get(enemy);
            fixture.rule(power != null && power.getSummonedStandEntity() == out[0] && !out[0].isRemoved()
                    && Math.abs(power.getStamina() - stamina[0]) < 0.01F, "after the pull-back his Stand power "
                            + (power != null && power.getSummonedStandEntity() == out[0] ? "knows" : "does not know")
                            + " the Stand he has out, and has " + (power != null ? power.getStamina() : -1.0F) + " stamina ("
                            + stamina[0] + " at the press)");
            fixture.rule(EpitaphPredictions.isForcedWalking(enemy), "pulled back, the player is not walked");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The pull-back puts a player back exactly as he was at the press");
        }));
    }

    // E2
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_drop")
    public static void droppedItemIsNotDoubled(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-drop", 4, 24, 8);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-drop-him", 12.5D, 2.5D);
        enemy.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        enemy.getInventory().selected = 0;
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            drop(fixture, enemy, Items.DIAMOND);
            helper.assertTrue(has(enemy, Items.DIAMOND) == 0 && lying(fixture, Items.DIAMOND) == 1, "Setup: after the drop the player has "
                    + has(enemy, Items.DIAMOND) + " diamonds and " + lying(fixture, Items.DIAMOND) + " lie on the ground");
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            close(fixture, running[0], enemy);
            fixture.rule(has(enemy, Items.DIAMOND) == 1 && lying(fixture, Items.DIAMOND) == 0, "he had one diamond and dropped it in the"
                    + " window: after the pull-back he has " + has(enemy, Items.DIAMOND) + " and " + lying(fixture, Items.DIAMOND)
                    + " lie on the ground");
            fixture.rule(EpitaphPredictions.isForcedWalking(enemy), "pulled back, the player is not walked");
            running[0].end(EpitaphEnd.CANCELLED);
            fixture.finish("A dropped item is not doubled");
        }));
    }

    // E6
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_wall")
    public static void aThirdPlayerCannotTakeAMarkedItem(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-wall", 4, 24, 10);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-wall-him", 12.5D, 2.5D);
        enemy.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        enemy.getInventory().selected = 0;
        // A third player, who is no part of the prediction, beside a hopper: the diamond will lie on it, within his reach.
        ServerPlayer third = fixture.other("kc-ep-wall-third", 12.5D, 6.5D);
        helper.setBlock(new BlockPos(13, 2, 6), Blocks.HOPPER);
        EpitaphSession[] running = new EpitaphSession[1];
        ItemEntity[] dropped = new ItemEntity[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            dropped[0] = drop(fixture, enemy, Items.DIAMOND);
            Vec3 onHopper = fixture.spot(13.5D, 3.0D, 6.5D);
            dropped[0].setPos(onHopper.x, onHopper.y, onHopper.z);
            dropped[0].setDeltaMovement(Vec3.ZERO);
            dropped[0].setNoPickUpDelay();
        }));
        helper.runAfterDelay(PRESS_AT + 28, () -> fixture.guarded(() -> {
            helper.assertTrue(running[0].window() != null && dropped[0].distanceTo(third) < 2.0F, "Setup: the window is closed, or the"
                    + " diamond lies " + dropped[0].distanceTo(third) + " blocks from the third player");
            boolean inHopper = fixture.level.getBlockEntity(fixture.absolute(13, 2, 6)) instanceof HopperBlockEntity hopper
                    && !hopper.isEmpty();
            fixture.rule(dropped[0].isAlive() && has(third, Items.DIAMOND) == 0 && !inHopper, "25 ticks on a hopper beside a third"
                    + " player, the diamond a participant dropped " + (dropped[0].isAlive() ? "still lies there" : "is gone") + ", the third"
                    + " player has " + has(third, Items.DIAMOND) + " and the hopper " + (inHopper ? "holds something" : "is empty"));
            running[0].end(EpitaphEnd.CANCELLED);
            fixture.finish("A third player cannot take a marked item, nor can a hopper");
        }));
    }

    // E7
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_hole")
    public static void aTakenMarkedItemMakesAHoleNotACopy(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-hole", 4, 24, 10);
        window(fixture);
        // The walls are down in this test: what holds then is the check before the pull-back.
        WindowBoundary.wallsOn = false;
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = player(fixture, "kc-ep-hole-him", 12.5D, 2.5D);
        enemy.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        enemy.getInventory().selected = 0;
        ServerPlayer third = fixture.other("kc-ep-hole-third", 12.5D, 6.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            ItemEntity dropped = drop(fixture, enemy, Items.DIAMOND);
            dropped.setPos(third.getX(), third.getY(), third.getZ());
            dropped.setDeltaMovement(Vec3.ZERO);
            dropped.setNoPickUpDelay();
            enemy.absMoveTo(enemy.getX() + 2.0D, enemy.getY(), enemy.getZ(), enemy.getYRot(), enemy.getXRot());
        }));
        helper.runAfterDelay(PRESS_AT + 10, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            WindowJournal journal = session.window();
            helper.assertTrue(journal != null && has(third, Items.DIAMOND) == 1, "Setup: the window is closed, or the third player has "
                    + has(third, Items.DIAMOND) + " diamonds: he did not take the one that lay at his feet");
            Vec3 stood = enemy.position();
            close(fixture, session, enemy);
            int all = has(enemy, Items.DIAMOND) + has(third, Items.DIAMOND) + lying(fixture, Items.DIAMOND);
            fixture.rule(all == 1 && has(third, Items.DIAMOND) == 1, "one diamond was dropped and taken by a third player: after the"
                    + " pull-back there are " + all + " (the dropper " + has(enemy, Items.DIAMOND) + ", the third " + has(third, Items.DIAMOND)
                    + ", on the ground " + lying(fixture, Items.DIAMOND) + ")");
            WindowJournal.Participant dropper = journal.of(enemy);
            fixture.rule(dropper != null && dropper.isHole() && !EpitaphPredictions.isForcedWalking(enemy)
                    && enemy.position().distanceTo(stood) < 1.0E-9D, "the dropper is " + (dropper != null && dropper.isHole() ? "a hole ("
                            + dropper.why() + ")" : "no hole") + ", " + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked")
                            + ", and was moved " + enemy.position().distanceTo(stood) + " blocks");
            fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(user), "with the player a hole the prediction ended as "
                    + session.endedAs() + ", or its user is not pulled back and walked");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A marked item that was taken makes a hole, not a copy");
        }));
    }

    // E9
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_both_ways")
    public static void theWallHoldsBothWays(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-ways", 4, 24, 10);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-ways-him", 12.5D, 2.5D);
        ServerPlayer third = fixture.other("kc-ep-ways-third", 12.5D, 4.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        float[] health = new float[2];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            health[0] = enemy.getHealth();
            health[1] = third.getHealth();
            running[0] = open(fixture, enemy);
        }));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            // What his client sends when he strikes the player beside him, who is no part of the prediction.
            enemy.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(third, false));
            fixture.rule(third.getHealth() == health[1], "a blow of a participant landed on a player outside the window: he has "
                    + third.getHealth() + " of " + health[1] + " health");
            // What a potion of his would do to the outsider.
            third.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0), enemy);
            fixture.rule(!third.hasEffect(MobEffects.POISON), "an effect a participant put on a player outside the window took hold");
            third.attack(enemy);
            helper.assertTrue(enemy.getHealth() < health[0], "Setup: the blow of the outsider did not land on the participant");
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            close(fixture, running[0], enemy);
            fixture.rule(enemy.getHealth() == health[0] && EpitaphPredictions.isForcedWalking(enemy), "struck by an outsider in the"
                    + " window, the participant has " + enemy.getHealth() + " health after the pull-back; he had " + health[0] + " at the press");
            running[0].end(EpitaphEnd.CANCELLED);
            fixture.finish("The wall holds both ways");
        }));
    }

    // E3
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_blocks")
    public static void placedAndBrokenBlocksAreUndone(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-blocks", 4, 24, 8);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-blocks-him", 12.5D, 2.5D);
        ServerPlayer third = fixture.other("kc-ep-blocks-third", 12.5D, 6.5D);
        enemy.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        enemy.getInventory().selected = 0;
        // A pot with gold in it: it breaks at a touch, and what it held falls out.
        helper.setBlock(new BlockPos(13, 2, 4), Blocks.DECORATED_POT);
        BlockPos pot = fixture.absolute(13, 2, 4);
        BlockPos floor = fixture.absolute(11, 1, 3);
        helper.assertTrue(fixture.level.getBlockEntity(pot) instanceof DecoratedPotBlockEntity, "Setup: the pot has no block entity");
        ((DecoratedPotBlockEntity) fixture.level.getBlockEntity(pot)).setTheItem(new ItemStack(Items.GOLD_INGOT, 5));
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            // What his client sends when he sets a stone on the floor and strikes the pot.
            enemy.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor).add(0.0D, 0.5D, 0.0D), Direction.UP, floor, false), 1));
            enemy.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pot, Direction.UP, 2));
            helper.assertTrue(fixture.level.getBlockState(floor.above()).is(Blocks.COBBLESTONE) && has(enemy, Items.COBBLESTONE) == 3,
                    "Setup: the player set no stone: above the floor is " + fixture.level.getBlockState(floor.above()) + " and he has "
                            + has(enemy, Items.COBBLESTONE) + " left of 4");
            helper.assertTrue(fixture.level.getBlockState(pot).isAir() && lying(fixture, Items.GOLD_INGOT) == 5, "Setup: the pot is "
                    + fixture.level.getBlockState(pot) + " and " + lying(fixture, Items.GOLD_INGOT) + " gold lies about: he did not break it");
            // What the server does when a player outside the window has broken through the stone the participant set.
            boolean broke = third.gameMode.destroyBlock(floor.above());
            fixture.rule(!broke && fixture.level.getBlockState(floor.above()).is(Blocks.COBBLESTONE), "a player outside the window "
                    + (broke ? "broke" : "did not break") + " the stone a participant set in it: there is now "
                    + fixture.level.getBlockState(floor.above()));
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            close(fixture, running[0], enemy);
            fixture.rule(fixture.level.getBlockState(floor.above()).isAir() && has(enemy, Items.COBBLESTONE) == 4, "after the pull-back the"
                    + " stone he set is " + fixture.level.getBlockState(floor.above()) + " and he has " + has(enemy, Items.COBBLESTONE)
                    + " of the 4 he had at the press");
            DecoratedPotBlockEntity again = fixture.level.getBlockEntity(pot) instanceof DecoratedPotBlockEntity found ? found : null;
            ItemStack held = again != null ? again.getTheItem() : ItemStack.EMPTY;
            fixture.rule(fixture.level.getBlockState(pot).is(Blocks.DECORATED_POT) && held.is(Items.GOLD_INGOT) && held.getCount() == 5,
                    "after the pull-back the pot he broke is " + fixture.level.getBlockState(pot) + " and holds " + held
                            + "; it held 5 gold at the press");
            fixture.rule(lying(fixture, Items.GOLD_INGOT) == 0 && lying(fixture, Items.DECORATED_POT) == 0, "what fell out of the pot"
                    + " still lies there: " + lying(fixture, Items.GOLD_INGOT) + " gold and " + lying(fixture, Items.DECORATED_POT) + " pots");
            fixture.rule(EpitaphPredictions.isForcedWalking(enemy), "pulled back, the player is not walked");
            // Nothing falls out when the floor of the test is laid again.
            if (again != null) {
                again.setTheItem(ItemStack.EMPTY);
            }
            running[0].end(EpitaphEnd.CANCELLED);
            fixture.finish("Placed and broken blocks are undone");
        }));
    }

    // E4
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_shots")
    public static void shotArrowAndThrownPearlAreUndone(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-shots", 4, 30, 8);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-shots-him", 12.5D, 2.5D);
        enemy.getInventory().setItem(0, new ItemStack(Items.ENDER_PEARL, 2));
        enemy.getInventory().setItem(1, new ItemStack(Items.BOW));
        enemy.getInventory().setItem(2, new ItemStack(Items.ARROW, 12));
        enemy.getInventory().selected = 0;
        EpitaphSession[] running = new EpitaphSession[1];
        Entity[] flying = new Entity[2];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 2, () -> fixture.guarded(() -> {
            // What his client sends when he throws a pearl high, takes the bow and draws it.
            enemy.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, -90.0F, -40.0F));
            enemy.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(1));
            enemy.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 2, -90.0F, -40.0F));
            helper.assertTrue(enemy.isUsingItem() && has(enemy, Items.ENDER_PEARL) == 1, "Setup: the player threw no pearl (he has "
                    + has(enemy, Items.ENDER_PEARL) + " of 2), or does not draw the bow");
        }));
        helper.runAfterDelay(PRESS_AT + 9, () -> fixture.guarded(() -> {
            // He lets the string go, and draws again.
            enemy.connection.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                    BlockPos.ZERO, Direction.DOWN));
            enemy.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 3, -90.0F, -40.0F));
            for (Entity shot : fixture.level.getEntities(enemy, enemy.getBoundingBox().inflate(64.0D),
                    entity -> entity instanceof AbstractArrow || entity instanceof ThrownEnderpearl)) {
                flying[shot instanceof AbstractArrow ? 0 : 1] = shot;
            }
            helper.assertTrue(flying[0] != null && flying[1] != null && has(enemy, Items.ARROW) == 11 && enemy.isUsingItem(),
                    "Setup: of an arrow and a pearl in the air there is " + (flying[0] != null ? "the arrow" : "no arrow") + " and "
                            + (flying[1] != null ? "the pearl" : "no pearl") + "; he has " + has(enemy, Items.ARROW) + " arrows of 12");
        }));
        helper.runAfterDelay(PRESS_AT + 11, () -> fixture.guarded(() -> {
            helper.assertTrue(running[0].window() != null && !flying[1].isRemoved(), "Setup: the window is closed, or the pearl has landed");
            close(fixture, running[0], enemy);
            fixture.rule(flying[0].isRemoved() && flying[1].isRemoved(), "after the pull-back the arrow he shot is "
                    + (flying[0].isRemoved() ? "gone" : "still there") + " and the pearl he threw is "
                    + (flying[1].isRemoved() ? "gone" : "still there"));
            ItemStack bow = enemy.getInventory().getItem(1);
            fixture.rule(has(enemy, Items.ARROW) == 12 && has(enemy, Items.ENDER_PEARL) == 2 && bow.is(Items.BOW) && bow.getDamageValue() == 0,
                    "after the pull-back he has " + has(enemy, Items.ARROW) + " arrows of 12 and " + has(enemy, Items.ENDER_PEARL)
                            + " pearls of 2, and his bow is " + bow + " worn by " + bow.getDamageValue());
            fixture.rule(!enemy.getCooldowns().isOnCooldown(Items.ENDER_PEARL) && enemy.getInventory().selected == 0 && !enemy.isUsingItem(),
                    "after the pull-back his pearls " + (enemy.getCooldowns().isOnCooldown(Items.ENDER_PEARL) ? "wait" : "do not wait")
                            + ", he holds slot " + enemy.getInventory().selected + " (0 at the press) and "
                            + (enemy.isUsingItem() ? "still draws the bow he drew in the window" : "draws nothing"));
            fixture.rule(EpitaphPredictions.isForcedWalking(enemy), "pulled back, the player is not walked");
            running[0].end(EpitaphEnd.CANCELLED);
            fixture.finish("A shot arrow and a thrown pearl are undone");
        }));
    }

    // E5
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_pickup")
    public static void pickedUpItemGoesBackToTheGround(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-pick", 4, 24, 8);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-pick-him", 12.5D, 2.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        Entity[] lay = new Entity[2];
        int[] points = new int[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            points[0] = enemy.totalExperience;
            running[0] = open(fixture, enemy);
        }));
        helper.runAfterDelay(PRESS_AT + 2, () -> fixture.guarded(() -> {
            // They lie at his feet by no hand of the window, and he takes them.
            Vec3 at = enemy.position();
            ItemEntity emeralds = new ItemEntity(fixture.level, at.x + 0.3D, at.y, at.z, new ItemStack(Items.EMERALD, 3));
            emeralds.setDeltaMovement(Vec3.ZERO);
            emeralds.setNoPickUpDelay();
            ExperienceOrb orb = new ExperienceOrb(fixture.level, at.x - 0.3D, at.y, at.z, 7);
            helper.assertTrue(fixture.level.addFreshEntity(emeralds) && fixture.level.addFreshEntity(orb), "Setup: nothing was laid down");
            lay[0] = emeralds;
            lay[1] = orb;
        }));
        helper.runAfterDelay(PRESS_AT + 9, () -> fixture.guarded(() -> {
            helper.assertTrue(running[0].window() != null && has(enemy, Items.EMERALD) == 3 && lay[0].isRemoved() && lay[1].isRemoved()
                    && enemy.totalExperience == points[0] + 7, "Setup: in the window the player took " + has(enemy, Items.EMERALD)
                            + " of 3 emeralds and " + (enemy.totalExperience - points[0]) + " of 7 points of experience");
            close(fixture, running[0], enemy);
            int orbs = 0;
            for (ExperienceOrb orb : fixture.level.getEntitiesOfClass(ExperienceOrb.class, arena(fixture), Entity::isAlive)) {
                orbs += orb.getValue();
            }
            fixture.rule(has(enemy, Items.EMERALD) == 0 && lying(fixture, Items.EMERALD) == 3, "he took 3 emeralds from the ground in the"
                    + " window: after the pull-back he has " + has(enemy, Items.EMERALD) + " and " + lying(fixture, Items.EMERALD)
                    + " lie on the ground");
            fixture.rule(enemy.totalExperience == points[0] && orbs == 7, "he took 7 points of experience in the window: after the"
                    + " pull-back he has " + (enemy.totalExperience - points[0]) + " more than at the press and " + orbs + " lie on the ground");
            fixture.rule(EpitaphPredictions.isForcedWalking(enemy), "pulled back, the player is not walked");
            running[0].end(EpitaphEnd.CANCELLED);
            fixture.finish("What was picked up goes back to the ground");
        }));
    }

    // 3.5, the wall: an arrow a participant shot is collected by him alone
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_arrow")
    public static void aThirdPlayerCannotCollectAMarkedArrow(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-arrow", 4, 24, 10);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-arrow-him", 12.5D, 2.5D);
        ServerPlayer third = fixture.other("kc-ep-arrow-third", 12.5D, 6.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        Arrow[] stuck = new Arrow[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 2, () -> fixture.guarded(() -> {
            // An arrow of the participant comes down beside the third player, within his reach.
            Arrow arrow = new Arrow(fixture.level, enemy, new ItemStack(Items.ARROW), null);
            arrow.pickup = AbstractArrow.Pickup.ALLOWED;
            arrow.setPos(third.getX() + 0.8D, third.getY() + 1.0D, third.getZ());
            arrow.setDeltaMovement(0.0D, -1.0D, 0.0D);
            helper.assertTrue(fixture.level.addFreshEntity(arrow), "Setup: the arrow was not added");
            stuck[0] = arrow;
        }));
        helper.runAfterDelay(PRESS_AT + 27, () -> fixture.guarded(() -> {
            helper.assertTrue(running[0].window() != null && stuck[0].distanceTo(third) < 1.2F, "Setup: the window is closed, or the arrow"
                    + " is " + stuck[0].distanceTo(third) + " blocks from the third player");
            fixture.rule(WindowJournal.isMade(stuck[0]), "an arrow whose shooter is a participant is not written down as his");
            fixture.rule(stuck[0].isAlive() && has(third, Items.ARROW) == 0, "25 ticks in the ground beside a third player, the arrow of a"
                    + " participant " + (stuck[0].isAlive() ? "is still there" : "is gone") + " and the third player has "
                    + has(third, Items.ARROW) + " arrows");
            running[0].end(EpitaphEnd.CANCELLED);
            fixture.finish("A third player cannot collect a marked arrow");
        }));
    }

    private static int onFire(KingCrimsonEpitaphFixture fixture, BlockPos fire, Item item) {
        int count = 0;
        if (fixture.level.getBlockEntity(fire) instanceof CampfireBlockEntity campfire) {
            for (ItemStack stack : campfire.getItems()) {
                count += stack.is(item) ? stack.getCount() : 0;
            }
        }
        return count;
    }

    // E8
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_containers")
    public static void containersMakeNoCopies(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-box", 4, 24, 10);
        window(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = player(fixture, "kc-ep-box-him", 12.5D, 2.5D);
        enemy.getInventory().setItem(0, new ItemStack(Items.BEEF, 3));
        enemy.getInventory().selected = 0;
        ServerPlayer third = fixture.other("kc-ep-box-third", 12.5D, 6.5D);
        helper.setBlock(new BlockPos(13, 2, 4), Blocks.CHEST);
        helper.setBlock(new BlockPos(11, 2, 3), Blocks.CAMPFIRE);
        BlockPos box = fixture.absolute(13, 2, 4);
        BlockPos fire = fixture.absolute(11, 2, 3);
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            helper.assertTrue(fixture.level.getBlockEntity(box) instanceof ChestBlockEntity, "Setup: the chest has no block entity");
            ChestBlockEntity chest = (ChestBlockEntity) fixture.level.getBlockEntity(box);
            third.openMenu(chest);
            helper.assertTrue(third.containerMenu != third.inventoryMenu, "Setup: the chest opens for nobody");
            third.closeContainer();
            enemy.openMenu(chest);
            fixture.rule(enemy.containerMenu == enemy.inventoryMenu, "in the window a chest opened for a participant");
            // What no wall shuts out is found out: he lays a piece of meat on a campfire.
            enemy.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(fire), Direction.UP, fire, false), 1));
            helper.assertTrue(onFire(fixture, fire, Items.BEEF) == 1 && has(enemy, Items.BEEF) == 2, "Setup: on the campfire lie "
                    + onFire(fixture, fire, Items.BEEF) + " pieces of meat and the player has " + has(enemy, Items.BEEF) + " of 3");
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            WindowJournal journal = session.window();
            helper.assertTrue(journal != null, "Setup: the window is closed already");
            Vec3 stood = enemy.position();
            close(fixture, session, enemy);
            int all = has(enemy, Items.BEEF) + onFire(fixture, fire, Items.BEEF) + lying(fixture, Items.BEEF);
            fixture.rule(all == 3, "he had 3 pieces of meat and laid one on a campfire in the window: after the pull-back there are " + all
                    + " (he has " + has(enemy, Items.BEEF) + ", on the fire " + onFire(fixture, fire, Items.BEEF) + ")");
            WindowJournal.Participant he = journal.of(enemy);
            fixture.rule(he != null && he.isHole() && !EpitaphPredictions.isForcedWalking(enemy)
                    && enemy.position().distanceTo(stood) < 1.0E-9D, "the player who changed a block entity is "
                            + (he != null && he.isHole() ? "a hole (" + he.why() + ")" : "no hole") + ", "
                            + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked") + ", and was moved "
                            + enemy.position().distanceTo(stood) + " blocks");
            fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(user), "with the player a hole the prediction ended as "
                    + session.endedAs() + ", or its user is not pulled back and walked");
            // Nothing falls out when the floor of the test is laid again.
            if (fixture.level.getBlockEntity(fire) instanceof CampfireBlockEntity campfire) {
                campfire.getItems().clear();
            }
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("Containers make no copies");
        }));
    }

    // E10
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_mob")
    public static void mobKilledInTheWindowIsBackAndItsLootIsGone(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-mob", 4, 24, 8);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-mob-him", 8.5D, 2.5D);
        enemy.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SWORD));
        enemy.getInventory().selected = 0;
        // Two cows nobody foresees, and a zombie that hunts the user and is foreseen with him: each dies at one blow. The
        // zombie stands nearer to the user than to the player who will strike it: it turns on the nearest.
        Cow early = fixture.mob(EntityType.COW, 9.5D, 3.5D);
        Cow late = fixture.mob(EntityType.COW, 9.5D, 1.5D);
        Zombie hunter = fixture.hunter(EntityType.ZOMBIE, 5.4D, 2.5D);
        early.setNoAi(true);
        late.setNoAi(true);
        early.setHealth(1.0F);
        late.setHealth(1.0F);
        hunter.setHealth(1.0F);
        int zombieId = hunter.getId();
        Vec3 grazed = early.position();
        EpitaphSession[] running = new EpitaphSession[1];
        int[] points = new int[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            points[0] = enemy.totalExperience;
            running[0] = open(fixture, enemy);
            helper.assertTrue(running[0].window().of(hunter) != null && running[0].window().of(early) == null, "Setup: the zombie that"
                    + " hunts the user is no part of the window, or a cow is");
        }));
        helper.runAfterDelay(PRESS_AT + 2, () -> fixture.guarded(() -> {
            // What a potion of his does to a creature: the second cow is slowed, and that is his doing.
            late.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 1), enemy);
            helper.assertTrue(late.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Setup: the second cow is not slowed");
            // What his client sends when he strikes them.
            enemy.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(hunter, false));
            enemy.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(early, false));
            helper.assertTrue(hunter.isDeadOrDying() && early.isDeadOrDying(), "Setup: struck, the zombie has " + hunter.getHealth()
                    + " health and the first cow " + early.getHealth());
        }));
        helper.runAfterDelay(PRESS_AT + 25, () -> fixture.guarded(() -> {
            helper.assertTrue(running[0].window() != null && hunter.isRemoved() && early.isRemoved(), "Setup: the window is closed, or the"
                    + " zombie and the first cow are not gone yet");
            enemy.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(late, false));
        }));
        helper.runAfterDelay(PRESS_AT + 27, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(session.window() != null && late.isDeadOrDying() && !late.isRemoved(), "Setup: the second cow is not dying");
            close(fixture, session, enemy);
            Entity cow = fixture.level.getEntity(early.getUUID());
            fixture.rule(cow instanceof Cow again && again != early && again.isAlive() && again.getHealth() == 1.0F
                    && again.position().distanceTo(grazed) < 1.0E-6D, "the cow that was killed and gone in the window is "
                            + (cow instanceof Cow again ? "back with " + again.getHealth() + " health, "
                                    + again.position().distanceTo(grazed) + " blocks from where it stood" : "not back: " + cow));
            fixture.rule(late.isAlive() && !late.isRemoved() && late.deathTime == 0 && late.getHealth() == 1.0F
                    && late.getPose() != Pose.DYING, "the cow that was dying at the pull-back has " + late.getHealth() + " health, is "
                            + late.deathTime + " ticks into its death and has the pose " + late.getPose());
            fixture.rule(!late.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the cow a participant slowed in the window is still slowed"
                    + " after the pull-back");
            Entity zombie = fixture.level.getEntity(zombieId);
            fixture.rule(zombie instanceof Zombie again && again != hunter && again.isAlive() && again.getUUID().equals(hunter.getUUID())
                    && session.binder().of(again) != null, "the zombie of the prediction that was killed in the window is "
                            + (zombie instanceof Zombie again ? "back under its number, " + (session.binder().of(again) != null
                                    ? "on its track" : "on no track") : "not back under its number: " + zombie));
            int orbs = fixture.level.getEntitiesOfClass(ExperienceOrb.class, arena(fixture), Entity::isAlive).size();
            int loot = lying(fixture, Items.BEEF) + lying(fixture, Items.LEATHER) + lying(fixture, Items.ROTTEN_FLESH)
                    + has(enemy, Items.BEEF) + has(enemy, Items.LEATHER);
            fixture.rule(loot == 0 && orbs == 0 && enemy.totalExperience == points[0], "after the pull-back " + loot + " pieces of the loot"
                    + " of the dead and " + orbs + " orbs of experience are left, and the player has " + (enemy.totalExperience - points[0])
                    + " points more than at the press");
            if (cow != null) {
                fixture.base.track(cow);
            }
            if (zombie != null) {
                fixture.base.track(zombie);
            }
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A mob killed in the window is back, and its loot is gone");
        }));
    }

    // E13
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_ends")
    public static void windowEndsCleanly(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-wends", 4, 24, 12);
        window(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer leaver = player(fixture, "kc-ep-wends-out", 12.5D, 2.5D);
        ServerPlayer dier = player(fixture, "kc-ep-wends-dead", 12.5D, 6.5D);
        ServerPlayer mover = player(fixture, "kc-ep-wends-away", 12.5D, 10.5D);
        ServerPlayer changer = player(fixture, "kc-ep-wends-mode", 16.5D, 6.5D);
        leaver.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        leaver.getInventory().selected = 0;
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = EpitaphSessions.predict(user, List.of(leaver, dier, mover, changer), leaver);
            helper.assertTrue(session != null && session.mode() == EpitaphMode.MULTI_WINDOW && session.window() != null,
                    "Setup: the press at four players opened no window");
            session.foreseeAll();
            running[0] = session;
        }));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            WindowJournal journal = session.window();
            ItemEntity dropped = drop(fixture, leaver, Items.DIAMOND);
            Vec3[] stood = {leaver.position(), dier.position(), mover.position(), changer.position()};
            EpitaphSessions.onLogout(new PlayerEvent.PlayerLoggedOutEvent(leaver));
            dier.hurt(dier.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(dier.isDeadOrDying(), "Setup: the second player did not die");
            EpitaphSessions.onChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(mover, Level.OVERWORLD, Level.NETHER));
            changer.setGameMode(GameType.CREATIVE);
            ServerPlayer[] gone = {leaver, dier, mover, changer};
            String[] how = {"logged out", "died", "left the level", "had his game mode changed"};
            for (int one = 0; one < gone.length; one++) {
                WindowJournal.Participant he = journal.of(gone[one]);
                fixture.rule(he != null && he.isHole() && !EpitaphPredictions.isPredictedPlayer(gone[one]), "a player who " + how[one]
                        + " in the window is " + (he != null && he.isHole() ? "a hole" : "no hole") + " and "
                        + (EpitaphPredictions.isPredictedPlayer(gone[one]) ? "still" : "no longer") + " predicted");
            }
            fixture.rule(!WindowJournal.isMade(dropped), "what a player dropped is still written down as his after he logged out: it is"
                    + " his for good, and anybody may take it");
            fixture.rule(!session.ended() && session.window() == journal && WindowJournal.active, "with four players gone the prediction"
                    + " ended as " + session.endedAs() + ", or its window is closed");
            close(fixture, session, leaver, dier, mover, changer);
            for (int one = 0; one < gone.length; one++) {
                fixture.rule(!EpitaphPredictions.isForcedWalking(gone[one]) && gone[one].position().distanceTo(stood[one]) < 1.0E-9D,
                        "at the pull-back a player who " + how[one] + " in the window was "
                                + (EpitaphPredictions.isForcedWalking(gone[one]) ? "walked" : "not walked") + " and moved "
                                + gone[one].position().distanceTo(stood[one]) + " blocks");
            }
            fixture.rule(has(leaver, Items.DIAMOND) == 0 && lying(fixture, Items.DIAMOND) == 1, "the diamond of the player who logged out:"
                    + " he has " + has(leaver, Items.DIAMOND) + " and " + lying(fixture, Items.DIAMOND) + " lie on the ground");
            fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(user) && !WindowJournal.active, "after the pull-back the"
                    + " prediction ended as " + session.endedAs() + ", its user is "
                    + (EpitaphPredictions.isForcedWalking(user) ? "walked" : "not walked") + " and a window is "
                    + (WindowJournal.active ? "still open" : "open no more"));
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A window ends cleanly for who logs out, dies or leaves the level");
        }));
    }

    // 3.6: a window that never reaches its pull-back keeps what was done in it
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_user_out")
    public static void aWindowWhoseUserLeavesKeepsWhatWasDone(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-uout", 4, 24, 8);
        window(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = player(fixture, "kc-ep-uout-him", 12.5D, 2.5D);
        enemy.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        enemy.getInventory().selected = 0;
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            ItemEntity dropped = drop(fixture, enemy, Items.DIAMOND);
            enemy.absMoveTo(enemy.getX() + 2.0D, enemy.getY(), enemy.getZ(), enemy.getYRot(), enemy.getXRot());
            Vec3 stood = enemy.position();
            EpitaphSessions.onLogout(new PlayerEvent.PlayerLoggedOutEvent(user));
            fixture.rule(session.endedAs() == EpitaphEnd.USER_LOGGED_OUT && session.window() == null && !WindowJournal.active
                    && !WindowJournal.isMade(dropped), "the user logged out in his window: the prediction ended as " + session.endedAs()
                            + ", a window is " + (WindowJournal.active ? "still open" : "open no more") + " and what was dropped in it is "
                            + (WindowJournal.isMade(dropped) ? "still written down" : "nobody of a window any more"));
            fixture.rule(has(enemy, Items.DIAMOND) == 0 && lying(fixture, Items.DIAMOND) == 1 && enemy.position().distanceTo(stood) < 1.0E-9D
                    && !EpitaphPredictions.isForcedWalking(enemy) && !EpitaphPredictions.isPredictedPlayer(enemy), "after a window that"
                            + " never reached its pull-back the player has " + has(enemy, Items.DIAMOND) + " diamonds, "
                            + lying(fixture, Items.DIAMOND) + " lie on the ground, he was moved " + enemy.position().distanceTo(stood)
                            + " blocks and is " + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked"));
            fixture.finish("A window whose user leaves keeps what was done in it");
        }));
    }

    // 3.5: a player who has a container open at the press is a hole from the start
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_open_box")
    public static void aPlayerWithAContainerOpenAtThePressIsAHoleFromTheStart(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-obox", 4, 24, 8);
        window(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = player(fixture, "kc-ep-obox-him", 12.5D, 2.5D);
        helper.setBlock(new BlockPos(13, 2, 4), Blocks.CHEST);
        BlockPos box = fixture.absolute(13, 2, 4);
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            helper.assertTrue(fixture.level.getBlockEntity(box) instanceof ChestBlockEntity, "Setup: the chest has no block entity");
            enemy.openMenu((ChestBlockEntity) fixture.level.getBlockEntity(box));
            helper.assertTrue(enemy.containerMenu != enemy.inventoryMenu, "Setup: the chest did not open before the press");
            EpitaphSession session = open(fixture, enemy);
            WindowJournal.Participant he = session.window().of(enemy);
            fixture.rule(he != null && he.isHole() && !EpitaphPredictions.isPredictedPlayer(enemy), "a player with a chest open at the"
                    + " press is " + (he != null && he.isHole() ? "a hole" : "no hole") + " and "
                    + (EpitaphPredictions.isPredictedPlayer(enemy) ? "counts" : "does not count") + " as predicted");
            fixture.rule(enemy.containerMenu != enemy.inventoryMenu, "the press closed the chest he had open: that is a sign");
            Vec3 stood = enemy.position();
            close(fixture, session, enemy);
            fixture.rule(!EpitaphPredictions.isForcedWalking(enemy) && enemy.position().distanceTo(stood) < 1.0E-9D
                    && enemy.containerMenu != enemy.inventoryMenu, "at the pull-back the player with the chest open was "
                            + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked") + ", moved "
                            + enemy.position().distanceTo(stood) + " blocks, and his chest is "
                            + (enemy.containerMenu != enemy.inventoryMenu ? "open" : "closed"));
            fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(user), "with the player a hole from the start the"
                    + " prediction ended as " + session.endedAs() + ", or its user is not pulled back and walked");
            enemy.closeContainer();
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A player with a container open at the press is a hole from the start");
        }));
    }

    private static String nameOf(EntityActionInstance action) {
        return action == null ? "nothing" : action.ability instanceof Ability ability ? ability.name() : String.valueOf(action.ability);
    }

    // Rulings R4 point 3 and R12 point 5, in a window: nothing ends at the press, and a Stand is held to the record from the pull-back
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_window_stand")
    public static void inAWindowAStandGoesOnAndIsHeldFromThePullBack(GameTestHelper helper) {
        // Below the Resolve level from which the barrage of King Crimson asks for low health.
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-wstand", 2, 24, 8);
        fixture.phantomsUse("barrage");
        window(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = player(fixture, "kc-ep-wstand-him", 12.5D, 2.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        StandEntity[] out = new StandEntity[2];
        EntityActionInstance[] own = new EntityActionInstance[2];
        EntityTrack.ActionMark[] taken = new EntityTrack.ActionMark[1];
        helper.runAfterDelay(PRESS_AT - 3, () -> fixture.guarded(() -> {
            out[0] = theWorld(fixture, enemy);
            out[1] = fixture.power.getSummonedStandEntity();
            helper.assertTrue(fixture.base.press(enemy, "barrage", STAND_KEY, InputMethod.HOLD, false).isActionSuccess
                    && fixture.base.press(user, "barrage", KingCrimsonEraseFixture.OTHER_KEY, InputMethod.HOLD, false).isActionSuccess,
                    "Setup: a barrage could not be pressed");
        }));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            own[0] = LivingComponentAction.getComponent(out[0]).getAction();
            own[1] = LivingComponentAction.getComponent(out[1]).getAction();
            helper.assertTrue(own[0] != null && !own[0].isOver() && own[1] != null && !own[1].isOver(), "Setup: three ticks after the"
                    + " keys went down the Stand of the player performs " + nameOf(own[0]) + " and that of the user " + nameOf(own[1]));
            ActionPhase phase = own[0].getPhase();
            EpitaphSession session = open(fixture, enemy);
            fixture.rule(LivingComponentAction.getComponent(out[0]).getAction() == own[0] && !own[0].isOver()
                    && LivingComponentAction.getComponent(out[1]).getAction() == own[1] && !own[1].isOver(), "the press of a window ended"
                            + " what a Stand was doing: that of the player " + (own[0].isOver() ? "ended" : "goes on") + ", that of the user "
                            + (own[1].isOver() ? "ended" : "goes on"));
            fixture.rule(session.binder().of(out[0]) == null && session.binder().of(out[1]) == null, "in a window a Stand is on a track"
                    + " from the press");
            EntityTrack his = session.record().track(out[0].getId());
            EntityTrack mine = session.record().track(out[1].getId());
            taken[0] = his != null ? his.mark(0, LivingComponentAction.MAIN_SLOT) : null;
            fixture.rule(taken[0] != null && taken[0].phase() == phase, "at the press the Stand of the player was at " + phase
                    + " of its barrage, and its copy in the room took over " + (taken[0] == null ? "nothing" : taken[0].phase()));
            fixture.rule(mine != null && mine.mark(0, LivingComponentAction.MAIN_SLOT) == null, "the copy of the Stand of the user took"
                    + " over its barrage, or that Stand has no track: what it does ends when he is pulled back, and no copy goes on with it");
            running[0] = session;
        }));
        helper.runAfterDelay(PRESS_AT + 6, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(session.window() != null && !own[0].isOver(), "Setup: the window is closed, or the barrage the player"
                    + " holds ended by itself");
            close(fixture, session, enemy);
            EntityActionInstance his = LivingComponentAction.getComponent(out[0]).getAction();
            EntityTrack.ActionMark was = taken[0];
            fixture.rule(own[0].isOver() && his != null && his != own[0] && !his.isOver() && "barrage".equals(nameOf(his)) && was != null
                    && his.getPhase() == was.phase() && (int) his.getPhaseTick() == was.phaseTick(), "at the pull-back the Stand"
                            + " of the player performs " + nameOf(his) + (his == null ? "" : " at " + his.getPhase() + " " + his.getPhaseTick())
                            + (his == own[0] ? ", still what it did in the window" : "") + "; its copy took over "
                            + (was == null ? "nothing" : "the barrage at " + was.phase() + " " + was.phaseTick()));
            var keys = enemy.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys;
            fixture.rule(keys.values().stream().noneMatch(entry -> entry.action == own[0] || entry.action == his), "a key of the player"
                    + " still holds a barrage of his Stand after the pull-back: the hold is that of the copy");
            fixture.rule(own[1].isOver(), "what the Stand of the user did in the window goes on after he was pulled back");
            fixture.rule(session.binder().of(out[0]) != null && session.binder().of(out[1]) != null
                    && EpitaphPredictions.isForcedWalking(enemy) && EpitaphPredictions.isForcedWalking(user), "after the pull-back the Stand"
                            + " of the player is " + (session.binder().of(out[0]) != null ? "on its track" : "on no track")
                            + ", that of the user " + (session.binder().of(out[1]) != null ? "on its track" : "on no track")
                            + ", or a player is not walked");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("In a window a Stand goes on, and is held to the record from the pull-back");
        }));
    }

    // 5.4: a Stand is out or in after the pull-back as it was at the press, whatever its user did with it in the window
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_stand_out")
    public static void aStandTakenBackOrCalledOutInTheWindowIsAsItWasAtThePress(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-sout", 4, 24, 12);
        window(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer first = player(fixture, "kc-ep-sout-a", 12.5D, 2.5D);
        ServerPlayer second = player(fixture, "kc-ep-sout-b", 12.5D, 8.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        StandEntity[] stands = new StandEntity[2];
        helper.runAfterDelay(PRESS_AT - 3, () -> fixture.guarded(() -> {
            // The first has his Stand out at the press; the second has the same Stand and keeps it in.
            stands[0] = theWorld(fixture, first);
            theWorld(fixture, second);
            StandPower power = StandPower.get(second);
            power.getPowerType().forceUnsummon(second, power);
            helper.assertTrue(power.getSummonedStandEntity() == null, "Setup: the second player could not take his Stand back");
        }));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = EpitaphSessions.predict(user, List.of(first, second), first);
            helper.assertTrue(session != null && session.mode() == EpitaphMode.MULTI_WINDOW && session.window() != null,
                    "Setup: the press at two players opened no window");
            session.foreseeAll();
            running[0] = session;
        }));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            StandPower his = StandPower.get(first);
            his.getPowerType().forceUnsummon(first, his);
            StandPower other = StandPower.get(second);
            helper.assertTrue(stands[0].isRemoved() && his.getSummonedStandEntity() == null && other.getPowerType().summon(second, other)
                    && other.getSummonedStandEntity() != null, "Setup: in the window the first player did not take his Stand back, or"
                            + " the second did not call his out");
            stands[1] = fixture.base.track(other.getSummonedStandEntity());
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(session.window() != null, "Setup: the window is closed already");
            close(fixture, session, first, second);
            StandEntity again = StandPower.get(first).getSummonedStandEntity();
            fixture.rule(again != null && again != stands[0] && !again.isRemoved() && again.getId() == stands[0].getId()
                    && again.getUUID().equals(stands[0].getUUID()) && session.binder().of(again) != null, "the Stand that was out at the press"
                            + " and taken back in the window is " + (again == null ? "not out" : "out under number " + again.getId() + " ("
                                    + stands[0].getId() + " at the press), " + (session.binder().of(again) != null ? "on its track"
                                            : "on no track")));
            fixture.rule(stands[1].isRemoved() && StandPower.get(second).getSummonedStandEntity() == null, "the Stand that was called out"
                    + " in the window is " + (stands[1].isRemoved() ? "gone" : "still there") + " and its power "
                    + (StandPower.get(second).getSummonedStandEntity() == null ? "has none out" : "has one out"));
            fixture.rule(EpitaphPredictions.isForcedWalking(first) && EpitaphPredictions.isForcedWalking(second), "pulled back, the first"
                    + " player is " + (EpitaphPredictions.isForcedWalking(first) ? "walked" : "not walked") + " and the second "
                    + (EpitaphPredictions.isForcedWalking(second) ? "walked" : "not walked"));
            if (again != null) {
                fixture.base.track(again);
            }
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A Stand is out or in after the pull-back as it was at the press");
        }));
    }

    // 5.4: a commit that fails is taken back step by step, and nobody is pulled back
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_rollback")
    public static void aPullBackThatFailsIsTakenBackAndNobodyIsPulledBack(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-roll", 4, 24, 8);
        window(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = player(fixture, "kc-ep-roll-him", 12.5D, 2.5D);
        enemy.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        enemy.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 4));
        enemy.getInventory().selected = 0;
        BlockPos floor = fixture.absolute(11, 1, 3);
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            drop(fixture, enemy, Items.DIAMOND);
            enemy.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(1));
            enemy.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor).add(0.0D, 0.5D, 0.0D), Direction.UP, floor, false), 1));
            helper.assertTrue(fixture.level.getBlockState(floor.above()).is(Blocks.COBBLESTONE), "Setup: the player set no stone");
            enemy.absMoveTo(enemy.getX() + 2.0D, enemy.getY(), enemy.getZ(), enemy.getYRot(), enemy.getXRot());
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            WindowJournal journal = session.window();
            helper.assertTrue(journal != null && journal.participants().size() >= 2, "Setup: the window is closed, or has one participant");
            Vec3 stood = enemy.position();
            Vec3 userStood = user.position();
            // The fourth step throws: by then what he dropped is removed, his stone is taken away and he himself is put back.
            PullBack.failAtStep = 4;
            close(fixture, session, enemy);
            PullBack.failAtStep = 0;
            int diamonds = has(enemy, Items.DIAMOND) + lying(fixture, Items.DIAMOND);
            fixture.rule(diamonds == 1 && has(enemy, Items.DIAMOND) == 0, "after a pull-back that failed there are " + diamonds
                    + " diamonds of the one he dropped, and he has " + has(enemy, Items.DIAMOND));
            fixture.rule(fixture.level.getBlockState(floor.above()).is(Blocks.COBBLESTONE) && has(enemy, Items.COBBLESTONE) == 3,
                    "after a pull-back that failed the stone he set is " + fixture.level.getBlockState(floor.above()) + " and he has "
                            + has(enemy, Items.COBBLESTONE) + " of 4 left");
            fixture.rule(enemy.position().distanceTo(stood) < 1.0E-6D && user.position().distanceTo(userStood) < 1.0E-6D
                    && !EpitaphPredictions.isForcedWalking(enemy) && !EpitaphPredictions.isForcedWalking(user), "after a pull-back that"
                            + " failed the player stands " + enemy.position().distanceTo(stood) + " blocks and the user "
                            + user.position().distanceTo(userStood) + " blocks from where they stood, and the player is "
                            + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked") + ", the user "
                            + (EpitaphPredictions.isForcedWalking(user) ? "walked" : "not walked"));
            int holes = 0;
            for (WindowJournal.Participant participant : journal.participants()) {
                holes += participant.isHole() ? 1 : 0;
            }
            fixture.rule(holes == journal.participants().size() && !WindowJournal.active, holes + " of " + journal.participants().size()
                    + " participants are holes after a pull-back that failed, and a window is "
                    + (WindowJournal.active ? "still open" : "open no more"));
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A pull-back that fails is taken back, and nobody is pulled back");
        }));
    }

    // 3.5: what a block he set does by itself a tick later, and what the thing that comes of it does, is his and is undone
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_inherit")
    public static void whatASetBlockDoesByItselfIsUndoneWithTheRest(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-sand", 4, 24, 8);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-sand-him", 12.5D, 2.5D);
        enemy.getInventory().setItem(0, new ItemStack(Items.SAND, 4));
        enemy.getInventory().selected = 0;
        // A post two blocks high: he sets sand against its upper block, over air.
        helper.setBlock(new BlockPos(11, 2, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(11, 3, 3), Blocks.STONE);
        BlockPos post = fixture.absolute(11, 3, 3);
        BlockPos set = post.east();
        BlockPos landed = set.below();
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            enemy.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(post).add(0.5D, 0.0D, 0.0D), Direction.EAST, post, false), 1));
            helper.assertTrue(fixture.level.getBlockState(set).is(Blocks.SAND) && has(enemy, Items.SAND) == 3, "Setup: the player set no"
                    + " sand: beside the post is " + fixture.level.getBlockState(set) + " and he has " + has(enemy, Items.SAND) + " of 4");
        }));
        helper.runAfterDelay(PRESS_AT + 20, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            WindowJournal journal = session.window();
            helper.assertTrue(journal != null && fixture.level.getBlockState(set).isAir() && fixture.level.getBlockState(landed).is(Blocks.SAND),
                    "Setup: the window is closed, or the sand did not fall: where he set it is " + fixture.level.getBlockState(set)
                            + " and below it " + fixture.level.getBlockState(landed));
            close(fixture, session, enemy);
            int sand = has(enemy, Items.SAND) + lying(fixture, Items.SAND) + (fixture.level.getBlockState(landed).is(Blocks.SAND) ? 1 : 0)
                    + (fixture.level.getBlockState(set).is(Blocks.SAND) ? 1 : 0);
            WindowJournal.Participant he = journal.of(enemy);
            fixture.rule(sand == 4, "he had 4 blocks of sand and set one, which fell: after the pull-back there are " + sand + " (he has "
                    + has(enemy, Items.SAND) + ", where it fell is " + fixture.level.getBlockState(landed) + ")");
            fixture.rule(he != null && !he.isHole() && has(enemy, Items.SAND) == 4 && fixture.level.getBlockState(landed).isAir()
                    && fixture.level.getBlockState(set).isAir() && EpitaphPredictions.isForcedWalking(enemy), "the sand he set fell by itself:"
                            + " he is " + (he != null && he.isHole() ? "a hole (" + he.why() + ")" : "no hole") + ", has "
                            + has(enemy, Items.SAND) + " of 4, where it fell is " + fixture.level.getBlockState(landed) + " and he is "
                            + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked"));
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("What a set block does by itself is undone with the rest");
        }));
    }

    // 3.5: the advancements and the statistics of a participant are held back for the window
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_earned")
    public static void whatAParticipantEarnsInTheWindowIsHeldBack(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-earn", 4, 24, 8);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-earn-him", 12.5D, 2.5D);
        AdvancementHolder first = fixture.level.getServer().getAdvancements().get(ResourceLocation.withDefaultNamespace("story/root"));
        helper.assertTrue(first != null && !enemy.getAdvancements().getOrStartProgress(first).isDone(), "Setup: the first advancement of"
                + " the game is not there, or the player has it already");
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = open(fixture, enemy);
            int jumps = enemy.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));
            enemy.awardStat(Stats.JUMP);
            boolean earned = enemy.getAdvancements().award(first, "crafting_table");
            int counted = enemy.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) - jumps;
            fixture.rule(counted == 0, "in the window " + counted + " jumps of a participant were counted");
            fixture.rule(!earned && !enemy.getAdvancements().getOrStartProgress(first).isDone(), "in the window a participant earned an"
                    + " advancement");
            close(fixture, session, enemy);
            // Pulled back, what he really does counts again.
            enemy.awardStat(Stats.JUMP);
            counted = enemy.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) - jumps;
            fixture.rule(counted == 1 && enemy.getAdvancements().award(first, "crafting_table"), "after the pull-back " + counted
                    + " jumps of his were counted for the one he made, or an advancement is still held back");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("What a participant earns in the window is held back");
        }));
    }

    // 3.5: what the payload of a mod makes a participant do is his, and is undone with the rest
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_payload")
    public static void whatAPayloadMakesAParticipantDoIsHis(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-pay", 4, 24, 8);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-pay-him", 12.5D, 2.5D);
        BlockPos spot = fixture.absolute(14, 2, 4);
        EpitaphSession[] running = new EpitaphSession[1];
        boolean[] pressed = new boolean[1];
        helper.runAfterDelay(PRESS_AT - 3, () -> fixture.guarded(() -> theWorld(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            // What the loader does with the payload of a mod: its handler is queued as work of the connection it came by.
            ServerPayloadContext came = new ServerPayloadContext(enemy.connection, KingCrimsonAddon.id("a_payload_of_a_test"));
            // A handler that sets a block out of nothing, as the wand of a builder mod sets one out of his inventory: once as
            // work that answers nothing, once as work that answers.
            Runnable plain = () -> fixture.level.setBlockAndUpdate(spot, Blocks.GOLD_BLOCK.defaultBlockState());
            Supplier<Boolean> answering = () -> fixture.level.setBlockAndUpdate(spot.east(), Blocks.IRON_BLOCK.defaultBlockState());
            came.enqueueWork(plain);
            came.enqueueWork(answering);
            // And the key of an ability of the core, which comes by such a payload as well.
            Runnable key = () -> pressed[0] = fixture.base.press(enemy, "punch", STAND_KEY, InputMethod.CLICK, false).isActionSuccess;
            came.enqueueWork(key);
            helper.assertTrue(fixture.level.getBlockState(spot).is(Blocks.GOLD_BLOCK) && fixture.level.getBlockState(spot.east())
                    .is(Blocks.IRON_BLOCK) && pressed[0], "Setup: the work of the payload set no block, or the punch of the Stand was"
                            + " not pressed");
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            WindowJournal journal = session.window();
            helper.assertTrue(journal != null, "Setup: the window is closed already");
            WindowJournal.Participant he = journal.of(enemy);
            fixture.rule(he != null && !he.isHole(), "a participant who pressed an ability of the core in the window is a hole: "
                    + (he == null ? "no participant" : he.why()));
            close(fixture, session, enemy);
            fixture.rule(fixture.level.getBlockState(spot).isAir() && EpitaphPredictions.isForcedWalking(enemy), "the block the payload"
                    + " of a mod set for a participant is " + fixture.level.getBlockState(spot) + " after the pull-back, and he is "
                    + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked"));
            fixture.rule(fixture.level.getBlockState(spot.east()).isAir(), "the block that work of a payload which answers set for a"
                    + " participant is " + fixture.level.getBlockState(spot.east()) + " after the pull-back");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("What a payload makes a participant do is his");
        }));
    }

    // The ruling of the coordinator on entry 29: where the loader no longer has the hook of payloads, no window opens
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_window_cover")
    public static void withoutTheHookOfPayloadsNoWindowOpens(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-cover", 4, 24, 8);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-cover-him", 12.5D, 2.5D);
        boolean had = WindowCover.payloads;
        fixture.base.onClose(() -> WindowCover.payloads = had);
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            MethodType plain = MethodType.methodType(CompletableFuture.class, Runnable.class);
            fixture.rule(had && WindowCover.has(ServerPayloadContext.class, "enqueueWork", plain), "on the loader of this build the"
                    + " method the bracket of payloads hangs on is reported as " + (had ? "there at startup" : "missing at startup")
                    + " and as " + (WindowCover.has(ServerPayloadContext.class, "enqueueWork", plain) ? "there" : "missing") + " now");
            boolean other = WindowCover.has(ServerPayloadContext.class, "enqueueWork", MethodType.methodType(void.class, Runnable.class));
            boolean named = WindowCover.has(ServerPayloadContext.class, "enqueueWorkOfOld", plain);
            fixture.rule(!other && !named, "a method with another descriptor is reported as " + (other ? "there" : "missing")
                    + " and one of another name as " + (named ? "there" : "missing"));
            WindowCover.payloads = false;
            EpitaphSession session = fixture.press(enemy);
            KingCrimsonEpitaphFixture.acceptTeleport(fixture.user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            fixture.rule(session.mode() == EpitaphMode.MULTI_BOUND && session.window() == null && !WindowJournal.active
                    && EpitaphPredictions.isForcedWalking(enemy), "with the hook of payloads missing and the pull-back on, the press began"
                            + " a prediction as " + session.mode() + ", a window is " + (WindowJournal.active ? "open" : "not open")
                            + " and the player under the crosshair is " + (EpitaphPredictions.isForcedWalking(enemy) ? "walked from"
                                    + " the press" : "not walked"));
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("Without the hook of payloads no window opens");
        }));
    }

    // F3: the player a rule of another add-on exempts (the holder of Gold Experience Requiem) in a window
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_exempt")
    public static void requiemPlayerIsNeverPulledBackOrWalked(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-wger", 4, 24, 8);
        window(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer holder = player(fixture, "kc-ep-wger-him", 12.5D, 2.5D);
        holder.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        holder.getInventory().selected = 0;
        // The rule the Requiem add-on will register, for the length of the test: this player holds Requiem.
        EpitaphPredictions.registerExemption(KingCrimsonAddon.id("requiem_of_a_test"), player -> player == holder);
        fixture.base.onClose(() -> EpitaphPredictions.removeExemption(KingCrimsonAddon.id("requiem_of_a_test")));
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = open(fixture, holder);
            fixture.rule(session.window().of(holder) == null && session.window().of(user) != null, "in the window the exempt player is "
                    + (session.window().of(holder) == null ? "no participant" : "a participant") + " and the one who foresees is "
                    + (session.window().of(user) != null ? "one" : "none"));
            fixture.rule(EpitaphPredictions.isPredictedPlayer(holder) && !EpitaphPredictions.isForcedWalking(holder), "at the press the"
                    + " exempt player " + (EpitaphPredictions.isPredictedPlayer(holder) ? "counts" : "does not count")
                    + " as predicted and is " + (EpitaphPredictions.isForcedWalking(holder) ? "walked" : "not walked"));
            running[0] = session;
        }));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            // He does nothing about it: he plays on, drops what he holds and walks away.
            holder.connection.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.DROP_ITEM,
                    BlockPos.ZERO, Direction.DOWN));
            holder.absMoveTo(holder.getX() + 2.0D, holder.getY(), holder.getZ(), holder.getYRot(), holder.getXRot());
            helper.assertTrue(has(holder, Items.DIAMOND) == 0 && lying(fixture, Items.DIAMOND) == 1, "Setup: the exempt player dropped"
                    + " nothing");
            boolean written = false;
            for (ItemEntity item : fixture.level.getEntitiesOfClass(ItemEntity.class, arena(fixture), Entity::isAlive)) {
                written |= WindowJournal.isMade(item);
            }
            fixture.rule(!written, "what the exempt player dropped in the window is written down as the doing of a participant");
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(session.window() != null && EpitaphPredictions.isPredictedPlayer(holder), "Setup: the window is closed, or"
                    + " the exempt player is no longer told");
            Vec3 stood = holder.position();
            close(fixture, session, holder);
            fixture.rule(holder.position().distanceTo(stood) < 1.0E-9D && !EpitaphPredictions.isForcedWalking(holder)
                    && has(holder, Items.DIAMOND) == 0 && lying(fixture, Items.DIAMOND) == 1, "at the pull-back the exempt player was"
                            + " moved " + holder.position().distanceTo(stood) + " blocks and is "
                            + (EpitaphPredictions.isForcedWalking(holder) ? "walked" : "not walked") + "; he has "
                            + has(holder, Items.DIAMOND) + " diamonds and " + lying(fixture, Items.DIAMOND) + " lie where he dropped one");
            fixture.rule(!EpitaphPredictions.isPredictedPlayer(holder), "after the window the exempt player still counts as predicted");
            fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(user), "with an exempt player under the crosshair the"
                    + " prediction ended as " + session.endedAs() + ", or its user is not pulled back and walked");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A player who holds Requiem is never pulled back or walked");
        }));
    }

    // The owner, 2026-11-11: in a window a rider and his mount play on, and are put back together and walked together
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_rider")
    public static void aRiderAndHisMountArePutBackAndWalkedTogether(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-wride", 2, 24, 8);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-wride-him", 14.5D, 2.5D);
        Horse mount = fixture.mob(EntityType.HORSE, 14.5D, 2.5D);
        mount.setTamed(true);
        mount.equipSaddle(new ItemStack(Items.SADDLE), null);
        EpitaphSession[] running = new EpitaphSession[1];
        Vec3[] stood = new Vec3[1];
        helper.runAfterDelay(PRESS_AT - 2, () -> fixture.guarded(
                () -> helper.assertTrue(enemy.startRiding(mount, true), "Setup: the player did not mount the horse")));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            helper.assertTrue(enemy.getVehicle() == mount, "Setup: the player does not ride the horse at the press");
            stood[0] = mount.position();
            EpitaphSession session = open(fixture, enemy);
            EntityTrack rider = session.record().track(enemy.getId());
            EntityTrack ridden = session.record().track(mount.getId());
            WindowJournal.Participant he = session.window().of(enemy);
            fixture.rule(rider != null && ridden != null && he != null && !he.isHole() && WindowJournal.isParticipant(mount),
                    "in a window a rider under the crosshair has " + (rider != null ? "a" : "no") + " phantom and his mount "
                            + (ridden != null ? "one" : "none") + "; he is " + (he != null && !he.isHole() ? "a" : "no")
                            + " participant and his mount is " + (WindowJournal.isParticipant(mount) ? "one" : "none"));
            fixture.rule(enemy.getVehicle() == mount, "at the press of a window the rider was taken off his mount");
            running[0] = session;
        }));
        helper.runAfterDelay(PRESS_AT + 6, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(session.window() != null && enemy.getVehicle() == mount, "Setup: six ticks into the window it is closed,"
                    + " or the rider is off his horse");
            // He gets off and goes away, and the horse is led off: at the pull-back both are as they were at the press.
            enemy.stopRiding();
            Vec3 off = fixture.spot(18.5D, 2.0D, 5.5D);
            enemy.moveTo(off.x, off.y, off.z);
            Vec3 led = fixture.spot(10.5D, 2.0D, 6.5D);
            mount.setPos(led.x, led.y, led.z);
            helper.assertTrue(!enemy.isPassenger() && mount.position().distanceTo(stood[0]) > 3.0D, "Setup: the rider is still on"
                    + " his horse, or the horse was not led off");
            close(fixture, session, enemy);
            fixture.rule(enemy.getVehicle() == mount, "after the pull-back the rider "
                    + (enemy.getVehicle() == mount ? "sits on his horse again" : "is off his horse"));
            double moved = mount.position().distanceTo(stood[0]);
            fixture.rule(moved < 1.0E-6D, "pulled back, the horse stands " + moved + " blocks from where it stood at the press");
            fixture.rule(EpitaphPredictions.isForcedWalking(enemy) && EpitaphSessions.isOnTrack(mount), "after the pull-back the rider"
                    + " is " + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked") + " and his horse is "
                    + (EpitaphSessions.isOnTrack(mount) ? "carried" : "free"));
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("In a window a rider and his mount are put back and walked together");
        }));
    }

    // 3.5, the walls that had no test: a piston and a blast of others at a block a participant set, the merge of an item he
    // dropped, the result of his crafting grid, and the menus the core opens for a player by other ways than a chest
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_window_more_walls")
    public static void theOtherWallsOfAWindowHold(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-walls", 4, 24, 12);
        window(fixture);
        ServerPlayer enemy = player(fixture, "kc-ep-walls-him", 12.5D, 2.5D);
        ServerPlayer third = fixture.other("kc-ep-walls-third", 3.5D, 9.5D);
        enemy.getInventory().setItem(0, new ItemStack(Items.DIRT, 4));
        enemy.getInventory().setItem(1, new ItemStack(Items.DIAMOND));
        enemy.getInventory().selected = 0;
        BlockPos pushed = fixture.absolute(11, 2, 3);
        BlockPos piston = fixture.absolute(10, 2, 3);
        BlockPos power = fixture.absolute(10, 2, 4);
        BlockPos blown = fixture.absolute(14, 2, 4);
        BlockPos control = fixture.absolute(14, 2, 6);
        Vec3 blast = fixture.spot(14.5D, 2.5D, 5.5D);
        Vec3 heap = fixture.spot(17.5D, 2.0D, 2.5D);
        // A piston that looks at where he will set a block, and a block of the test as near to the blast as his will be.
        fixture.level.setBlockAndUpdate(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        fixture.level.setBlockAndUpdate(control, Blocks.DIRT.defaultBlockState());
        fixture.base.onClose(() -> {
            for (BlockPos pos : List.of(pushed, pushed.east(), piston, piston.east(), power, blown, control)) {
                fixture.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        });
        EpitaphSession[] running = new EpitaphSession[1];
        ItemEntity[] heaped = new ItemEntity[2];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> running[0] = open(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            // What his client sends when he sets two blocks of dirt on the floor.
            int sequence = 1;
            for (BlockPos set : List.of(pushed, blown)) {
                enemy.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(set.below()).add(0.0D, 0.5D, 0.0D), Direction.UP, set.below(), false), sequence++));
            }
            helper.assertTrue(fixture.level.getBlockState(pushed).is(Blocks.DIRT) && fixture.level.getBlockState(blown).is(Blocks.DIRT),
                    "Setup: the player set no dirt: " + fixture.level.getBlockState(pushed) + " and " + fixture.level.getBlockState(blown));
            // He throws a diamond away, and a diamond of nobody is laid on the very spot.
            enemy.getInventory().selected = 1;
            heaped[0] = drop(fixture, enemy, Items.DIAMOND);
            heaped[0].setPos(heap.x, heap.y, heap.z);
            heaped[0].setDeltaMovement(Vec3.ZERO);
            heaped[1] = new ItemEntity(fixture.level, heap.x, heap.y, heap.z, new ItemStack(Items.DIAMOND));
            heaped[1].setDeltaMovement(Vec3.ZERO);
            helper.assertTrue(fixture.level.addFreshEntity(heaped[1]) && !WindowJournal.isMade(heaped[1]), "Setup: the diamond of"
                    + " nobody was not added, or the journal took it for his");
            // The result of the crafting grid of his inventory, and the two other ways the core opens a menu for a player.
            boolean takes = enemy.inventoryMenu.getSlot(0).mayPickup(enemy);
            boolean others = third.inventoryMenu.getSlot(0).mayPickup(third);
            fixture.rule(!takes && others, "in the window a participant " + (takes ? "may" : "may not") + " take the result of his"
                    + " crafting grid, and a player outside " + (others ? "may" : "may not") + " take his own");
            boolean external = PlayerOperationPolicies.intercept(enemy, PlayerOperation.MENU_OPEN_EXTERNAL);
            boolean wrapped = PlayerOperationPolicies.intercept(enemy, PlayerOperation.MENU_OPEN_AS_NON_PLAYER);
            boolean outside = PlayerOperationPolicies.intercept(third, PlayerOperation.MENU_OPEN_EXTERNAL)
                    || PlayerOperationPolicies.intercept(third, PlayerOperation.MENU_OPEN_AS_NON_PLAYER);
            fixture.rule(external && wrapped && !outside, "in the window a menu from an external container is "
                    + (external ? "refused" : "opened") + " for a participant, one opened as a non-player " + (wrapped ? "refused" : "opened")
                    + ", and for a player outside one of them is " + (outside ? "refused" : "opened"));
            // Somebody else powers the piston.
            fixture.level.setBlockAndUpdate(power, Blocks.REDSTONE_BLOCK.defaultBlockState());
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            boolean out = fixture.level.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED);
            fixture.rule(!out && fixture.level.getBlockState(pushed).is(Blocks.DIRT) && !fixture.level.getBlockState(pushed.east())
                    .is(Blocks.DIRT), "a piston somebody else powered " + (out ? "pushed" : "did not push") + " the block a participant"
                            + " set in the window: there is now " + fixture.level.getBlockState(pushed) + ", and beyond it "
                            + fixture.level.getBlockState(pushed.east()));
            fixture.level.explode(null, blast.x, blast.y, blast.z, 1.5F, Level.ExplosionInteraction.TNT);
            helper.assertTrue(fixture.level.getBlockState(control).isAir(), "Setup: the blast did not break the dirt of the test beside"
                    + " it: " + fixture.level.getBlockState(control));
            fixture.rule(fixture.level.getBlockState(blown).is(Blocks.DIRT), "a blast of nobody broke the block a participant set in"
                    + " the window, as it broke the one beside it: there is now " + fixture.level.getBlockState(blown));
        }));
        helper.runAfterDelay(PRESS_AT + 50, () -> fixture.guarded(() -> {
            helper.assertTrue(running[0].window() != null, "Setup: the window is closed already");
            int his = heaped[0].isAlive() ? heaped[0].getItem().getCount() : 0;
            int other = heaped[1].isAlive() ? heaped[1].getItem().getCount() : 0;
            fixture.rule(his == 1 && other == 1, "after 47 ticks on one spot the diamond a participant dropped is a stack of " + his
                    + " and the diamond of nobody a stack of " + other + ": they are one heap now");
            running[0].end(EpitaphEnd.CANCELLED);
            fixture.finish("The other walls of a window hold");
        }));
    }
}
