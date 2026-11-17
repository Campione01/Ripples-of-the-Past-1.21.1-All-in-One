package rotp.core.impl.stands.goldexperience;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The menu of a Gold Experience user's lifeforms, as the server keeps it: the rows it shows him, and the one revert it
 * lets him ask for. His client only asks; this decides.
 */
public final class GoldExperienceLifeformRoster {
    public static final int MAX_ROWS = 256;

    private GoldExperienceLifeformRoster() {}

    public enum Place {
        /** Loaded where its user is: it can be reverted from the menu. */
        HERE,
        /** Still turning into the creature, where its user is: it can be reverted too. */
        FORMING,
        /** Loaded, in another dimension. */
        ELSEWHERE,
        /** In a chunk nobody has loaded: only its last known place is known. */
        UNLOADED;

        public boolean revertible() {
            return this == HERE || this == FORMING;
        }
    }

    public enum Answer {
        NONE,
        REVERTED,
        /** It died, was reverted or no longer exists, or it is not his. */
        GONE,
        /** Not loaded where he is. */
        NOT_HERE,
        /** His Revert cannot be used right now. */
        REFUSED
    }

    public record Row(UUID id, @Nullable ResourceLocation type, @Nullable Component name, Component source,
            @Nullable ResourceLocation dimension, @Nullable Vec3 pos, int entityId, long age, boolean counts, Place place) {}

    public static List<Row> rows(LivingEntity user) {
        StandPower standPower = StandPower.get(user);
        if (standPower == null) {
            return List.of();
        }
        long gameTime = user.level().getGameTime();
        List<Row> rows = new ArrayList<>();
        standPower.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get())
                .filter(effect -> !effect.isStopped() && effect.getTargetUUID() != null)
                .forEach(effect -> rows.add(row(effect, user, gameTime)));
        rows.sort(nearestFirst(user.level().dimension().location(), user.position()));
        return rows.size() > MAX_ROWS ? List.copyOf(rows.subList(0, MAX_ROWS)) : rows;
    }

    /** Nearest first in the player's dimension, then the rest, nearest first by their coordinates. */
    public static Comparator<Row> nearestFirst(@Nullable ResourceLocation playerDimension, Vec3 playerPos) {
        return Comparator.comparing((Row row) -> playerDimension == null || !playerDimension.equals(row.dimension()))
                .thenComparingDouble(row -> row.pos() != null ? row.pos().distanceToSqr(playerPos) : Double.MAX_VALUE);
    }

    private static Row row(GECreatedLifeformEffect effect, LivingEntity user, long gameTime) {
        Entity target = effect.getTarget();
        Place place;
        if (target == null || !target.isAlive()) {
            place = Place.UNLOADED;
        }
        else if (target.level() != user.level()) {
            place = Place.ELSEWHERE;
        }
        else {
            place = target instanceof GETransformationEntity ? Place.FORMING : Place.HERE;
        }
        boolean loaded = place != Place.UNLOADED;
        ResourceKey<Level> dimension = loaded ? target.level().dimension() : effect.getLastDimension();
        Vec3 pos = loaded ? target.position() : effect.getLastPos();
        return new Row(effect.getTargetUUID(), effect.getLifeformType(), effect.getLifeformName(), effect.getSourceName(),
                dimension != null ? dimension.location() : null, pos,
                place.revertible() ? target.getId() : -1,
                effect.getAge(gameTime), effect.countsTowardLimit(gameTime), place);
    }

    /** The user asks to revert his lifeform with this UUID from the menu. */
    public static Answer revert(LivingEntity user, UUID lifeformId) {
        StandPower standPower = StandPower.get(user);
        if (standPower == null || !standPower.hasPower()) {
            return Answer.REFUSED;
        }
        GECreatedLifeformEffect effect = standPower.userStandEffects
                .getEffectsOfType(ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get())
                .filter(lifeform -> !lifeform.isStopped() && lifeformId.equals(lifeform.getTargetUUID()))
                .findFirst().orElse(null);
        if (effect == null) {
            return Answer.GONE;
        }
        Ability revert = revertAbility(standPower);
        if (revert == null || !revert.isAbilityUnlocked(standPower) || !revert.checkConditions(standPower).isPositive()) {
            return Answer.REFUSED;
        }
        Entity target = effect.getTarget();
        // any distance: one still counting turns back by itself past 128 blocks, and one past 7 minutes is his anywhere
        if (target == null || !target.isAlive() || target.level() != user.level()) {
            return Answer.NOT_HERE;
        }
        GoldExperienceRevertLifeformAbility.revertLifeform(standPower, effect);
        return Answer.REVERTED;
    }

    /** The Revert of this Stand's moveset (Gold Experience's own, or an add-on's that reuses it), if it has one. */
    @Nullable
    public static Ability revertAbility(StandPower standPower) {
        if (standPower == null || !standPower.hasPower()) {
            return null;
        }
        return standPower.getMoveset().abilities.values().stream()
                .filter(GoldExperienceRevertLifeformAbility.class::isInstance)
                .findFirst().orElse(null);
    }
}
