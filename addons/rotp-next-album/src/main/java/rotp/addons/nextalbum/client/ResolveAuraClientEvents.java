package rotp.addons.nextalbum.client;

import java.util.Random;
import java.util.Set;

import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.impl.powers.hamon.client.particle.custom.FirstPersonHamonAura;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.client.particle.ResolveAuraParticle;
import rotp.addons.nextalbum.config.NextAlbumClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Next Album 0.1.13 ResolveAuraClientEvents: a player under Resolve (or,
 * with the option, with a summoned Stand) gives off an aura in the
 * Stand's colour, on the body, the first-person arms and the Stand.
 */
@EventBusSubscriber(modid = NextAlbumIds.MOD_ID, value = Dist.CLIENT)
public final class ResolveAuraClientEvents {
    // 0.1.13 limited the aura to its own namespace; the port's Next Album
    // Stands live in these modules.
    private static final Set<String> NEXT_ALBUM_STAND_NAMESPACES = Set.of(
            NextAlbumIds.MOD_ID, "rotp_sex_pistols", "rotp_grateful_dead",
            "rotp_beach_boy");
    private static final Random RANDOM = new Random();

    private ResolveAuraClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        SpriteSet sprites = ResolveAuraParticle.sprites();
        if (level == null || minecraft.player == null || minecraft.isPaused()
                || sprites == null) {
            return;
        }
        boolean forAll = NextAlbumClientConfig.resolveAuraForAllStands();
        boolean onStands = NextAlbumClientConfig.standResolveAura();
        boolean onSummon = NextAlbumClientConfig.resolveAuraOnSummon();
        for (Player player : level.players()) {
            if (!player.isAlive()) {
                continue;
            }
            StandPower power = StandPower.get(player);
            if (power == null || !power.hasPower() || power.getPowerType() == null) {
                continue;
            }
            boolean nextAlbumStand = NEXT_ALBUM_STAND_NAMESPACES.contains(
                    power.getPowerType().getId().getNamespace());
            boolean fromResolve = player.hasEffect(ModStatusEffects.RESOLVE)
                    && (forAll || nextAlbumStand);
            boolean fromSummon = onSummon && (forAll || nextAlbumStand)
                    && power.getSummonedStandEntity() != null;
            if (!fromResolve && !fromSummon) {
                continue;
            }
            int colour = StandSkinsLoader.getInstance().getSkin(power).getColor();
            spawnAround(level, player, colour, sprites, fromResolve ? 7 : 3, false);
            if (player == minecraft.getCameraEntity()) {
                spawnFirstPerson(colour, sprites);
            }
            if (onStands) {
                spawnOnStands(level, player, colour, sprites);
            }
        }
    }

    private static void spawnOnStands(
            ClientLevel level, Player user, int colour, SpriteSet sprites) {
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof StandEntity stand) || !stand.isAlive()
                    || stand.getUser() != user
                    || stand.getBbWidth() <= 0.05F
                    || stand.getBbHeight() <= 0.05F) {
                continue;
            }
            // 0.1.13 gave Sex Pistols' tiny members one particle every 3
            // ticks; any small Stand gets that here.
            if (stand.getBbWidth() < 0.5F) {
                if (stand.tickCount % 3 == 0) {
                    spawnAround(level, stand, colour, sprites, 1, true);
                }
            } else {
                spawnAround(level, stand, colour, sprites, 5, false);
            }
        }
    }

    private static void spawnAround(
            ClientLevel level, LivingEntity entity, int colour,
            SpriteSet sprites, int count, boolean small) {
        float width = small ? Math.max(entity.getBbWidth(), 0.12F) : entity.getBbWidth();
        float height = small ? Math.max(entity.getBbHeight(), 0.12F) : entity.getBbHeight();
        double spread = small ? width : width + 0.5D;
        double rise = small ? height : height * 0.5D;
        for (int i = 0; i < count; i++) {
            Minecraft.getInstance().particleEngine.add(ResolveAuraParticle.create(
                    level, entity,
                    entity.getX() + (RANDOM.nextDouble() - 0.5D) * spread,
                    entity.getY() + RANDOM.nextDouble() * rise,
                    entity.getZ() + (RANDOM.nextDouble() - 0.5D) * spread,
                    colour, sprites));
        }
    }

    private static void spawnFirstPerson(int colour, SpriteSet sprites) {
        FirstPersonHamonAura aura = FirstPersonHamonAura.getInstance();
        if (aura == null) {
            return;
        }
        float perTick = 1.4F;
        for (HumanoidArm arm : HumanoidArm.values()) {
            int count = (int) perTick;
            if (RANDOM.nextFloat() < perTick - count) {
                count++;
            }
            for (int i = 0; i < count; i++) {
                double fx = RANDOM.nextDouble() * 0.5D - 0.625D;
                double fy = RANDOM.nextDouble();
                double fz = RANDOM.nextDouble() * 0.5D - 0.25D;
                if (arm == HumanoidArm.LEFT) {
                    fx = -fx;
                }
                aura.add(new ResolveAuraParticle.FirstPerson(
                        fx, fy, fz, sprites, arm, colour));
            }
        }
    }
}
