package rotp.addons.echoes.state;

import java.lang.reflect.Method;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Master SheerHeartAttackCompat: Killer Queen's Sheer Heart Attack, known by name and looked into by reflection, since this
 * add-on does not depend on the Killer Queen add-ons. Both have the type path sheer_heart and getKillerQueenOwner.
 */
final class EchoesSheerHeartCompat {
    private EchoesSheerHeartCompat() {}

    static boolean isSheerHeart(Entity entity) {
        if ("sheer_heart".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath())) {
            return true;
        }
        for (Class<?> type = entity.getClass(); type != null && type != Entity.class; type = type.getSuperclass()) {
            if ("SheerHeart".equals(type.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static LivingEntity ownerOf(Entity car) {
        for (Method method : car.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || !LivingEntity.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.contains("owner") && !name.contains("master")) {
                continue;
            }
            try {
                if (method.invoke(car) instanceof LivingEntity owner) {
                    return owner;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // the next candidate, as the master went on
            }
        }
        return null;
    }
}
