package rotp.addons.echoes.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import rotp.addons.echoes.init.EchoesBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * R772 F11: the sound ACT1 left on a block (the master's EchoesSoundMarkTileEntity and SoundMarkRegistry): its sound,
 * owner and when it was put there are saved with the world; the owner's loaded marks are found here, oldest first.
 */
public final class EchoesSoundMarkBlockEntity extends BlockEntity {
    private static final Set<EchoesSoundMarkBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
    // orders marks put in the same tick
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Nullable private ResourceLocation sound;
    @Nullable private UUID owner;
    private long placedAt;
    private long sequence = SEQUENCE.incrementAndGet();

    public EchoesSoundMarkBlockEntity(BlockPos pos, BlockState state) {
        super(EchoesBlocks.SOUND_MARK_ENTITY.get(), pos, state);
    }

    public void set(UUID owner, ResourceLocation sound, long gameTime) {
        this.owner = owner;
        this.sound = sound;
        this.placedAt = gameTime;
        this.sequence = SEQUENCE.incrementAndGet();
        setChanged();
        if (level != null) {
            // a fresh block entity is told it loaded only on the next tick; its owner may count it before that
            if (!level.isClientSide()) {
                LOADED.add(this);
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Nullable
    public ResourceLocation sound() {
        return sound;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    /** The owner's loaded marks in every level, oldest first. */
    public static List<EchoesSoundMarkBlockEntity> marksOf(UUID owner) {
        List<EchoesSoundMarkBlockEntity> marks = new ArrayList<>();
        for (EchoesSoundMarkBlockEntity mark : LOADED) {
            if (!mark.isRemoved() && mark.level != null && owner.equals(mark.owner)) {
                marks.add(mark);
            }
        }
        marks.sort(Comparator.<EchoesSoundMarkBlockEntity>comparingLong(mark -> mark.placedAt)
                .thenComparingLong(mark -> mark.sequence));
        return marks;
    }

    @Nullable
    public static EchoesSoundMarkBlockEntity at(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof EchoesSoundMarkBlockEntity mark && mark.sound != null ? mark : null;
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
        if (sound != null) {
            tag.putString("Sound", sound.toString());
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putLong("PlacedAt", placedAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sound = tag.contains("Sound") ? ResourceLocation.tryParse(tag.getString("Sound")) : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        placedAt = tag.getLong("PlacedAt");
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
