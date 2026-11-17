package rotp.addons.kingcrimson.client.epitaph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.entity.KingCrimsonPhantomCarrierEntity;
import rotp.addons.kingcrimson.epitaph.sandbox.PhantomIdentity;

/**
 * The phantoms of one prediction on the client of the one who foresees: real client entities of their own types, made
 * by the packets of the frames and kept here, outside the level, so that the body with the same id stays what it is.
 * Each has a carrier in the level, an entity of ours with an id of its own, through which it is drawn.
 */
@OnlyIn(Dist.CLIENT)
public final class EpitaphPhantomWorld {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Carriers have ids no server entity has; the phantom's own id is counted down from here. */
    private static final int CARRIER_ID_BASE = -0x48000000;

    private final ClientLevel level;
    private final Int2ObjectLinkedOpenHashMap<Entity> puppets = new Int2ObjectLinkedOpenHashMap<>();
    private final Map<UUID, Entity> byUuid = new HashMap<>();
    private boolean failureLogged;
    @Nullable
    private LevelEntityGetter<Entity> over;
    @Nullable
    private LevelEntityGetter<Entity> overWhat;

    EpitaphPhantomWorld(ClientLevel level) {
        this.level = level;
    }

    ClientLevel level() {
        return level;
    }

    @Nullable
    public Entity get(int id) {
        return puppets.get(id);
    }

    @Nullable
    Entity get(UUID uuid) {
        return byUuid.get(uuid);
    }

    public boolean isPuppet(@Nullable Entity entity) {
        return entity != null && puppets.get(entity.getId()) == entity;
    }

    /** An entity a packet of a frame made: it is a phantom, kept here and never added to the level. */
    void add(Entity puppet) {
        remove(puppet.getId());
        PhantomIdentity.mark(puppet);
        puppets.put(puppet.getId(), puppet);
        byUuid.put(puppet.getUUID(), puppet);
        puppet.onAddedToLevel();
        KingCrimsonPhantomCarrierEntity carrier = KingCrimsonAddon.PHANTOM_CARRIER_ENTITY.get().create(level);
        if (carrier != null) {
            carrier.setId(CARRIER_ID_BASE - puppet.getId());
            carrier.carry(puppet);
            outside(() -> level.addEntity(carrier));
        }
    }

    /**
     * The packet that would add a player, from a frame: the phantom of a human. The game would look for him in the list
     * of the players and find nobody; he is made here, with the look of the real player whose id he shares.
     */
    void addHuman(ClientboundAddEntityPacket packet) {
        Entity[] real = new Entity[1];
        outside(() -> real[0] = level.getEntity(packet.getId()));
        if (real[0] instanceof AbstractClientPlayer player) {
            EpitaphRemotePlayer puppet = new EpitaphRemotePlayer(level, player.getGameProfile());
            puppet.recreateFromPacket(packet);
            add(puppet);
        }
    }

    void remove(int id) {
        Entity puppet = puppets.remove(id);
        if (puppet == null) {
            return;
        }
        byUuid.remove(puppet.getUUID(), puppet);
        puppet.setRemoved(Entity.RemovalReason.DISCARDED);
        puppet.onClientRemoval();
        outside(() -> level.removeEntity(CARRIER_ID_BASE - id, Entity.RemovalReason.DISCARDED));
    }

    void clear() {
        for (int id : puppets.keySet().toIntArray()) {
            remove(id);
        }
    }

    /** One tick of every phantom, as the level ticks an entity it holds. */
    void tick() {
        for (Entity puppet : List.copyOf(puppets.values())) {
            if (puppet.isRemoved() || puppet.isPassenger()) {
                continue;
            }
            try {
                puppet.setOldPosAndRot();
                puppet.tickCount++;
                puppet.tick();
                for (Entity rider : List.copyOf(puppet.getPassengers())) {
                    ride(puppet, rider);
                }
            }
            catch (RuntimeException failure) {
                drop(puppet, failure);
            }
        }
    }

    private void ride(Entity vehicle, Entity rider) {
        if (rider.isRemoved() || rider.getVehicle() != vehicle) {
            rider.stopRiding();
            return;
        }
        if (!isPuppet(rider)) {
            return;
        }
        rider.setOldPosAndRot();
        rider.tickCount++;
        rider.rideTick();
        for (Entity above : List.copyOf(rider.getPassengers())) {
            ride(rider, above);
        }
    }

    // A phantom whose tick or packet throws is taken out; the others and the game go on.
    void drop(Entity puppet, RuntimeException failure) {
        if (!failureLogged) {
            failureLogged = true;
            LOGGER.warn("Epitaph[client-phantom] the phantom of a {} failed on the client and is not shown further",
                    puppet.getType().toShortString(), failure);
        }
        remove(puppet.getId());
    }

    // The level is touched for the carriers alone, and as the level, not as the phantoms' world.
    private static void outside(Runnable work) {
        boolean active = PhantomContext.active;
        boolean applying = PhantomContext.applying;
        PhantomContext.active = false;
        PhantomContext.applying = false;
        try {
            work.run();
        }
        finally {
            PhantomContext.active = active;
            PhantomContext.applying = applying;
        }
    }

    /**
     * What the level answers while the phantoms are handled: by id and by UUID a phantom before the entity of the
     * world; in a box or by type the phantoms alone, which are each other's world. While packets are applied nothing
     * of the world is answered at all: a packet that names no phantom must find nothing to act on.
     */
    LevelEntityGetter<Entity> over(LevelEntityGetter<Entity> world) {
        if (over == null || overWhat != world) {
            over = combined(world);
            overWhat = world;
        }
        return over;
    }

    private LevelEntityGetter<Entity> combined(LevelEntityGetter<Entity> world) {
        return new LevelEntityGetter<>() {
            @Nullable
            @Override
            public Entity get(int id) {
                Entity puppet = puppets.get(id);
                return puppet != null || PhantomContext.applying ? puppet : world.get(id);
            }

            @Nullable
            @Override
            public Entity get(UUID uuid) {
                Entity puppet = byUuid.get(uuid);
                return puppet != null || PhantomContext.applying ? puppet : world.get(uuid);
            }

            @Override
            public Iterable<Entity> getAll() {
                return new ArrayList<>(puppets.values());
            }

            @Override
            public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AbortableIterationConsumer<U> consumer) {
                for (Entity puppet : new ArrayList<>(puppets.values())) {
                    U cast = test.tryCast(puppet);
                    if (cast != null && consumer.accept(cast).shouldAbort()) {
                        return;
                    }
                }
            }

            @Override
            public void get(AABB boundingBox, Consumer<Entity> consumer) {
                for (Entity puppet : new ArrayList<>(puppets.values())) {
                    if (puppet.getBoundingBox().intersects(boundingBox)) {
                        consumer.accept(puppet);
                    }
                }
            }

            @Override
            public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AABB bounds, AbortableIterationConsumer<U> consumer) {
                for (Entity puppet : new ArrayList<>(puppets.values())) {
                    if (puppet.getBoundingBox().intersects(bounds)) {
                        U cast = test.tryCast(puppet);
                        if (cast != null && consumer.accept(cast).shouldAbort()) {
                            return;
                        }
                    }
                }
            }
        };
    }
}
