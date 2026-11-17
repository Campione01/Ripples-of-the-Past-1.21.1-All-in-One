package rotp.addons.kingcrimson.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.replay.ActionMirror;
import rotp.addons.kingcrimson.mixin.epitaph.PlayerListAccessor;

/**
 * Epitaph, the barrier of the muted side itself (EPITAPH-ARCH, ruling R13 point 7). An action of the record ticks on its
 * real performer in the muted mode: it runs, and what it writes to the world is absorbed, since the ops of the record do
 * that. No ability of the table writes a block or adds an entity from its own tick; the abilities of other add-ons will.
 * So the test enters the mode and makes each kind of write itself.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphMutedWallGameTests {
    private static final int PRESS_AT = 5;

    private KingCrimsonEpitaphMutedWallGameTests() {}

    private static int count(ServerPlayer to, int since, Class<?> kind) {
        int count = 0;
        for (Packet<?> packet : KingCrimsonEpitaphFixture.flat(KingCrimsonEraseFixture.listener(to).sentSince(since))) {
            if (kind.isInstance(packet)) {
                count++;
            }
        }
        return count;
    }

    // Ruling R13, point 7
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_muted_wall")
    public static void whatAMutedTickWritesIsAbsorbed(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-muted", 2, 16, 10);
        fixture.budget(0);
        fixture.steadyHealth();
        ServerLevel level = fixture.level;
        // He tracks what is around him, and is sent what the server really sends.
        ServerPlayer beside = fixture.witness("kc-ep-muted-by", 3.5D, 6.5D);
        Zombie bound = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 9.5D, 2.5D));
        Cow near = fixture.mob(EntityType.COW, 12.5D, 7.5D);
        BlockPos set = fixture.absolute(6, 2, 5);
        BlockPos kept = fixture.absolute(6, 2, 7);
        BlockPos fragile = fixture.absolute(12, 2, 5);
        Vec3 blast = fixture.spot(12.5D, 2.5D, 6.5D);
        Vec3 placed = Vec3.atCenterOf(set);
        int[] heard = new int[1];
        fixture.listen(VanillaGameEvent.class, event -> {
            if (event.getVanillaEvent().value() == GameEvent.BLOCK_PLACE.value() && event.getEventPosition().distanceToSqr(placed) < 0.01D) {
                heard[0]++;
            }
        });
        fixture.base.onClose(() -> {
            for (BlockPos pos : List.of(set, kept, fragile)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            bound.removeEffect(MobEffects.GLOWING);
        });
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            level.setBlockAndUpdate(kept, Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(fragile, Blocks.GLASS.defaultBlockState());
            EpitaphSession session = fixture.predict(bound);
            helper.assertTrue(EpitaphSessions.isOnTrack(bound) && level.getBlockState(set).isAir(), "Setup: the zombie is on no track,"
                    + " or the place of the test is not free");
            // Made before the picture is taken: making an item draws from the random source of the level.
            ItemEntity dropped = new ItemEntity(level, placed.x, placed.y, placed.z, new ItemStack(Items.DIAMOND));
            KingCrimsonEpitaphWorldPicture before = KingCrimsonEpitaphWorldPicture.of(level, fixture.absolute(-1, 0, -1),
                    fixture.absolute(16, 8, 10));
            float health = near.getHealth();
            // For the length of the writes he is on the list of the server, as a real player is: sounds and level events go by it.
            List<ServerPlayer> listed = ((PlayerListAccessor) level.getServer().getPlayerList()).kingCrimson$players();
            int sent = KingCrimsonEraseFixture.listener(beside).sentCount();
            int had = heard[0];
            listed.add(beside);
            try {
                ActionMirror.muted(session, 1, () -> {
                    level.setBlock(set, Blocks.STONE.defaultBlockState(), 3);
                    level.destroyBlock(kept, true);
                    level.addFreshEntity(dropped);
                    level.explode(null, blast.x, blast.y, blast.z, 4.0F, Level.ExplosionInteraction.TNT);
                    level.playSound(null, set, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.sendParticles(ParticleTypes.FLAME, placed.x, placed.y, placed.z, 5, 0.1D, 0.1D, 0.1D, 0.0D);
                    level.levelEvent(2001, set, Block.getId(Blocks.STONE.defaultBlockState()));
                    level.gameEvent(null, GameEvent.BLOCK_PLACE, set);
                    level.scheduleTick(set, Blocks.STONE, 5);
                    bound.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100));
                });
            }
            finally {
                listed.remove(beside);
            }
            fixture.rule(level.getBlockState(set).isAir(), "a block set in a muted tick is set: " + level.getBlockState(set));
            fixture.rule(level.getBlockState(kept).is(Blocks.DIRT), "a block broken in a muted tick is broken: " + level.getBlockState(kept));
            fixture.rule(level.getEntity(dropped.getId()) == null, "an entity added in a muted tick is in the world");
            fixture.rule(level.getBlockState(fragile).is(Blocks.GLASS) && near.getHealth() == health, "a blast in a muted tick broke the"
                    + " glass beside it (" + level.getBlockState(fragile) + ") or hurt the cow beside it (" + health + " -> "
                    + near.getHealth() + ")");
            int sounds = count(beside, sent, ClientboundSoundPacket.class);
            fixture.rule(sounds == 0, "a sound made in a muted tick was sent to a player beside it, " + sounds + " times");
            int particles = count(beside, sent, ClientboundLevelParticlesPacket.class);
            fixture.rule(particles == 0, "particles made in a muted tick were sent to a player beside them, " + particles + " times");
            int events = count(beside, sent, ClientboundLevelEventPacket.class);
            fixture.rule(events == 0, "a level event of a muted tick was sent to a player beside it, " + events + " times");
            fixture.rule(heard[0] == had, "a game event of a muted tick was posted, " + (heard[0] - had) + " times");
            fixture.rule(!level.getBlockTicks().hasScheduledTick(set, Blocks.STONE), "a tick scheduled in a muted tick is scheduled");
            fixture.rule(!bound.hasEffect(MobEffects.GLOWING), "an effect put on a bound body in a muted tick is on it");
            List<String> changed = before.differences(KingCrimsonEpitaphWorldPicture.of(level, fixture.absolute(-1, 0, -1),
                    fixture.absolute(16, 8, 10)));
            fixture.rule(changed.isEmpty(), "after the writes of a muted tick the world is not as it was: " + changed.size()
                    + " differences, the first: " + (changed.isEmpty() ? "" : changed.get(0)));
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("What a muted tick writes is absorbed");
        }));
    }
}
