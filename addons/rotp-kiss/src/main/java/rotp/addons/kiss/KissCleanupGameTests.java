package rotp.addons.kiss;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.addons.kiss.entity.KissStickerEntity;
import rotp.addons.kiss.service.KissService;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * R772.1 OQ-A01: the walk over every entity of every level that takes a lost Kiss user's stickers and bombs away is made
 * once, when he loses the Stand, and not every 10 ticks for every player who has no Kiss.
 */
@GameTestHolder(KissAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KissCleanupGameTests {
    private static final int TICKS_WATCHED = 200;

    private KissCleanupGameTests() {}

    @GameTest(template = "empty", templateNamespace = KissAddon.MOD_ID, timeoutTicks = 20)
    public static void aPlayerWithoutKissCostsNoWalkAndALostKissIsCleanedOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer bystander = player(helper, "KissBystander");
        FakePlayer owner = player(helper, "KissOwner");
        StandPower power = PowerClass.STAND.attachGet(owner);
        KissStickerEntity sticker = new KissStickerEntity(KissAddon.STICKER_ENTITY.get(), level);
        try {
            int walks = KissService.ownerWalks();
            tickEvery10(bystander, 0, TICKS_WATCHED);
            helper.assertTrue(KissService.ownerWalks() == walks,
                    "A player without Kiss must cost no walk over the levels, made "
                            + (KissService.ownerWalks() - walks) + " in " + TICKS_WATCHED + " ticks");

            power.setStand(KissAddon.KISS.get());
            helper.assertTrue(KissAddon.KISS.get().summon(owner, power), "Fixture: Kiss's summon failed");
            tick(owner, 10);
            sticker.attachBlock(owner, power.getSummonedStandEntity(), helper.absolutePos(BlockPos.ZERO), Direction.UP);
            helper.assertTrue(level.addFreshEntity(sticker), "Fixture: the sticker was not added");
            helper.assertTrue(KissService.ownerWalks() == walks, "Fixture: a Kiss user walked the levels");

            power.setStand(ModStands.STAR_PLATINUM.get());
            tick(owner, 20);
            helper.assertTrue(sticker.isRemoved() && KissService.ownerWalks() == walks + 1,
                    "The loss of Kiss must take its sticker away with one walk; removed " + sticker.isRemoved()
                            + ", walks " + (KissService.ownerWalks() - walks));
            tickEvery10(owner, 30, TICKS_WATCHED);
            helper.assertTrue(KissService.ownerWalks() == walks + 1,
                    "Once cleaned up, a player without Kiss must cost no more walks, made "
                            + (KissService.ownerWalks() - walks - 1));
        }
        finally {
            sticker.discard();
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            level.removePlayerImmediately(bystander, Entity.RemovalReason.DISCARDED);
            level.removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    /**
     * R772.1 OQ-C04: the HUD lights a slot whose skill goes on with the Stand recalled. A sticker of the user's goes on
     * after Kiss is recalled, and the sticker slots say so while it is there; the bomb and pull slots do not.
     */
    @GameTest(template = "empty", templateNamespace = KissAddon.MOD_ID, timeoutTicks = 20)
    public static void theStickerSlotsSayAStickerGoesOnWithKissRecalled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer owner = player(helper, "KissSign");
        StandPower power = PowerClass.STAND.attachGet(owner);
        KissStickerEntity sticker = new KissStickerEntity(KissAddon.STICKER_ENTITY.get(), level);
        try {
            power.setStand(KissAddon.KISS.get());
            helper.assertTrue(KissAddon.KISS.get().summon(owner, power), "Fixture: Kiss's summon failed");
            Ability place = power.getAbility(KissAddon.STICKER_PLACE);
            Ability launch = power.getAbility(KissAddon.STICKER);
            Ability bomb = power.getAbility(KissAddon.BOMB);
            Ability pull = power.getAbility(KissAddon.PULL);
            helper.assertTrue(!place.cl_runsWithStandRecalled(owner), "With no sticker out the slot must not say so");
            sticker.attachBlock(owner, power.getSummonedStandEntity(), helper.absolutePos(BlockPos.ZERO), Direction.UP);
            helper.assertTrue(level.addFreshEntity(sticker), "Fixture: the sticker was not added");
            KissAddon.KISS.get().forceUnsummon(owner, power);
            helper.assertTrue(place.cl_runsWithStandRecalled(owner) && launch.cl_runsWithStandRecalled(owner),
                    "A sticker of the user's goes on with Kiss recalled: its slots must say so");
            helper.assertTrue(!bomb.cl_runsWithStandRecalled(owner) && !pull.cl_runsWithStandRecalled(owner),
                    "No bomb flies and the pull is a hold: their slots must not say so");
            sticker.discard();
            helper.assertTrue(!place.cl_runsWithStandRecalled(owner), "With the sticker gone the slot must not say so");
        }
        finally {
            sticker.discard();
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            level.removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    // the server's player tick, at the ticks KissEvents looks at
    private static void tickEvery10(FakePlayer player, int from, int ticks) {
        for (int tick = from; tick < from + ticks; tick += 10) {
            tick(player, tick);
        }
    }

    private static void tick(FakePlayer player, int tickCount) {
        player.tickCount = tickCount;
        KissEvents.onPlayerTick(new PlayerTickEvent.Post(player));
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos origin = helper.absolutePos(new BlockPos(0, 2, 0));
        player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        player.setNoGravity(true);
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
