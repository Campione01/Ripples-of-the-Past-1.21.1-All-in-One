package rotp.addons.khnum.disguise;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.khnum.KhnumConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class DisguisePolicy {
    public static final float MIN_HEIGHT = 1.0F;
    public static final float MAX_HEIGHT = 3.0F;
    public static final float MIN_WIDTH = 0.5F;
    public static final float MAX_WIDTH = 2.0F;

    private DisguisePolicy() {}

    public static boolean isAllowedEntity(
            EntityType<?> type, Level level) {
        if (type == null || level == null
                || type == EntityType.PLAYER
                || type == EntityType.ENDER_DRAGON
                || !hasDisguiseCategory(type)) {
            return false;
        }
        EntityDimensions dimensions = type.getDimensions();
        ResourceLocation id = EntityType.getKey(type);
        if (id == null
                || KhnumConfig.ENTITY_DISGUISE_BLACKLIST
                        .get().contains(id.toString())
                || isProjectileId(id.toString())
                || dimensions.height()
                        < KhnumConfig.MIN_ENTITY_HEIGHT.get()
                || dimensions.height()
                        > KhnumConfig.MAX_ENTITY_HEIGHT.get()
                || dimensions.width()
                        < KhnumConfig.MIN_ENTITY_WIDTH.get()
                || dimensions.width()
                        > KhnumConfig.MAX_ENTITY_WIDTH.get()) {
            return false;
        }
        Entity created = null;
        try {
            created = type.create(level);
            return created instanceof LivingEntity
                    && !(created instanceof Player)
                    && !(created instanceof StandEntity);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
        finally {
            if (created != null) {
                created.remove(Entity.RemovalReason.DISCARDED);
            }
        }
    }

    // 1.16 EntityDisguiseRegistry: creatures and monsters, and of the MISC types only these four.
    public static boolean hasDisguiseCategory(EntityType<?> type) {
        MobCategory category = type.getCategory();
        return category == MobCategory.CREATURE
                || category == MobCategory.MONSTER
                || category == MobCategory.MISC
                        && (type == EntityType.IRON_GOLEM
                                || type == EntityType.SNOW_GOLEM
                                || type == EntityType.VILLAGER
                                || type == EntityType.WANDERING_TRADER);
    }

    private static boolean isProjectileId(String id) {
        return id.contains("fireball") || id.contains("bullet")
                || id.contains("arrow") || id.contains("projectile");
    }

    public static EntityType<?> entityType(
            ResourceLocation id, Level level) {
        EntityType<?> type = id != null
                ? BuiltInRegistries.ENTITY_TYPE
                        .getOptional(id).orElse(null)
                : null;
        return isAllowedEntity(type, level) ? type : null;
    }
}
