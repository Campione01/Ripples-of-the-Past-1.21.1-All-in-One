package rotp.addons.echoes.state;

import java.util.UUID;

import javax.annotation.Nullable;

/**
 * R772 F11: the word ACT2's tail left on a living target (the master's OnomatopoeiaAttachHandler.AttachedEntityData).
 * Like the master's, it is not saved: it ends when the target unloads, as the master's in-memory map did.
 */
public final class EchoesOnomatopoeiaMark {
    @Nullable private UUID owner;
    @Nullable private EchoesOnomatopoeia word;
    private int variant;
    private boolean triggered;
    private long nextTantan;

    public void set(UUID owner, EchoesOnomatopoeia word, int variant) {
        this.owner = owner;
        this.word = word;
        this.variant = variant;
        this.triggered = false;
        this.nextTantan = Long.MIN_VALUE;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    @Nullable
    public EchoesOnomatopoeia word() {
        return word;
    }

    public int variant() {
        return variant;
    }

    public boolean triggered() {
        return triggered;
    }

    public void markTriggered() {
        triggered = true;
    }

    public long nextTantan() {
        return nextTantan;
    }

    public void setNextTantan(long gameTime) {
        nextTantan = gameTime;
    }
}
