package rotp.core.api.time;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import rotp.core.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Owner-keyed rules for players a time erase (King Crimson) does not fully take, so that the add-on that erases and
 * those that resist it meet here and never call each other. The owner: "黃鎮持有者能看到時刪狀態下的玩家，同時時刪狀態下玩家看到的黃鎮
 * 持有者的位置雖然是矢量偏移後的假身位置，但黃鎮玩家真身在時刪結束後不會被強制移動到假身". Two separate questions, so that a rule may answer
 * one of them only; any rule that answers yes wins, and a rule that fails counts as no. The eraser's add-on asks; Gold
 * Experience Requiem answers both for its holders. Without a rule every answer is no and nothing is asked.
 */
public final class TimeEraseWitnesses {

	public enum Question {
		/** The witness sees the eraser during his erase, drawn as he is at his real place. */
		SEES_ERASER,
		/** The witness is never moved at the end of an erase to where the eraser saw him. */
		KEEPS_PLACE
	}

	@FunctionalInterface
	public interface Witness {
		/** @return true if the answer to the question is yes for this witness of this eraser's erase */
		boolean answers(Player witness, LivingEntity eraser, Question question);
	}

	private static final Map<ResourceLocation, Witness> WITNESSES = new LinkedHashMap<>();
	private static volatile ResourceLocation[] owners = new ResourceLocation[0];
	private static volatile Witness[] witnesses = new Witness[0];

	private TimeEraseWitnesses() {}

	public static synchronized void register(ResourceLocation owner, Witness witness) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(witness, "witness");
		if (WITNESSES.putIfAbsent(owner, witness) != null) {
			throw new IllegalStateException("Duplicate time erase witness: " + owner);
		}
		owners = WITNESSES.keySet().toArray(new ResourceLocation[0]);
		witnesses = WITNESSES.values().toArray(new Witness[0]);
	}

	public static boolean hasWitnesses() {
		return witnesses.length > 0;
	}

	/** Whether the player sees the eraser during his erase, at his real place ({@link Question#SEES_ERASER}). */
	public static boolean seesEraser(Player witness, LivingEntity eraser) {
		return answers(witness, eraser, Question.SEES_ERASER);
	}

	/** Whether the player is never moved at the end of the eraser's erase ({@link Question#KEEPS_PLACE}). */
	public static boolean keepsPlace(Player witness, LivingEntity eraser) {
		return answers(witness, eraser, Question.KEEPS_PLACE);
	}

	public static boolean answers(Player witness, LivingEntity eraser, Question question) {
		Witness[] all = witnesses;
		if (all.length == 0 || witness == null || eraser == null) {
			return false;
		}
		ResourceLocation[] keys = owners;
		for (int i = 0; i < all.length && i < keys.length; i++) {
			try {
				if (all[i].answers(witness, eraser, question)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Time erase witness {} failed.", keys[i], error);
			}
		}
		return false;
	}
}
