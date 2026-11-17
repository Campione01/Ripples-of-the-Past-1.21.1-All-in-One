package rotp.addons.kingcrimson.epitaph.replay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import rotp.addons.kingcrimson.epitaph.replay.PuppetBinder.Binding;

/**
 * What entity lookups answer in a muted tick: the bodies that are on their tracks, and nothing else. Whoever is not of
 * the prediction is not found by an action the record put on a body, and so is not touched by it.
 */
final class BoundBodies implements LevelEntityGetter<Entity> {
    private final List<Entity> bodies = new ArrayList<>();
    private final List<ServerPlayer> players = new ArrayList<>();

    BoundBodies(PuppetBinder binder) {
        for (Binding binding : binder.all()) {
            bodies.add(binding.body);
            if (binding.body instanceof ServerPlayer player) {
                players.add(player);
            }
        }
    }

    List<ServerPlayer> players() {
        return players;
    }

    @Nullable
    @Override
    public Entity get(int id) {
        for (Entity body : bodies) {
            if (body.getId() == id && !body.isRemoved()) {
                return body;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public Entity get(UUID uuid) {
        for (Entity body : bodies) {
            if (body.getUUID().equals(uuid) && !body.isRemoved()) {
                return body;
            }
        }
        return null;
    }

    @Override
    public Iterable<Entity> getAll() {
        return present();
    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AbortableIterationConsumer<U> consumer) {
        for (Entity body : present()) {
            U cast = test.tryCast(body);
            if (cast != null && consumer.accept(cast).shouldAbort()) {
                return;
            }
        }
    }

    @Override
    public void get(AABB boundingBox, Consumer<Entity> consumer) {
        for (Entity body : present()) {
            if (body.getBoundingBox().intersects(boundingBox)) {
                consumer.accept(body);
            }
        }
    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AABB bounds, AbortableIterationConsumer<U> consumer) {
        for (Entity body : present()) {
            if (body.getBoundingBox().intersects(bounds)) {
                U cast = test.tryCast(body);
                if (cast != null && consumer.accept(cast).shouldAbort()) {
                    return;
                }
            }
        }
    }

    private List<Entity> present() {
        List<Entity> present = new ArrayList<>(bodies.size());
        for (Entity body : bodies) {
            if (!body.isRemoved()) {
                present.add(body);
            }
        }
        return present;
    }
}
