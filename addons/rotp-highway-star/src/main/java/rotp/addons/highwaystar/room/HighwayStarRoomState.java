package rotp.addons.highwaystar.room;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import rotp.addons.highwaystar.network.HighwayStarRoomPayloads.ViewEndPayload;
import rotp.addons.highwaystar.network.HighwayStarRoomPayloads.ViewStartPayload;

public final class HighwayStarRoomState {
    public record RoomRef(UUID roomId, ResourceKey<Level> dimension) {
        private static final Codec<RoomRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("room_id").forGetter(RoomRef::roomId),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(RoomRef::dimension)
        ).apply(instance, RoomRef::new));
    }

    public static final Codec<HighwayStarRoomState> CODEC = RoomRef.CODEC.optionalFieldOf("room").codec()
            .xmap(HighwayStarRoomState::new, state -> Optional.ofNullable(state.room));

    @Nullable private RoomRef room;
    @Nullable private ResourceLocation recentCombatType;
    private long recentCombatTick;
    @Nullable private ViewStartPayload clientView;
    @Nullable private Entity clientPreview;
    private int clientTicks;

    public HighwayStarRoomState() {}

    private HighwayStarRoomState(Optional<RoomRef> room) {
        this.room = room.orElse(null);
    }

    public boolean hasRoom() {
        return room != null;
    }

    @Nullable
    public RoomRef getRoom() {
        return room;
    }

    @Nullable
    RoomRef replaceRoom(@Nullable RoomRef replacement) {
        RoomRef previous = room;
        room = replacement;
        return previous;
    }

    void rememberCombat(ResourceLocation entityType, long gameTick) {
        recentCombatType = entityType;
        recentCombatTick = gameTick;
    }

    @Nullable
    ResourceLocation recentCombatType(long gameTick, int maxAge) {
        long age = gameTick - recentCombatTick;
        return age >= 0 && age <= maxAge ? recentCombatType : null;
    }

    @Nullable
    public ViewStartPayload getClientView() {
        return clientView;
    }

    public void startClientView(ViewStartPayload view) {
        clearClientView();
        clientView = view;
    }

    public void endClientView(ViewEndPayload end) {
        if (clientView != null && clientView.roomId().equals(end.roomId())
                && clientView.dimension().equals(end.dimension())) {
            clearClientView();
        }
    }

    public void clearClientView() {
        clientView = null;
        clientPreview = null;
        clientTicks = 0;
    }

    public int getClientTicks() {
        return clientTicks;
    }

    public void tickClientView() {
        if (clientView != null && ++clientTicks >= clientView.durationTicks()) {
            clearClientView();
        }
    }

    @Nullable
    public Entity getClientPreview() {
        return clientPreview;
    }

    public void setClientPreview(@Nullable Entity preview) {
        clientPreview = preview;
    }
}
