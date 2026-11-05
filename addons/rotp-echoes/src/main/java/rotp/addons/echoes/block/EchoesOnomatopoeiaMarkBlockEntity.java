package rotp.addons.echoes.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import rotp.addons.echoes.init.EchoesBlocks;
import rotp.addons.echoes.state.EchoesOnomatopoeia;
import rotp.addons.echoes.state.EchoesOnomatopoeiaService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * R772 F11: the word ACT2 left on a block (the master's OnomatopoeiaMarkTileEntity and OnomatopoeiaMarkRegistry):
 * every half second it acts on each living entity within 0.6 blocks of its cell, and an entity that leaves loses the
 * state it gave; it is saved with the world, and its owner can take it back or recall it while it is loaded.
 */
public final class EchoesOnomatopoeiaMarkBlockEntity extends BlockEntity {
    private static final int TRIGGER_INTERVAL_TICKS = 10;
    private static final double TRIGGER_RANGE = 0.6D;
    private static final Set<EchoesOnomatopoeiaMarkBlockEntity> LOADED =
            Collections.newSetFromMap(new WeakHashMap<>());

    @Nullable private EchoesOnomatopoeia word;
    @Nullable private UUID owner;
    private final Set<UUID> inside = new HashSet<>();

    public EchoesOnomatopoeiaMarkBlockEntity(BlockPos pos, BlockState state) {
        super(EchoesBlocks.ONOMATOPOEIA_MARK_ENTITY.get(), pos, state);
    }

    public void set(UUID owner, EchoesOnomatopoeia word) {
        if (level instanceof ServerLevel serverLevel && this.word != null && this.word != word) {
            releaseInside(serverLevel);
        }
        this.owner = owner;
        this.word = word;
        setChanged();
        if (level != null) {
            // a fresh block entity is told it loaded only on the next tick; its owner may recall it before that
            if (!level.isClientSide()) {
                LOADED.add(this);
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Nullable
    public EchoesOnomatopoeia word() {
        return word;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public int variant() {
        return getBlockState().getValue(EchoesOnomatopoeiaMarkBlock.VARIANT);
    }

    /** The owner's loaded marks, in every level. */
    public static List<EchoesOnomatopoeiaMarkBlockEntity> marksOf(UUID owner) {
        List<EchoesOnomatopoeiaMarkBlockEntity> marks = new ArrayList<>();
        for (EchoesOnomatopoeiaMarkBlockEntity mark : LOADED) {
            if (!mark.isRemoved() && mark.level != null && owner.equals(mark.owner)) {
                marks.add(mark);
            }
        }
        return marks;
    }

    @Nullable
    public static EchoesOnomatopoeia wordAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof EchoesOnomatopoeiaMarkBlockEntity mark ? mark.word : null;
    }

    /** The mark comes off its block; the states it gave the ones inside end with it. */
    public void takeOff() {
        if (level instanceof ServerLevel serverLevel) {
            releaseInside(serverLevel);
            serverLevel.removeBlock(worldPosition, false);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EchoesOnomatopoeiaMarkBlockEntity mark) {
        if (level instanceof ServerLevel serverLevel) {
            mark.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        if (word == null) {
            return;
        }
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(worldPosition).inflate(TRIGGER_RANGE), LivingEntity::isAlive);
        if (level.getGameTime() % TRIGGER_INTERVAL_TICKS == 0L) {
            for (LivingEntity entity : entities) {
                EchoesOnomatopoeiaService.apply(word, entity);
            }
        }
        Set<UUID> now = new HashSet<>();
        for (LivingEntity entity : entities) {
            now.add(entity.getUUID());
        }
        for (UUID id : inside) {
            if (!now.contains(id) && level.getEntity(id) instanceof LivingEntity left) {
                EchoesOnomatopoeiaService.removeWord(word, left);
            }
        }
        inside.clear();
        inside.addAll(now);
    }

    private void releaseInside(ServerLevel level) {
        if (word != null) {
            for (UUID id : inside) {
                Entity entity = level.getEntity(id);
                if (entity instanceof LivingEntity living) {
                    EchoesOnomatopoeiaService.removeWord(word, living);
                }
            }
        }
        inside.clear();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            LOADED.add(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        LOADED.remove(this);
    }

    @Override
    public void setRemoved() {
        LOADED.remove(this);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (word != null) {
            tag.putString("Word", word.id());
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        word = EchoesOnomatopoeia.byId(tag.getString("Word"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
