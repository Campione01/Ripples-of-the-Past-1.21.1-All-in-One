package rotp.addons.kingcrimson.epitaph.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;

/**
 * What entity lookups answer in a room: its phantoms and nothing else. A room holds a few dozen entities at most, so
 * a box query walks them all; a phantom that was removed earlier in the same tick is no longer found, as an entity
 * that left the world is not.
 */
public final class SandboxEntityGetter implements LevelEntityGetter<Entity> {
    private final SandboxRoom room;

    SandboxEntityGetter(SandboxRoom room) {
        this.room = room;
    }

    @Nullable
    @Override
    public Entity get(int id) {
        Entity phantom = room.phantom(id);
        return phantom != null && !phantom.isRemoved() ? phantom : null;
    }

    @Nullable
    @Override
    public Entity get(UUID uuid) {
        Entity phantom = room.phantom(uuid);
        return phantom != null && !phantom.isRemoved() ? phantom : null;
    }

    @Override
    public Iterable<Entity> getAll() {
        return present();
    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AbortableIterationConsumer<U> consumer) {
        for (Entity phantom : present()) {
            U cast = test.tryCast(phantom);
            if (cast != null && consumer.accept(cast).shouldAbort()) {
                return;
            }
        }
    }

    @Override
    public void get(AABB boundingBox, Consumer<Entity> consumer) {
        for (Entity phantom : present()) {
            if (phantom.getBoundingBox().intersects(boundingBox)) {
                consumer.accept(phantom);
            }
        }
    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AABB bounds, AbortableIterationConsumer<U> consumer) {
        for (Entity phantom : present()) {
            if (phantom.getBoundingBox().intersects(bounds)) {
                U cast = test.tryCast(phantom);
                if (cast != null && consumer.accept(cast).shouldAbort()) {
                    return;
                }
            }
        }
    }

    // A copy: what a consumer does may add a child to the room or take a phantom out of it.
    private List<Entity> present() {
        List<Entity> present = new ArrayList<>();
        for (Entity phantom : room.phantoms()) {
            if (!phantom.isRemoved()) {
                present.add(phantom);
            }
        }
        return present;
    }
}
