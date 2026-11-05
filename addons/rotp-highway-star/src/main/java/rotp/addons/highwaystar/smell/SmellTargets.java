package rotp.addons.highwaystar.smell;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/** The 1.16 smell_targets player capability: every creature the user has smelled, and the one picked for tracking. */
public final class SmellTargets {
    public static final Codec<SmellTargets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.listOf().fieldOf("targets").forGetter(targets -> new ArrayList<>(targets.targets)),
            UUIDUtil.CODEC.optionalFieldOf("selected").forGetter(targets -> Optional.ofNullable(targets.selected))
    ).apply(instance, SmellTargets::new));

    private final Set<UUID> targets = new LinkedHashSet<>();
    @Nullable private UUID selected;

    public SmellTargets() {}

    private SmellTargets(List<UUID> targets, Optional<UUID> selected) {
        this.targets.addAll(targets);
        this.selected = selected.orElse(null);
    }

    public Set<UUID> getTargets() {
        return targets;
    }

    public void addTarget(UUID uuid) {
        targets.add(uuid);
    }

    public void removeTarget(UUID uuid) {
        targets.remove(uuid);
        if (uuid != null && uuid.equals(selected)) {
            selected = null;
        }
    }

    public void clear() {
        targets.clear();
        selected = null;
    }

    @Nullable
    public UUID getSelectedTrackingTarget() {
        return selected;
    }

    public void setSelectedTrackingTarget(@Nullable UUID uuid) {
        selected = uuid;
    }
}
