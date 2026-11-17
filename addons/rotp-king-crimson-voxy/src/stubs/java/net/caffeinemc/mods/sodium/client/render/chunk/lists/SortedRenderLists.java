package net.caffeinemc.mods.sodium.client.render.chunk.lists;

/** Compile-time stand-in; the real type comes from Sodium at run time. */
public class SortedRenderLists implements ChunkRenderListIterable {
    public static SortedRenderLists empty() {
        throw new AssertionError();
    }
}
