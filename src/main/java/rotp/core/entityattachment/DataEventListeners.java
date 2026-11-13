package rotp.core.entityattachment;

import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

public class DataEventListeners {
	// Sorted maps: a map hashed on the data class visited the data in an order that changed with every JVM run.
	private Map<Class<?>, SynchronizableEntityData> entityDataSync = new TreeMap<>(EntityDataOrder.SYNC_ORDER);
	private Map<Class<?>, SynchronizablePlayerData> playerDataSync = new TreeMap<>(EntityDataOrder.SYNC_ORDER);
	// Data can be attached on a thread that does not tick the entity (a client sound listener ran on the sound engine
	// thread); only the thread that ticks it moves the data into the ticking map.
	private final Queue<TickingEntityData> pendingAddToTick = new ConcurrentLinkedQueue<>();
	private Map<Class<?>, TickingEntityData> ticking = new TreeMap<>(EntityDataOrder.TICK_ORDER);
	private Map<Class<?>, PostNbtReadEntityData> postNbtCallback = new TreeMap<>(EntityDataOrder.SYNC_ORDER);
	
	public DataEventListeners(IAttachmentHolder entity) {}
	
	
	@ApiStatus.Internal
	public void addEntityDataSync(SynchronizableEntityData data) {
		this.entityDataSync.put(data.getClass(), data);
	}
	
	@ApiStatus.Internal
	public void addPlayerDataSync(SynchronizablePlayerData data) {
		this.entityDataSync.put(data.getClass(), data);
		this.playerDataSync.put(data.getClass(), data);
	}
	
	public void addTickingData(TickingEntityData data) {
		this.pendingAddToTick.add(data);
	}
	
	public void addPostNbtReadCallback(PostNbtReadEntityData data) {
		this.postNbtCallback.put(data.getClass(), data);
	}
	
	
	public void onTracking(ServerPlayer tracking) {
		for (var listener : entityDataSync.values()) {
			listener.syncToTracking(tracking);
		}
	}
	
	public void onSyncToPlayer(ServerPlayer player) {
		for (var listener : playerDataSync.values()) {
			listener.syncToPlayer(player);
		}
	}
	
	public void onClone(Player newPlayer, boolean wasDeath) {
		for (var listener : playerDataSync.values()) {
			listener.onPlayerClone(newPlayer, wasDeath);
		}
	}
	
	public void onTick() {
		TickingEntityData attachment;
		while ((attachment = pendingAddToTick.poll()) != null) {
			this.ticking.put(attachment.getClass(), attachment);
		}
		for (var listener : ticking.values()) {
			listener.tick();
		}
	}
	
	public void afterNbtRead() {
		for (var listener : postNbtCallback.values()) {
			listener.afterNbtRead();
		}
	}
	
}
