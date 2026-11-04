package rotp.core.api.time.event;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Posted on the game bus by a rewind (Gold Experience Requiem's Return to Zero, Bites the Dust, Mandom) on the server
 * right after it has put things back, so that every time ability that began after the rewind's point inside its region
 * can end (rule G1); older ones go on. The core ends its time stops that began after the point and meet the region;
 * a time erase's add-on ends its erases the same way. Not cancelable: the rewind has happened.
 */
public class RewindAppliedEvent extends Event {
	@Nullable private final LivingEntity rewinder;
	private final ServerLevel level;
	private final long rewindPoint;
	private final AABB region;
	private final ResourceLocation source;

	public RewindAppliedEvent(@Nullable LivingEntity rewinder, ServerLevel level, long rewindPoint, AABB region,
			ResourceLocation source) {
		this.rewinder = rewinder;
		this.level = level;
		this.rewindPoint = rewindPoint;
		this.region = region;
		this.source = source;
	}

	public static void post(@Nullable LivingEntity rewinder, ServerLevel level, long rewindPoint, AABB region,
			ResourceLocation source) {
		NeoForge.EVENT_BUS.post(new RewindAppliedEvent(rewinder, level, rewindPoint, region, source));
	}

	/** Who rewound, if anyone. */
	@Nullable
	public LivingEntity getRewinder() {
		return rewinder;
	}

	public ServerLevel getLevel() {
		return level;
	}

	/** The level's game time the rewind went back to (the mark, the marker, the sample). */
	public long getRewindPoint() {
		return rewindPoint;
	}

	/** The rewound region. */
	public AABB getRegion() {
		return region;
	}

	/** The ability that rewound, e.g. rotp_ger:return_to_zero. */
	public ResourceLocation getSource() {
		return source;
	}
}
