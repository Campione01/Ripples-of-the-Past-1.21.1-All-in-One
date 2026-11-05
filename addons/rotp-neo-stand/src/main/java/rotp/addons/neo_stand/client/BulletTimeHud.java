package rotp.addons.neo_stand.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import rotp.core.api.client.time.ClientRegionalTimeDilationQuery;
import rotp.core.api.client.time.ClientRegionalTimeDilationSurface;
import rotp.core.api.perception.PerceptionBlockers;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.client.ClientTimeStopHandler;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.addons.neo_stand.ability.BulletTimeService;
import rotp.addons.neo_stand.network.BulletTimeSyncPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class BulletTimeHud implements LayeredDraw.Layer {
    private static final Map<UUID, ClientField> FIELDS =
            new HashMap<>();
    private static ClientLevel trackedLevel;

    public static void accept(BulletTimeSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        resetForLevel(minecraft.level);
        if (!payload.active()) {
            FIELDS.remove(payload.owner());
            return;
        }
        if (minecraft.level == null
                || !minecraft.level.dimension().location().equals(
                        payload.dimension())) {
            return;
        }
        FIELDS.put(
                payload.owner(),
                new ClientField(
                        payload.dimension(),
                        Vec3.atCenterOf(payload.center()),
                        payload.radiusBlocks(),
                        payload.remainingTicks(),
                        payload.dilationFactor(),
                        minecraft.level.getGameTime()));
    }

    public static float dilationAt(Vec3 position) {
        Minecraft minecraft = Minecraft.getInstance();
        resetForLevel(minecraft.level);
        if (minecraft.level == null) {
            return 1.0F;
        }
        advanceFields(minecraft.level.getGameTime());
        float factor = 1.0F;
        for (ClientField field : FIELDS.values()) {
            if (isCentreTimeStopped(field)
                    || !field.dimension().equals(
                            minecraft.level.dimension().location())
                    || horizontalDistanceSqr(
                                    field.center(), position)
                            > (double) field.radiusBlocks()
                                    * field.radiusBlocks()) {
                continue;
            }
            factor = Math.min(factor, field.dilationFactor());
        }
        return factor;
    }

    public static float dilation(
            ClientRegionalTimeDilationQuery query) {
        Minecraft minecraft = Minecraft.getInstance();
        resetForLevel(minecraft.level);
        if (minecraft.level != null) {
            advanceFields(minecraft.level.getGameTime());
        }
        if (ClientTimeStopHandler.isTimeStoppedStatic()
                || !isFinite(query.position())) {
            return 1.0F;
        }
        if (query.surface()
                        != ClientRegionalTimeDilationSurface.TIMER
                && query.localPlayer() != null
                && isOwnerActive(
                        query.localPlayer().getUUID())
                && query.position().distanceToSqr(
                        query.localPlayer().position())
                        <= 36.0D) {
            return 1.0F;
        }
        // The timer is how a player inside another's field is slowed: it
        // follows the being rule of BulletTimeService (an exempt player, or
        // one another source holds back more slowly, keeps his own time)
        if (query.surface() == ClientRegionalTimeDilationSurface.TIMER
                && query.localPlayer() != null
                && !FIELDS.isEmpty()
                && !isOwnerActive(query.localPlayer().getUUID())
                && !EntityTimeRates.governs(
                        EntityTimeRates.Channel.BEING,
                        query.localPlayer(),
                        BulletTimeService.TIME_RATE_OWNER)) {
            return 1.0F;
        }
        return dilationAt(query.position());
    }

    /**
     * The client's answer for BulletTimeService.dilationFor: the slowest
     * synced field around the entity, leaving out the field owners and
     * their Stands and whatever a field's owner cannot perceive.
     */
    public static double fieldDilationFor(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        resetForLevel(minecraft.level);
        ClientLevel level = minecraft.level;
        if (level == null
                || FIELDS.isEmpty()
                || entity.level() != level) {
            return 1.0D;
        }
        advanceFields(level.getGameTime());
        LivingEntity standUser = entity instanceof StandEntity stand
                ? stand.getUser()
                : null;
        if (FIELDS.containsKey(entity.getUUID())
                || standUser != null
                        && FIELDS.containsKey(standUser.getUUID())) {
            return 1.0D;
        }
        Vec3 position = entity.position();
        double factor = 1.0D;
        for (Map.Entry<UUID, ClientField> entry : FIELDS.entrySet()) {
            ClientField field = entry.getValue();
            if (isCentreTimeStopped(field)
                    || !field.dimension().equals(
                            level.dimension().location())
                    || horizontalDistanceSqr(field.center(), position)
                            > (double) field.radiusBlocks()
                                    * field.radiusBlocks()
                    || PerceptionBlockers.isBlocked(
                            level.getPlayerByUUID(entry.getKey()),
                            entity)) {
                continue;
            }
            factor = Math.min(factor, field.dilationFactor());
        }
        return factor;
    }

    public static boolean isOwnerActive(UUID owner) {
        Minecraft minecraft = Minecraft.getInstance();
        resetForLevel(minecraft.level);
        if (minecraft.level == null) {
            return false;
        }
        advanceFields(minecraft.level.getGameTime());
        return FIELDS.containsKey(owner);
    }

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        resetForLevel(minecraft.level);
        if (minecraft.options.hideGui
                || minecraft.player == null
                || minecraft.level == null) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        advanceFields(gameTime);
        ClientField field =
                FIELDS.get(minecraft.player.getUUID());
        if (field == null) {
            return;
        }
        long ticks = Math.max(0L, field.remainingTicks());
        String time = String.format(
                Locale.ROOT,
                "%d.%ds",
                ticks / 20L,
                ticks % 20L / 2L);
        int y = graphics.guiHeight() - 30;
        graphics.drawString(
                minecraft.font,
                Component.translatable(
                        "hud.neojojostand.bullet_time"),
                10,
                y,
                0xFFFFFF,
                true);
        graphics.drawString(
                minecraft.font,
                time,
                10,
                // 1.16 put the countdown at screen height - 15, fifteen pixels under the label
                y + 15,
                0xFFFFFF,
                true);
    }

    private static void resetForLevel(ClientLevel level) {
        if (trackedLevel != level) {
            FIELDS.clear();
            trackedLevel = level;
        }
    }

    private static void advanceFields(long gameTime) {
        Iterator<ClientField> iterator =
                FIELDS.values().iterator();
        while (iterator.hasNext()) {
            ClientField field = iterator.next();
            field.advance(gameTime, isCentreTimeStopped(field));
            if (field.remainingTicks() <= 0L) {
                iterator.remove();
            }
        }
    }

    private static boolean isCentreTimeStopped(ClientField field) {
        return TimeStopState.getClientDisplayInstance(
                        new ChunkPos(BlockPos.containing(field.center())))
                .isPresent();
    }

    private static double horizontalDistanceSqr(
            Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    private static boolean isFinite(Vec3 position) {
        return Double.isFinite(position.x)
                && Double.isFinite(position.y)
                && Double.isFinite(position.z);
    }

    private static final class ClientField {
        private final ResourceLocation dimension;
        private final Vec3 center;
        private final int radiusBlocks;
        private final float dilationFactor;
        private long remainingTicks;
        private long lastGameTime;

        private ClientField(
                ResourceLocation dimension,
                Vec3 center,
                int radiusBlocks,
                long remainingTicks,
                float dilationFactor,
                long lastGameTime) {
            this.dimension = dimension;
            this.center = center;
            this.radiusBlocks = radiusBlocks;
            this.remainingTicks = remainingTicks;
            this.dilationFactor = dilationFactor;
            this.lastGameTime = lastGameTime;
        }

        private ResourceLocation dimension() {
            return dimension;
        }

        private Vec3 center() {
            return center;
        }

        private int radiusBlocks() {
            return radiusBlocks;
        }

        private long remainingTicks() {
            return remainingTicks;
        }

        private float dilationFactor() {
            return dilationFactor;
        }

        private void advance(
                long gameTime, boolean timeStopped) {
            long elapsed = Math.max(
                    0L, gameTime - lastGameTime);
            lastGameTime = gameTime;
            if (!timeStopped) {
                remainingTicks -= elapsed;
            }
        }
    }
}
