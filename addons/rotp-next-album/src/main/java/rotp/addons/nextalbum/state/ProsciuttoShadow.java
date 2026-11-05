package rotp.addons.nextalbum.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.nextalbum.item.CharacterSuitArmorItem;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem.Suit;
import rotp.addons.nextalbum.network.ProsciuttoShadowPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Next Album 0.1.13 ProsciuttoShadowUtil: with the full Prosciutto suit
 * in Stealth, a player becomes a shadow after 200 ticks out of combat, or
 * at once while aged by The Grateful Dead or standing in light 7 or less.
 * A shadow hides its name, makes mobs that target it lose it (hostile
 * mobs only beyond 8 blocks) and is seen at a quarter of the range.
 */
public final class ProsciuttoShadow {
    public static final String SHADOW_KEY = "RotpNextAlbumProsciuttoShadow";
    public static final String LAST_COMBAT_KEY = "RotpNextAlbumProsciuttoCombat";
    public static final int OUT_OF_COMBAT_TICKS = 200;
    public static final int LOW_LIGHT = 7;
    public static final double SHADOW_AGGRO_RANGE = 8.0D;
    public static final double TARGET_CLEAR_RANGE = 64.0D;
    public static final double SHADOW_VISIBILITY = 0.25D;
    // rotp-grateful-dead's aging state, looked up by id (no class link).
    private static final ResourceLocation GRATEFUL_DEAD_AGING =
            ResourceLocation.fromNamespaceAndPath(
                    "rotp_grateful_dead", "living_aging");
    private static final Map<Integer, Boolean> CLIENT_SHADOW =
            new ConcurrentHashMap<>();

    private ProsciuttoShadow() {}

    public static boolean isStealthMode(@Nullable LivingEntity entity) {
        return CharacterSuitArmorItem.hasFullSet(entity, Suit.PROSCIUTTO)
                && CharacterSuitArmorItem.getMode(entity.getItemBySlot(
                        net.minecraft.world.entity.EquipmentSlot.CHEST))
                        == CharacterSuitArmorItem.MODE_STEALTH;
    }

    public static boolean isShadow(@Nullable LivingEntity entity) {
        if (!(entity instanceof Player) || !isStealthMode(entity)) {
            return false;
        }
        return entity.level().isClientSide()
                ? Boolean.TRUE.equals(CLIENT_SHADOW.get(entity.getId()))
                : entity.getPersistentData().getBoolean(SHADOW_KEY);
    }

    public static boolean isShadowFlag(ServerPlayer player) {
        return player.getPersistentData().getBoolean(SHADOW_KEY);
    }

    public static void handleClientSync(int entityId, boolean shadow) {
        if (shadow) {
            CLIENT_SHADOW.put(entityId, Boolean.TRUE);
        } else {
            CLIENT_SHADOW.remove(entityId);
        }
    }

    public static void clearClient() {
        CLIENT_SHADOW.clear();
    }

    public static void tick(ServerPlayer player) {
        if (!isStealthMode(player)) {
            setShadow(player, false);
            return;
        }
        boolean wantShadow = isForceShadow(player) || isOutOfCombat(player);
        setShadow(player, wantShadow);
        if (wantShadow) {
            keepReducedAggro(player);
        }
    }

    public static boolean isForceShadow(Player player) {
        AttachmentType<?> aging = NeoForgeRegistries.ATTACHMENT_TYPES
                .get(GRATEFUL_DEAD_AGING);
        if (aging != null && player.hasData(aging)) {
            return true;
        }
        return player.level().getMaxLocalRawBrightness(
                player.blockPosition()) <= LOW_LIGHT;
    }

    public static boolean isOutOfCombat(Player player) {
        var data = player.getPersistentData();
        long now = player.level().getGameTime();
        if (!data.contains(LAST_COMBAT_KEY)) {
            data.putLong(LAST_COMBAT_KEY, now);
            return false;
        }
        return now - data.getLong(LAST_COMBAT_KEY) >= OUT_OF_COMBAT_TICKS;
    }

    public static void onEnterStealthMode(ServerPlayer player) {
        player.getPersistentData().putLong(
                LAST_COMBAT_KEY, player.level().getGameTime());
        if (!isForceShadow(player)) {
            setShadow(player, false);
        }
    }

    public static void markCombat(@Nullable Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getPersistentData().putLong(
                    LAST_COMBAT_KEY, serverPlayer.level().getGameTime());
            if (isStealthMode(serverPlayer) && !isForceShadow(serverPlayer)) {
                setShadow(serverPlayer, false);
            }
        }
    }

    // 0.1.13 left its own aging damage out of "combat".
    public static boolean isCombatDamage(@Nullable DamageSource source) {
        if (source == null || source.getMsgId().contains("aging")) {
            return false;
        }
        Entity entity = source.getEntity();
        return entity instanceof LivingEntity || entity instanceof Projectile;
    }

    @Nullable
    public static Player playerFrom(@Nullable Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof StandEntity stand
                && stand.getUser() instanceof Player user) {
            return user;
        }
        if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner instanceof Player player) {
                return player;
            }
            if (owner instanceof StandEntity stand
                    && stand.getUser() instanceof Player user) {
                return user;
            }
        }
        return null;
    }

    public static void setShadow(ServerPlayer player, boolean shadow) {
        boolean was = isShadowFlag(player);
        if (was == shadow) {
            return;
        }
        player.getPersistentData().putBoolean(SHADOW_KEY, shadow);
        if (shadow) {
            dropMobTargets(player);
        }
        player.refreshDisplayName();
        player.refreshTabListName();
        ProsciuttoShadowPayload.sync(player);
    }

    private static void dropMobTargets(Player player) {
        AABB box = player.getBoundingBox().inflate(TARGET_CLEAR_RANGE);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box,
                mob -> player.equals(mob.getTarget())
                        || player.equals(mob.getLastHurtByMob()))) {
            if (player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
            if (player.equals(mob.getLastHurtByMob())) {
                mob.setLastHurtByMob(null);
            }
        }
    }

    private static void keepReducedAggro(Player player) {
        AABB box = player.getBoundingBox().inflate(TARGET_CLEAR_RANGE);
        double rangeSq = SHADOW_AGGRO_RANGE * SHADOW_AGGRO_RANGE;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box,
                mob -> player.equals(mob.getTarget()))) {
            if (!isHostile(mob) || mob.distanceToSqr(player) > rangeSq) {
                mob.setTarget(null);
            }
        }
    }

    public static boolean shouldLoseTarget(Mob mob, @Nullable LivingEntity target) {
        if (!(target instanceof Player) || !isShadow(target)) {
            return false;
        }
        return !isHostile(mob)
                || mob.distanceToSqr(target)
                        > SHADOW_AGGRO_RANGE * SHADOW_AGGRO_RANGE;
    }

    private static boolean isHostile(Mob mob) {
        return mob.getType().getCategory() == MobCategory.MONSTER;
    }
}
