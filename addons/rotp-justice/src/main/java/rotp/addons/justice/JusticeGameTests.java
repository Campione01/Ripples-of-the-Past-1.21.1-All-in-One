package rotp.addons.justice;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.justice.JusticeDisguise.Mode;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The caster is a real server player: it is in the level's player list, and the tests that need it tick it once a world
 * tick, as its connection would. The ticking tests run in a batch of their own, as Fog reaches 150 blocks.
 */
@GameTestHolder(JusticeAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JusticeGameTests {
    private static final short KEY = 1;
    private static final List<String> MOVES = List.of("fog", "puppet", "illusion", "disguise");

    private JusticeGameTests() {}

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 30)
    public static void fogClickDoesNotSummonAndRecallDoesNotClear(GameTestHelper helper) {
        Scene scene = new Scene(helper, 22);
        scene.press("fog", InputMethod.HOLD, false);
        scene.check(!JusticeGameEvents.hasFog(scene.player), "A HOLD executed a click-only Fog");
        scene.press("fog");
        scene.check(JusticeGameEvents.hasFog(scene.player) && !scene.power.isSummoned(),
                "Unsummoned Fog click changed the Stand's manifestation");
        var type = JusticeAddon.STAND.get();
        scene.check(type.summon(scene.player, scene.power), "Could not summon Justice for recall");
        type.forceUnsummon(scene.player, scene.power);
        scene.check(!scene.power.isSummoned() && JusticeGameEvents.hasFog(scene.player),
                "Normal recall turned off Fog");
        scene.press("fog");
        scene.check(!JusticeGameEvents.hasFog(scene.player), "Second Fog click did not turn it off");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 30)
    public static void puppetSphereAndLethalDismissal(GameTestHelper helper) {
        Scene scene = new Scene(helper, 35);
        Cow inside = scene.spawn(EntityType.COW, 0, 149, 0);
        Cow edge = scene.spawn(EntityType.COW, 0, 150, 0);
        Cow outside = scene.spawn(EntityType.COW, 0, 151, 0);
        Cow unwounded = scene.spawn(EntityType.COW, 0, 5, 0);
        inside.setHealth(inside.getMaxHealth() * 0.04F);
        edge.setHealth(edge.getMaxHealth() * 0.05F);
        outside.setHealth(outside.getMaxHealth() * 0.04F);
        Vec3 before = new Vec3(0.5, 0.1, -0.5);
        for (Cow cow : List.of(inside, edge, outside, unwounded)) {
            cow.setDeltaMovement(before);
        }
        scene.press("puppet");
        scene.check(inside.getDeltaMovement().equals(before), "Puppet launched with Fog off");
        scene.press("fog");
        scene.press("puppet");
        Vec3 expected = scene.player.getLookAngle().scale(2.5);
        scene.check(inside.getDeltaMovement().equals(expected) && edge.getDeltaMovement().equals(expected),
                "Puppet did not replace motion within the inclusive 150-block sphere");
        scene.check(outside.getDeltaMovement().equals(before) && unwounded.getDeltaMovement().equals(before),
                "Puppet affected the 151-block or unwounded control");
        inside.setHealth(inside.getMaxHealth());
        scene.press("fog");
        scene.check(!inside.isAlive() && edge.isAlive() && outside.isAlive() && unwounded.isAlive(),
                "Dismissal did not kill just the retained, strictly below-five-percent mob");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 30)
    public static void illusionUsesRealBricksAndKeepsReplacement(GameTestHelper helper) {
        Scene scene = new Scene(helper, 47);
        BlockPos center = scene.player.blockPosition();
        BlockPos front = center.relative(scene.player.getDirection(), 3);
        BlockPos replaced = front.above(4);
        scene.press("illusion");
        scene.check(scene.countBricks(center) == 0, "Illusion created real blocks with Fog off");
        scene.press("fog");
        scene.press("illusion");
        scene.check(scene.countBricks(center) == 143 && scene.level.isEmptyBlock(center)
                && scene.level.isEmptyBlock(front) && scene.level.isEmptyBlock(front.above())
                && scene.level.getBlockState(front.above(2)).is(Blocks.STONE_BRICKS),
                "Actual placement did not create the donor roof, walls, open doorway and empty floor");
        scene.press("illusion");
        scene.check(scene.countBricks(center) == 143, "Repeat click changed the active house");
        scene.level.setBlock(replaced, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        scene.press("fog");
        scene.check(scene.countBricks(center) == 0 && scene.level.getBlockState(replaced).is(Blocks.GOLD_BLOCK),
                "Illusion cleanup erased an intervening different-state block or left its bricks");
        scene.level.removeBlock(replaced, false);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 30)
    public static void disguiseCyclesAndSuppressesOnlyItsCasters(GameTestHelper helper) {
        Scene scene = new Scene(helper, 60);
        Zombie enemy = scene.spawn(EntityType.ZOMBIE, 0, 0, 2);
        FakePlayer ordinary = scene.secondPlayer();
        scene.press("disguise");
        scene.check(JusticeDisguise.getMode(scene.player) == Mode.NONE, "Disguise ran without Fog");
        scene.press("fog");
        scene.press("disguise");
        scene.check(JusticeDisguise.getMode(scene.player) == Mode.VILLAGER, "First click not Villager");
        enemy.setTarget(scene.player);
        scene.check(enemy.getTarget() == null, "An actual monster targeted a Villager guise");
        enemy.setTarget(ordinary);
        scene.check(enemy.getTarget() == ordinary, "Disguise changed an ordinary target");
        scene.press("disguise");
        scene.check(JusticeDisguise.getMode(scene.player) == Mode.ZOMBIE, "Second click not Zombie");
        enemy.setTarget(scene.player);
        scene.check(enemy.getTarget() == null, "An actual monster targeted a Zombie guise");
        scene.press("disguise");
        enemy.setTarget(scene.player);
        scene.check(JusticeDisguise.getMode(scene.player) == Mode.NONE && enemy.getTarget() == scene.player,
                "Third click did not clear the guise and restore ordinary targeting");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 35)
    public static void villagerDiscountIsLiveOnlyAndDoesNotLeak(GameTestHelper helper) {
        Scene scene = new Scene(helper, 73);
        Villager merchant = scene.spawn(EntityType.VILLAGER, 0, 0, 1);
        merchant.setVillagerData(merchant.getVillagerData().setProfession(VillagerProfession.FARMER));
        var offer = new MerchantOffer(new ItemCost(Items.EMERALD, 16), new ItemStack(Items.APPLE), 8, 1, 0.0F);
        merchant.getOffers().clear();
        merchant.getOffers().add(offer);
        scene.press("fog");
        scene.press("disguise");
        scene.player.interactOn(merchant, InteractionHand.MAIN_HAND);
        scene.check(merchant.getTradingPlayer() == scene.player && offer.getSpecialPriceDiff() == -4,
                "Actual merchant interaction did not apply the four-emerald Justice discount");
        offer.addToSpecialPriceDiff(2);
        CompoundTag saved = merchant.saveWithoutId(new CompoundTag());
        CompoundTag row = saved.getCompound("Offers").getList("Recipes", Tag.TAG_COMPOUND).getCompound(0);
        scene.check(row.getInt("specialPrice") == 2 && offer.getSpecialPriceDiff() == -2,
                "Actual villager serialization leaked a live Justice discount or removed it from the active quote");
        scene.press("fog");
        scene.check(offer.getSpecialPriceDiff() == 2 && offer.getUses() == 0,
                "Clear removed another additive modifier or altered stock");
        scene.player.closeContainer();
        scene.check(merchant.getTradingPlayer() == null && offer.getSpecialPriceDiff() == 0,
                "Native session close left a price modifier: " + offer.getSpecialPriceDiff());
        FakePlayer ordinary = scene.secondPlayer();
        ordinary.interactOn(merchant, InteractionHand.MAIN_HAND);
        scene.check(merchant.getTradingPlayer() == ordinary && offer.getSpecialPriceDiff() == 0,
                "A later ordinary customer inherited Justice's discount: " + offer.getSpecialPriceDiff());
        merchant.setTradingPlayer(null);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 30)
    public static void villagerDiscountHoldsOncePerOfferUntilTheModeCycles(GameTestHelper helper) {
        Scene scene = new Scene(helper, 86);
        Villager merchant = scene.spawn(EntityType.VILLAGER, 0, 0, 1);
        merchant.setVillagerData(merchant.getVillagerData().setProfession(VillagerProfession.FARMER));
        var offer = new MerchantOffer(new ItemCost(Items.EMERALD, 16), new ItemStack(Items.APPLE), 8, 1, 0.0F);
        merchant.getOffers().clear();
        merchant.getOffers().add(offer);
        scene.press("fog");
        scene.press("disguise");
        scene.player.interactOn(merchant, InteractionHand.MAIN_HAND);
        scene.check(merchant.getTradingPlayer() == scene.player && offer.getSpecialPriceDiff() == -4,
                "The first session was not priced down by the four emeralds");
        scene.player.closeContainer();
        scene.check(merchant.getTradingPlayer() == null && offer.getSpecialPriceDiff() == 0,
                "Closing the first session left a price modifier: " + offer.getSpecialPriceDiff());
        scene.player.interactOn(merchant, InteractionHand.MAIN_HAND);
        scene.check(merchant.getTradingPlayer() == scene.player && offer.getSpecialPriceDiff() == 0,
                "The donor priced an offer down once; the second session of the same villager changed it by "
                        + offer.getSpecialPriceDiff());
        scene.player.closeContainer();
        MerchantOffer later = new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.BREAD), 8, 1, 0.0F);
        merchant.getOffers().add(later);
        scene.player.interactOn(merchant, InteractionHand.MAIN_HAND);
        scene.check(offer.getSpecialPriceDiff() == 0 && later.getSpecialPriceDiff() == -2,
                "An offer the villager gained later was not priced down once, or the first was priced down again: "
                        + offer.getSpecialPriceDiff() + " and " + later.getSpecialPriceDiff());
        scene.player.closeContainer();
        scene.press("disguise");
        scene.press("disguise");
        scene.press("disguise");
        scene.check(JusticeDisguise.getMode(scene.player) == Mode.VILLAGER, "Setup: the mode did not come round to Villager");
        scene.player.interactOn(merchant, InteractionHand.MAIN_HAND);
        scene.check(offer.getSpecialPriceDiff() == -4 && later.getSpecialPriceDiff() == -2,
                "After the mode cycled the offers were not priced down again: "
                        + offer.getSpecialPriceDiff() + " and " + later.getSpecialPriceDiff());
        scene.player.closeContainer();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 30)
    public static void stunnedPerformerRefusesEveryMove(GameTestHelper helper) {
        Scene scene = new Scene(helper, 99);
        for (String move : MOVES) {
            scene.check(scene.gateAdmits(move), "Setup: " + move + " was refused before any stun");
        }
        scene.check(scene.player.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40)),
                "Could not stun the caster");
        for (String move : MOVES) {
            scene.check(!scene.gateAdmits(move), move + " was admitted for a stunned caster with no Stand out");
        }
        scene.player.removeEffect(ModStatusEffects.STUN);
        scene.check(JusticeAddon.STAND.get().summon(scene.player, scene.power), "Could not summon Justice");
        for (String move : MOVES) {
            scene.check(scene.gateAdmits(move), move + " was refused with a Stand out and nobody stunned");
        }
        scene.check(scene.power.getSummonedStandEntity().addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40)),
                "Could not stun the Stand");
        for (String move : MOVES) {
            scene.check(!scene.gateAdmits(move), move + " was admitted while the performing Stand was stunned");
        }
        scene.check(!JusticeGameEvents.hasFog(scene.player), "A refused Fog click changed the caster's state");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 40, batch = "justiceFogDefence")
    public static void fogEnlistsAndDefendsWhileTheCasterTicks(GameTestHelper helper) {
        Scene scene = new Scene(helper, 112);
        Cow dying = scene.spawn(EntityType.COW, 3, 0, 0);
        Cow edge = scene.spawn(EntityType.COW, 0, 0, 3);
        Cow healthy = scene.spawn(EntityType.COW, -3, 0, 0);
        Cow far = scene.spawn(EntityType.COW, 0, 151, 0);
        Spider attacker = scene.spawn(EntityType.SPIDER, 0, 0, -4);
        dying.setHealth(dying.getMaxHealth() * 0.04F);
        edge.setHealth(edge.getMaxHealth() * 0.05F);
        far.setHealth(far.getMaxHealth() * 0.04F);
        scene.press("fog");
        scene.startTicking();
        helper.runAfterDelay(8, () -> {
            scene.check(dying.getTarget() == null, "Setup: a retained puppet targeted something with nobody attacking");
            attacker.setTarget(scene.player);
            scene.check(attacker.getTarget() == scene.player, "Setup: the monster did not take the caster as its target");
        });
        helper.runAfterDelay(16, () -> {
            scene.check(dying.getTarget() == attacker,
                    "The mob below five percent, enlisted by the caster's own tick, did not defend him: " + dying.getTarget());
            scene.check(edge.getTarget() == null && healthy.getTarget() == null && far.getTarget() == null,
                    "A mob at exactly five percent, a healthy one or one beyond 150 blocks was enlisted");
            scene.press("fog");
            scene.check(!dying.isAlive() && edge.isAlive() && healthy.isAlive() && far.isAlive() && attacker.isAlive(),
                    "Fog off did not kill exactly the enlisted mob");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 75, batch = "justiceFogParticles")
    public static void fogCloudsAndWoundSmokeReachTheCaster(GameTestHelper helper) {
        Scene scene = new Scene(helper, 112);
        Cow earlier = scene.spawn(EntityType.COW, -4, 0, 0);
        Cow later = scene.spawn(EntityType.COW, 4, 0, 0);
        scene.startTicking();
        scene.check(earlier.hurt(scene.level.damageSources().generic(), 1.0F), "Setup: the first cow was not hurt");
        helper.runAfterDelay(12, () -> {
            scene.check(scene.particles(ParticleTypes.CLOUD, Double.NaN).isEmpty()
                            && scene.particles(ParticleTypes.SMOKE, Double.NaN).isEmpty(),
                    "Fog clouds or wound smoke appeared with Fog off");
            scene.press("fog");
            scene.listener.sent.clear();
            scene.check(later.hurt(scene.level.damageSources().generic(), 1.0F), "Setup: the second cow was not hurt");
        });
        helper.runAfterDelay(45, () -> {
            scene.check(!scene.particles(ParticleTypes.CLOUD, Double.NaN).isEmpty(),
                    "No Fog cloud reached the caster in forty ticks of Fog");
            scene.check(!scene.particles(ParticleTypes.SMOKE, later.getX()).isEmpty(),
                    "The cow hurt within the fog gave off no wound smoke");
            scene.check(scene.particles(ParticleTypes.SMOKE, earlier.getX()).isEmpty(),
                    "The cow hurt before the fog was on gave off wound smoke");
            scene.check(scene.particles(ParticleTypes.SMOKE, later.getX()).stream().allMatch(packet -> packet.getCount() == 8),
                    "Wound smoke was not eight particles a burst");
            scene.press("fog");
            scene.listener.sent.clear();
        });
        helper.runAfterDelay(60, () -> {
            scene.check(scene.particles(ParticleTypes.CLOUD, Double.NaN).isEmpty(),
                    "Fog clouds went on after Fog was turned off");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 60, batch = "justiceDisguiseScan")
    public static void disguiseScanClearsMonstersAndSetsGolemTargets(GameTestHelper helper) {
        Scene scene = new Scene(helper, 112);
        Spider hunter = scene.spawn(EntityType.SPIDER, 0, 0, 5);
        IronGolem golem = scene.spawn(EntityType.IRON_GOLEM, 5, 0, 0);
        hunter.setTarget(scene.player);
        scene.check(hunter.getTarget() == scene.player, "Setup: the monster did not take the caster as its target");
        scene.press("fog");
        scene.press("disguise");
        scene.startTicking();
        helper.runAfterDelay(12, () -> {
            scene.check(hunter.getTarget() == null, "The ten-tick scan left a monster that already targeted the disguised caster");
            golem.setTarget(scene.player);
            scene.check(golem.getTarget() == scene.player, "Setup: the golem did not take the caster as its target");
        });
        helper.runAfterDelay(24, () -> {
            scene.check(golem.getTarget() == null, "A Villager guise kept a golem's attack on its caster");
            scene.press("disguise");
            scene.check(JusticeDisguise.getMode(scene.player) == Mode.ZOMBIE, "Setup: the second click was not Zombie");
        });
        helper.runAfterDelay(36, () -> {
            scene.check(golem.getTarget() == scene.player, "A Zombie guise did not turn a golem on its caster");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 30)
    public static void disguiseAndFogFollowTheUuidAndArePublishedOnRespawn(GameTestHelper helper) {
        Scene scene = new Scene(helper, 125);
        scene.press("fog");
        scene.press("disguise");
        scene.check(JusticeDisguise.getMode(scene.player) == Mode.VILLAGER, "Setup: the first click was not Villager");
        Caster reborn = scene.replacement();
        scene.check(reborn.player != scene.player && reborn.player.getUUID().equals(scene.player.getUUID()),
                "Setup: the replacement must be another object with the same UUID");
        scene.check(JusticeDisguise.getMode(reborn.player) == Mode.VILLAGER && JusticeGameEvents.hasFog(reborn.player),
                "A new player object of the same UUID lost the guise or Fog");
        reborn.listener.sent.clear();
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(reborn.player, true));
        JusticeVisualPayload sent = null;
        for (Packet<?> packet : reborn.listener.sent) {
            if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof JusticeVisualPayload payload) {
                sent = payload;
            }
        }
        scene.check(sent != null && sent.fog() && sent.mode() == Mode.VILLAGER
                        && sent.owner().equals(reborn.player.getUUID())
                        && sent.dimension().equals(scene.level.dimension().location()),
                "The respawned player was not sent its Fog and guise: " + sent);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = JusticeAddon.MOD_ID, timeoutTicks = 10)
    public static void donorGreenSelectionFollowsTheFourDonorRules(GameTestHelper helper) {
        for (Mode mode : Mode.values()) {
            for (String move : MOVES) {
                boolean fogOnly = !move.equals("disguise");
                helper.assertTrue(!JusticeSelectionPredicates.donorGreenSelection(move, false, mode),
                        move + " was green with Fog off in " + mode);
                helper.assertTrue(JusticeSelectionPredicates.donorGreenSelection(move, true, mode) == (fogOnly || mode != Mode.NONE),
                        move + " has the wrong green state with Fog on in " + mode);
            }
            helper.assertTrue(!JusticeSelectionPredicates.donorGreenSelection("manual_control", true, mode),
                    "Another move was green");
        }
        helper.succeed();
    }

    private record Caster(ServerPlayer player, CapturingListener listener) {}

    // Everything the server sends to the caster; the particle and payload tests read it.
    private static final class CapturingListener extends ServerGamePacketListenerImpl {
        final List<Packet<?>> sent = new ArrayList<>();

        CapturingListener(MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {
            sent.add(packet);
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            sent.add(packet);
        }
    }

    private static final class Scene implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final Vec3 origin;
        final ServerPlayer player;
        final CapturingListener listener;
        final StandPower power;
        final List<Mob> mobs = new ArrayList<>();
        final List<Caster> replacements = new ArrayList<>();
        boolean closed;

        Scene(GameTestHelper helper, int height) {
            this.helper = helper;
            level = helper.getLevel();
            BlockPos base = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(base);
            origin = new Vec3(chunk.getMiddleBlockX() + 0.5, base.getY() + height, chunk.getMiddleBlockZ() + 0.5);
            Caster caster = newCaster(new GameProfile(UUID.randomUUID(), "JusticeCaster"));
            player = caster.player();
            listener = caster.listener();
            power = PowerClass.STAND.attachGet(player);
            helper.testInfo.addListener(this);
            check(StandPowerTransitions.insert(power, new StandInstance(JusticeAddon.STAND.get())).applied(),
                    "Could not give the registered Justice Stand to the player");
        }

        // As the player's connection ticks it: PlayerTickEvent comes from doTick, not from the level's tick.
        void startTicking() {
            helper.onEachTick(() -> {
                if (!closed) {
                    player.doTick();
                }
            });
        }

        FakePlayer secondPlayer() {
            FakePlayer actor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "JusticeFixture"));
            actor.moveTo(origin.x, origin.y, origin.z, 0, 0);
            return actor;
        }

        // Leaving the End: a new player object of the same profile takes the old one's place in the level.
        Caster replacement() {
            player.discard();
            Caster reborn = newCaster(player.getGameProfile());
            replacements.add(reborn);
            return reborn;
        }

        private Caster newCaster(GameProfile profile) {
            MinecraftServer server = level.getServer();
            ServerPlayer caster = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            CapturingListener packets = new CapturingListener(
                    server, connection, caster, CommonListenerCookie.createInitial(profile, false));
            caster.moveTo(origin.x, origin.y, origin.z, 0, 0);
            level.addNewPlayer(caster);
            caster.setGameMode(GameType.SURVIVAL);
            caster.setNoGravity(true);
            caster.setDeltaMovement(Vec3.ZERO);
            return new Caster(caster, packets);
        }

        <T extends Mob> T spawn(EntityType<T> type, double dx, double dy, double dz) {
            T actor = type.create(level);
            check(actor != null, "Could not create " + type);
            actor.setNoAi(true);
            actor.setNoGravity(true);
            actor.moveTo(origin.x + dx, origin.y + dy, origin.z + dz, 0, 0);
            mobs.add(actor);
            check(level.addFreshEntity(actor), "Could not add " + type);
            return actor;
        }

        // Particle packets of one kind that reached the caster; x is the burst's x within a block, or NaN for any.
        List<ClientboundLevelParticlesPacket> particles(ParticleOptions type, double x) {
            List<ClientboundLevelParticlesPacket> found = new ArrayList<>();
            for (Packet<?> packet : listener.sent) {
                if (packet instanceof ClientboundLevelParticlesPacket particle && particle.getParticle() == type
                        && (Double.isNaN(x) || Math.abs(particle.getX() - x) <= 1.0)) {
                    found.add(particle);
                }
            }
            return found;
        }

        void press(String id) {
            press(id, InputMethod.CLICK, true);
        }

        void press(String id, InputMethod method, boolean expectedSuccess) {
            var ability = power.getAbility(id);
            var selected = resolve(id);
            check(selected != null && AbilityInput.withConditionCheck(selected, player, method),
                    "Registered Justice input was refused: " + id);
            BufferingState state = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, player, null, method, 0, state, ability.getAbilityId());
            check(state.isActionSuccess == expectedSuccess, "Wrong input result for " + id + " " + method);
            AbilityInput.keyRelease(KEY, player);
        }

        // The server's input gate alone.
        boolean gateAdmits(String id) {
            var selected = resolve(id);
            return selected != null && AbilityInput.withConditionCheck(selected, player, InputMethod.CLICK);
        }

        // The power resolves its keys once a tick (Power.updateAvailableMoves); a press made in the tick of another needs a fresh one.
        private AbilityConditionCheck resolve(String id) {
            try {
                Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
            }
            return power.updateAvailableMoves().getContextVariationContainer(power.getAbility(id));
        }

        int countBricks(BlockPos center) {
            int count = 0;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, 0, -3), center.offset(3, 4, 3))) {
                if (level.getBlockState(pos).is(Blocks.STONE_BRICKS)) {
                    count++;
                }
            }
            return count;
        }

        void check(boolean ok, String detail) {
            helper.assertTrue(ok, detail);
        }

        void close() {
            if (closed) return;
            closed = true;
            List<ServerPlayer> casters = new ArrayList<>();
            casters.add(player);
            replacements.forEach(reborn -> casters.add(reborn.player()));
            casters.forEach(caster -> JusticeGameEvents.logout(new PlayerEvent.PlayerLoggedOutEvent(caster)));
            if (power.isSummoned()) {
                JusticeAddon.STAND.get().forceUnsummon(player, power);
            }
            mobs.forEach(Mob::discard);
            casters.forEach(ServerPlayer::discard);
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) { close(); }
    }
}
