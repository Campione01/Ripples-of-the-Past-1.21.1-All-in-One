package rotp.addons.kingcrimsonvoxy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.opengl.GL45C;

/**
 * The plate map and the near columns on the GPU. The far plan is written straight into a persistently mapped staging buffer off
 * the render thread, and one GPU copy moves it into the buffer the shaders read: the render thread never carries the 10 MB, and
 * no array of that size is made in Java. Both buffers are made at the first erase and kept until logout.
 */
final class KcVoxyWords {
    static final int CAPACITY_WORDS = (KcVoxyPlan.TEXELS * KcVoxyPlan.TEXELS + 1 >> 1) + KcVoxyPlan.MAX_FOOTPRINT_WORDS + KcVoxyPieces.WORDS;
    private static final long BYTES = CAPACITY_WORDS * 4L;
    private static final int MAP_FLAGS = GL30C.GL_MAP_WRITE_BIT | GL44C.GL_MAP_PERSISTENT_BIT | GL44C.GL_MAP_COHERENT_BIT;

    private static int staging;
    private static int device;
    private static ByteBuffer mapped;
    private static long fence;
    private static final AtomicBoolean WRITING = new AtomicBoolean();
    private static boolean closing;

    private KcVoxyWords() {}

    /** The staging memory for one far plan, or null while the last copy or plan still uses it. Render thread. */
    static ByteBuffer acquire() {
        if (closing) {
            return null;
        }
        if (staging == 0) {
            staging = GL45C.glCreateBuffers();
            GL45C.glNamedBufferStorage(staging, BYTES, MAP_FLAGS);
            ByteBuffer map = GL45C.glMapNamedBufferRange(staging, 0L, BYTES, MAP_FLAGS);
            if (map == null) {
                throw new IllegalStateException("the far plan's staging buffer cannot be mapped");
            }
            mapped = map.order(ByteOrder.nativeOrder());
            device = GL45C.glCreateBuffers();
            GL45C.glNamedBufferStorage(device, BYTES, 0);
        }
        if (fence != 0L) {
            int state = GL32C.glClientWaitSync(fence, 0, 0L);
            if (state != GL32C.GL_ALREADY_SIGNALED && state != GL32C.GL_CONDITION_SATISFIED) {
                return null;
            }
            GL32C.glDeleteSync(fence);
            fence = 0L;
        }
        if (!WRITING.compareAndSet(false, true)) {
            return null;
        }
        return mapped.duplicate().order(ByteOrder.nativeOrder());
    }

    /** The plan's writer is done with the memory, whatever its outcome. Any thread. */
    static void release() {
        WRITING.set(false);
    }

    /** Moves a finished plan to the buffer the shaders read. Render thread, after its writer released the memory. */
    static void publish(int words) {
        GL45C.glCopyNamedBufferSubData(staging, device, 0L, 0L, words * 4L);
        fence = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    /** What the shaders bind: the GPU copy once it exists, else the given stand-in. */
    static int buffer(int standIn) {
        return device != 0 ? device : standIn;
    }

    /** At logout. The staging stays mapped until no plan is being written into it. */
    static void close() {
        closing = true;
        tick();
    }

    /** Each frame: finishes a pending close. */
    static void tick() {
        if (!closing || WRITING.get()) {
            return;
        }
        closing = false;
        if (fence != 0L) {
            GL32C.glDeleteSync(fence);
            fence = 0L;
        }
        if (staging != 0) {
            GL45C.glUnmapNamedBuffer(staging);
            GL45C.glDeleteBuffers(staging);
            GL45C.glDeleteBuffers(device);
            staging = 0;
            device = 0;
            mapped = null;
        }
    }
}
