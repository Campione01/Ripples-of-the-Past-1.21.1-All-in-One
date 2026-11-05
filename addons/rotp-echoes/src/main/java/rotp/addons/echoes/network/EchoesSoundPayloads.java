package rotp.addons.echoes.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.state.EchoesSoundService;
import rotp.addons.echoes.state.EchoesUserState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * R772 F11: ACT1's sounds between the client and the server (the master's SoundSelectPacket, SoundFavoritesPacket,
 * SoundNameOverridePacket, SoundSyncPacket, SoundNameSyncPacket, BlockMarkSyncPacket, SoundPlaybackPacket,
 * SoundLoopStartPacket, SoundLoopStopPacket and SoundGiveSyncPacket). Sound ids travel as strings; an empty one is none.
 */
public final class EchoesSoundPayloads {
    private static Consumer<Sync> clientSync = payload -> {};
    private static Consumer<Play> clientPlay = payload -> {};
    private static Consumer<Loop> clientLoop = payload -> {};
    private static Consumer<GiveTargets> clientGiveTargets = payload -> {};

    private EchoesSoundPayloads() {}

    /** c2s: the chosen sound, empty to choose none. */
    public record Select(String sound) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(RotpEchoes.id("sound_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Select> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Select::sound, Select::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** c2s: a sound in or out of the favourites. */
    public record Favorite(String sound, boolean favorite) implements CustomPacketPayload {
        public static final Type<Favorite> TYPE = new Type<>(RotpEchoes.id("sound_favorite"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Favorite> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Favorite::sound, ByteBufCodecs.BOOL, Favorite::favorite, Favorite::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** c2s: the player's own name for a sound, empty to drop it. */
    public record Rename(String sound, String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(RotpEchoes.id("sound_rename"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rename> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Rename::sound, ByteBufCodecs.STRING_UTF8, Rename::name, Rename::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** s2c: the user's sound state. */
    public record Sync(String sound, boolean selfSound, List<String> favorites, Map<String, String> names, int marks)
            implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(RotpEchoes.id("sound_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Sync::sound,
                ByteBufCodecs.BOOL, Sync::selfSound,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Sync::favorites,
                ByteBufCodecs.<ByteBuf, String, String, Map<String, String>>map(
                        HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), Sync::names,
                ByteBufCodecs.VAR_INT, Sync::marks,
                Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** s2c: one sound to the player. */
    public record Play(String sound, float pitch) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(RotpEchoes.id("sound_play"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Play> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Play::sound, ByteBufCodecs.FLOAT, Play::pitch, Play::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** s2c: the sound the player keeps hearing, empty to stop it. */
    public record Loop(String sound) implements CustomPacketPayload {
        public static final Type<Loop> TYPE = new Type<>(RotpEchoes.id("sound_loop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Loop> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Loop::sound, Loop::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** s2c: the living entities the caster gave its sound to (marked on its screen). */
    public record GiveTargets(List<UUID> targets) implements CustomPacketPayload {
        public static final Type<GiveTargets> TYPE = new Type<>(RotpEchoes.id("sound_give_targets"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GiveTargets> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), GiveTargets::targets, GiveTargets::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(Select.TYPE, Select.STREAM_CODEC, EchoesSoundPayloads::handleSelect)
                .playToServer(Favorite.TYPE, Favorite.STREAM_CODEC, EchoesSoundPayloads::handleFavorite)
                .playToServer(Rename.TYPE, Rename.STREAM_CODEC, EchoesSoundPayloads::handleRename)
                .playToClient(Sync.TYPE, Sync.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> clientSync.accept(payload)))
                .playToClient(Play.TYPE, Play.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> clientPlay.accept(payload)))
                .playToClient(Loop.TYPE, Loop.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> clientLoop.accept(payload)))
                .playToClient(GiveTargets.TYPE, GiveTargets.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> clientGiveTargets.accept(payload)));
    }

    public static void installClientHandlers(Consumer<Sync> sync, Consumer<Play> play, Consumer<Loop> loop,
            Consumer<GiveTargets> giveTargets) {
        clientSync = Objects.requireNonNull(sync);
        clientPlay = Objects.requireNonNull(play);
        clientLoop = Objects.requireNonNull(loop);
        clientGiveTargets = Objects.requireNonNull(giveTargets);
    }

    @Nullable
    public static ResourceLocation id(String sound) {
        return sound.isEmpty() ? null : ResourceLocation.tryParse(sound);
    }

    private static void handleSelect(Select payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                StandPower power = StandPower.get(player);
                if (power != null && power.getPowerType() == EchoesStands.ECHOES_ACT1.get()) {
                    EchoesSoundService.select(player, id(payload.sound()));
                }
            }
        });
    }

    private static void handleFavorite(Favorite payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ResourceLocation sound = id(payload.sound());
            if (context.player() instanceof ServerPlayer player && sound != null) {
                EchoesSoundService.setFavorite(player, sound, payload.favorite());
            }
        });
    }

    private static void handleRename(Rename payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ResourceLocation sound = id(payload.sound());
            if (context.player() instanceof ServerPlayer player && sound != null) {
                EchoesSoundService.rename(player, sound, payload.name());
            }
        });
    }

    public static void sendSync(ServerPlayer player, EchoesUserState state, int marks) {
        List<String> favorites = new ArrayList<>();
        state.soundFavorites().forEach(id -> favorites.add(id.toString()));
        Map<String, String> names = new HashMap<>();
        state.soundNames().forEach((id, name) -> names.put(id.toString(), name));
        PacketDistributor.sendToPlayer(player, new Sync(
                state.sound() != null ? state.sound().toString() : "", state.selfSound(), favorites, names, marks));
    }

    public static void sendPlay(ServerPlayer player, ResourceLocation sound, float pitch) {
        PacketDistributor.sendToPlayer(player, new Play(sound.toString(), pitch));
    }

    public static void sendLoop(ServerPlayer player, @Nullable ResourceLocation sound) {
        PacketDistributor.sendToPlayer(player, new Loop(sound != null ? sound.toString() : ""));
    }

    public static void sendGiveTargets(ServerPlayer caster, List<UUID> targets) {
        PacketDistributor.sendToPlayer(caster, new GiveTargets(targets));
    }
}
