package rotp.addons.ger.client;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import rotp.core.api.client.render.StandAuraFx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The gold of a hit returned to zero, as every client draws it: the Disabler's
 * user glows for its window, and each answer flashes on the user and on the
 * attacker it put back.
 */
public final class GerAnswerClientState {
    public static final int GOLD = GerRtzGhostFade.GOLD;
    public static final int FULL_FLASH_TICKS = 12;
    public static final int LIGHT_FLASH_TICKS = 5;

    private static final Int2IntMap GLOWING = new Int2IntOpenHashMap();
    private static final Int2IntMap FLASHING = new Int2IntOpenHashMap();

    private GerAnswerClientState() {}

    public static void glow(int entityId, int ticks) {
        if (ticks > 0) {
            GLOWING.put(entityId, ticks);
        }
        else {
            GLOWING.remove(entityId);
        }
    }

    public static void flash(int userId, int attackerId, boolean full) {
        int ticks = full ? FULL_FLASH_TICKS : LIGHT_FLASH_TICKS;
        FLASHING.merge(userId, ticks, Math::max);
        if (attackerId >= 0) {
            FLASHING.merge(attackerId, ticks, Math::max);
        }
    }

    public static boolean isGlowing(int entityId) {
        return GLOWING.containsKey(entityId);
    }

    public static void tick() {
        countDown(GLOWING);
        countDown(FLASHING);
    }

    private static void countDown(Int2IntMap ticks) {
        if (ticks.isEmpty()) {
            return;
        }
        var entries = ticks.int2IntEntrySet().iterator();
        while (entries.hasNext()) {
            Int2IntMap.Entry entry = entries.next();
            if (entry.getIntValue() <= 1) {
                entries.remove();
            }
            else {
                entry.setValue(entry.getIntValue() - 1);
            }
        }
    }

    public static void reset() {
        GLOWING.clear();
        FLASHING.clear();
    }

    /** Asks the core's aura for every entity in gold, each frame, where the rewind asks for its own. */
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY
                || GLOWING.isEmpty() && FLASHING.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        queue(minecraft, GLOWING);
        queue(minecraft, FLASHING);
    }

    private static void queue(Minecraft minecraft, Int2IntMap ticks) {
        for (int entityId : ticks.keySet()) {
            Entity entity = minecraft.level.getEntity(entityId);
            if (entity != null && entity.isAlive()) {
                StandAuraFx.renderAura(entity, GOLD);
            }
        }
    }
}
