package ru.ganj4craft.gate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * ClientGateCustomizer — Нативная кастомизация заголовка и иконки окна Minecraft (GLFW/STB).
 */
public class ClientGateCustomizer {
    public static final String BRAND_TITLE = "Ganj4Craft Season 4: Cyber & Magic";
    private static boolean iconLoaded = false;

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientGateCustomizer::onClientSetup);
        NeoForge.EVENT_BUS.addListener(ClientGateCustomizer::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ClientGateCustomizer::onClientTick);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            applyTitle();
            applyIcon();
        });
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        applyTitle();
        if (!iconLoaded) {
            applyIcon();
        }
    }

    private static int tickCount = 0;
    private static void onClientTick(ClientTickEvent.Post event) {
        tickCount++;
        if (tickCount % 20 == 0) { // every 1 second
            applyTitle();
            if (!iconLoaded) {
                applyIcon();
            }
        }
    }

    public static void applyTitle() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getWindow() != null) {
                LocalPlayer player = mc.player;
                String nick = player != null ? player.getGameProfile().getName() : null;
                String title = nick != null ? (BRAND_TITLE + " — [" + nick + "]") : BRAND_TITLE;
                mc.getWindow().setTitle(title);
            }
        } catch (Exception ignored) {}
    }

    private static ByteBuffer loadResourceBytes(String path) {
        try (InputStream in = ClientGateCustomizer.class.getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
            buf.put(bytes);
            buf.flip();
            return buf;
        } catch (Exception e) {
            return null;
        }
    }

    public static void applyIcon() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            long windowHandle = mc.getWindow().getWindow();
            if (windowHandle == 0) return;

            String[] paths = new String[]{
                "/assets/ganjagate/icons/icon_16x16.png",
                "/assets/ganjagate/icons/icon_32x32.png",
                "/assets/ganjagate/icons/icon_48x48.png"
            };

            ByteBuffer[] rawBuffers = new ByteBuffer[paths.length];
            ByteBuffer[] pixelBuffers = new ByteBuffer[paths.length];
            int loadedCount = 0;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer comp = stack.mallocInt(1);

                for (int i = 0; i < paths.length; i++) {
                    rawBuffers[i] = loadResourceBytes(paths[i]);
                    if (rawBuffers[i] != null) {
                        pixelBuffers[i] = STBImage.stbi_load_from_memory(rawBuffers[i], w, h, comp, 4);
                        if (pixelBuffers[i] != null) {
                            loadedCount++;
                        }
                    }
                }

                if (loadedCount > 0) {
                    GLFWImage.Buffer imageBuffer = GLFWImage.malloc(loadedCount);
                    int idx = 0;
                    for (int i = 0; i < paths.length; i++) {
                        if (pixelBuffers[i] != null) {
                            rawBuffers[i].position(0);
                            STBImage.stbi_info_from_memory(rawBuffers[i], w, h, comp);
                            imageBuffer.get(idx).width(w.get(0)).height(h.get(0)).pixels(pixelBuffers[i]);
                            idx++;
                        }
                    }

                    GLFW.glfwSetWindowIcon(windowHandle, imageBuffer);
                    imageBuffer.free();
                    iconLoaded = true;
                    GanjaGate.LOGGER.info("[GanjaGate] Custom GLFW window icon applied successfully!");
                }
            } finally {
                for (ByteBuffer pb : pixelBuffers) {
                    if (pb != null) STBImage.stbi_image_free(pb);
                }
                for (ByteBuffer rb : rawBuffers) {
                    if (rb != null) MemoryUtil.memFree(rb);
                }
            }
        } catch (Exception e) {
            GanjaGate.LOGGER.warn("[GanjaGate] Failed to apply custom window icon: {}", e.getMessage());
        }
    }
}
