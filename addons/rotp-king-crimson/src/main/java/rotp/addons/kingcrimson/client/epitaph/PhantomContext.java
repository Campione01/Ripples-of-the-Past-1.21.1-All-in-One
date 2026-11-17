package rotp.addons.kingcrimson.client.epitaph;

/**
 * The flag of the client. While it is set, the code that runs is handling the phantoms of the local player's
 * prediction: a frame of their packets, a tick of theirs, or their drawing. The entities those packets make are kept
 * apart from the world, and a lookup by id answers with a phantom before the body that has the same id. Client thread
 * only; every hook of the client reads it first and does nothing while it is false.
 */
public final class PhantomContext {
    public static boolean active;
    /** Packets of a frame are being handled: what they name is a phantom or nothing, never an entity of the world. */
    public static boolean applying;

    private PhantomContext() {}
}
