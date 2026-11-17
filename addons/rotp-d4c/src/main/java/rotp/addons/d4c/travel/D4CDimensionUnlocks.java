package rotp.addons.d4c.travel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Owner 2026-11-10: a real dimension is offered to a player only once he
 * has been in it. The record is the player's own, saved with him and kept
 * through death.
 */
@EventBusSubscriber(
        modid = D4CAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class D4CDimensionUnlocks {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                    D4CAddon.MOD_ID);
    public static final Supplier<AttachmentType<Visited>> VISITED =
            ATTACHMENT_TYPES.register(
                    "visited_dimensions",
                    () -> AttachmentType.builder(() -> Visited.NONE)
                            .serialize(Visited.CODEC)
                            .copyOnDeath()
                            .build());
    private static final ResourceLocation NETHER_ROOT =
            ResourceLocation.withDefaultNamespace("nether/root");
    private static final ResourceLocation END_ROOT =
            ResourceLocation.withDefaultNamespace("end/root");

    private D4CDimensionUnlocks() {}

    public record Visited(boolean seeded, List<ResourceLocation> dimensions) {
        public static final Visited NONE = new Visited(false, List.of());
        public static final Codec<Visited> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.BOOL.fieldOf("seeded")
                                .forGetter(Visited::seeded),
                        ResourceLocation.CODEC.listOf()
                                .fieldOf("dimensions")
                                .forGetter(Visited::dimensions))
                        .apply(instance, Visited::new));

        public Visited {
            dimensions = List.copyOf(dimensions);
        }
    }

    public static boolean isUnlocked(ServerPlayer player, ResourceLocation id) {
        return player.isCreative() || visited(player).contains(id);
    }

    /** The record, made first from the old-save rule when it is missing. */
    public static Set<ResourceLocation> visited(ServerPlayer player) {
        Visited record = ensureSeeded(player);
        return new LinkedHashSet<>(record.dimensions());
    }

    public static void recordVisit(
            ServerPlayer player, ResourceKey<Level> dimension) {
        record(player, dimension.location());
    }

    /** Adds a dimension to the record if a visit there counts. */
    public static void record(ServerPlayer player, ResourceLocation id) {
        ensureSeeded(player);
        add(player, id);
    }

    private static void add(ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.getServer();
        if (server == null || !D4CDimensionRules.isRecorded(
                id.toString(),
                D4CDimensionTargets.isRegistered(server, id),
                D4CDimensionTargets.isExcluded(server, id))
                || D4CParallelLevels.isParallel(id)) {
            return;
        }
        Visited record = player.getData(VISITED);
        if (record.dimensions().contains(id)) {
            return;
        }
        List<ResourceLocation> dimensions =
                new java.util.ArrayList<>(record.dimensions());
        dimensions.add(id);
        player.setData(VISITED, new Visited(record.seeded(), dimensions));
    }

    private static Visited ensureSeeded(ServerPlayer player) {
        Visited record = player.getData(VISITED);
        if (record.seeded()) {
            return record;
        }
        // first login after the update (owner may overturn): where he
        // stands, the Overworld, and the Nether and the End by the
        // advancements for having entered them
        Set<String> first = D4CDimensionRules.firstRecord(
                player.level().dimension().location().toString(),
                hasAdvancement(player, NETHER_ROOT),
                hasAdvancement(player, END_ROOT));
        player.setData(VISITED, new Visited(true, record.dimensions()));
        for (String id : first) {
            add(player, ResourceLocation.parse(id));
        }
        return player.getData(VISITED);
    }

    private static boolean hasAdvancement(
            ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.getServer();
        AdvancementHolder holder = server != null
                ? server.getAdvancements().get(id)
                : null;
        return holder != null
                && player.getAdvancements().getOrStartProgress(holder)
                        .isDone();
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordVisit(player, player.level().dimension());
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordVisit(player, event.getTo());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordVisit(player, player.level().dimension());
        }
    }
}
