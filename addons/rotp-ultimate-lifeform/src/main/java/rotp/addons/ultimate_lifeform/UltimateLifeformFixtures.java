package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.core.impl.powers.hamon.entity.HamonSendoOverdriveEntity;
import rotp.core.impl.powers.pillarman.PillarmanDivineSandstormEntity;

/**
 * Users and what they send out, for the game tests of this module. A user with a fixed UUID cannot join a server again
 * while an entity with that UUID is still in the level (a failed test leaves it there), and FakePlayerFactory hands out
 * the same, once removed, instance for a profile; so a fixture builds a new player, clears the UUID before it joins,
 * and has the player and the scales, tentacles and waves it sent removed when its test ends, passed or not.
 */
final class UltimateLifeformFixtures implements GameTestListener {
    // A scale flies 2 blocks a tick for 60 ticks, a Sandstorm wave 60 blocks.
    private static final double REACH = 160.0D;
    private final ServerLevel level;
    private final Entity user;

    private UltimateLifeformFixtures(ServerLevel level, Entity user) {
        this.level = level;
        this.user = user;
    }

    /** Removes the entity of the level that still has this UUID. */
    static void clearUuid(ServerLevel level, UUID uuid) {
        Entity lingering = level.getEntity(uuid);
        if (lingering != null) {
            lingering.discard();
        }
    }

    /** Call before the user joins the level: clears its UUID and removes it, with what it sent, as its test ends. */
    static <T extends Entity> T claim(GameTestHelper helper, T user) {
        ServerLevel level = helper.getLevel();
        clearUuid(level, user.getUUID());
        helper.testInfo.addListener(new UltimateLifeformFixtures(level, user));
        return user;
    }

    /** Scales, tentacles, Sandstorm waves and Sendo waves of the user that are still in the level. */
    static List<Entity> sentBy(ServerLevel level, Entity owner) {
        List<Entity> sent = new ArrayList<>();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, owner.getBoundingBox().inflate(REACH),
                candidate -> !candidate.isRemoved() && sends(candidate, owner))) {
            sent.add(entity);
        }
        return sent;
    }

    /** Removes what the user sent and checks that none of it is left. */
    static void discardSent(GameTestHelper helper, Entity owner) {
        sentBy(helper.getLevel(), owner).forEach(Entity::discard);
        List<Entity> left = sentBy(helper.getLevel(), owner);
        helper.assertTrue(left.isEmpty(), "Entities sent by " + owner.getName().getString() + " are left after the"
                + " test: " + left);
    }

    private static boolean sends(Entity entity, Entity owner) {
        if (entity instanceof UltimateScaleProjectileEntity || entity instanceof UltimateProjectileEntity
                || entity instanceof PillarmanDivineSandstormEntity) {
            return ((Projectile) entity).getOwner() == owner;
        }
        if (entity instanceof UltimateAreaEffectEntity area) {
            return area.getOwner() == owner;
        }
        if (entity instanceof HamonSendoOverdriveEntity) {
            CompoundTag saved = new CompoundTag();
            entity.saveWithoutId(saved);
            return saved.hasUUID("Owner") && saved.getUUID("Owner").equals(owner.getUUID());
        }
        return false;
    }

    private void end() {
        sentBy(level, user).forEach(Entity::discard);
        user.discard();
    }

    @Override
    public void testStructureLoaded(GameTestInfo test) {}

    @Override
    public void testPassed(GameTestInfo test, GameTestRunner runner) {
        end();
    }

    @Override
    public void testFailed(GameTestInfo test, GameTestRunner runner) {
        end();
    }

    @Override
    public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
        end();
    }
}
