package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import rotp.addons.kingcrimson.epitaph.sandbox.RandomState;

/**
 * Everything of a piece of the world that a prediction must leave as it was: every block and block entity of a box,
 * the saved data of every entity in it, the scoreboard, the raids, the points of interest, the saved random
 * sequences and the level's own random stream. Two pictures are compared entry by entry, and each difference is named.
 */
final class KingCrimsonEpitaphWorldPicture {
    private final Map<String, String> entries = new TreeMap<>();

    static KingCrimsonEpitaphWorldPicture of(ServerLevel level, BlockPos from, BlockPos to) {
        KingCrimsonEpitaphWorldPicture picture = new KingCrimsonEpitaphWorldPicture();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                picture.entries.put("block " + pos.toShortString(), state.toString());
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                picture.entries.put("block entity " + pos.toShortString(),
                        blockEntity.saveWithFullMetadata(level.registryAccess()).toString());
            }
        }
        AABB box = AABB.encapsulatingFullBlocks(from, to).inflate(8.0D);
        for (Entity entity : level.getEntities((Entity) null, box, entity -> true)) {
            picture.entries.put("entity " + entity.getUUID() + " " + entity.getType().toShortString() + " #" + entity.getId(),
                    entity.saveWithoutId(new CompoundTag()).toString());
        }
        Scoreboard scoreboard = level.getScoreboard();
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            picture.entries.put("team " + team.getName(), new ArrayList<>(new java.util.TreeSet<>(team.getPlayers())).toString());
        }
        for (Objective objective : scoreboard.getObjectives()) {
            List<String> scores = new ArrayList<>();
            scoreboard.listPlayerScores(objective).forEach(score -> scores.add(score.owner() + "=" + score.value()));
            scores.sort(null);
            picture.entries.put("objective " + objective.getName(), scores.toString());
        }
        picture.entries.put("raids", level.getRaids().save(new CompoundTag(), level.registryAccess()).toString());
        picture.entries.put("raids dirty", String.valueOf(level.getRaids().isDirty()));
        picture.entries.put("random sequences dirty", String.valueOf(level.getRandomSequences().isDirty()));
        picture.entries.put("level random", String.valueOf(RandomState.of(level.random)));
        picture.entries.put("points of interest with room", String.valueOf(level.getPoiManager()
                .getInRange(type -> true, from, 48, PoiManager.Occupancy.HAS_SPACE).count()));
        picture.entries.put("game time", String.valueOf(level.getGameTime()));
        picture.entries.put("day time", String.valueOf(level.getDayTime()));
        return picture;
    }

    int size() {
        return entries.size();
    }

    /** What is not the same in a later picture, each named; empty when nothing changed. */
    List<String> differences(KingCrimsonEpitaphWorldPicture later) {
        List<String> differences = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String now = later.entries.get(entry.getKey());
            if (now == null) {
                differences.add(entry.getKey() + " is gone");
            } else if (!now.equals(entry.getValue())) {
                differences.add(entry.getKey() + " changed" + where(entry.getValue(), now));
            }
        }
        for (String key : later.entries.keySet()) {
            if (!entries.containsKey(key)) {
                differences.add(key + " is new");
            }
        }
        return differences;
    }

    private static String where(String before, String after) {
        int at = 0;
        while (at < before.length() && at < after.length() && before.charAt(at) == after.charAt(at)) {
            at++;
        }
        int from = Math.max(0, at - 40);
        return " (..." + before.substring(from, Math.min(before.length(), at + 40)) + "... became ..."
                + after.substring(from, Math.min(after.length(), at + 40)) + "...)";
    }
}
