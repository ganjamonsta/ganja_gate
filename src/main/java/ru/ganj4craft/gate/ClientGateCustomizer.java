package ru.ganj4craft.gate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
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
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ClientGateCustomizer — Нативная кастомизация заголовка и иконки окна Minecraft (GLFW/STB).
 * Параметры настраиваются через config/ganjagate.json
 */
public class ClientGateCustomizer {
    private static boolean iconLoaded = false;

    public static void init(IEventBus modBus) {
        GateConfig.load();
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
        if (!GateConfig.isEnableCustomTitle()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getWindow() != null) {
                LocalPlayer player = mc.player;
                String nick = player != null ? player.getGameProfile().getName() : null;
                String title;
                if (nick != null && !nick.isBlank()) {
                    String template = GateConfig.getTitleInGameTemplate();
                    title = template.replace("{player}", nick);
                } else {
                    title = GateConfig.getTitleTemplate();
                }
                mc.getWindow().setTitle(title);
            }
        } catch (Exception ignored) {}
    }

    private static ByteBuffer loadResourceBytes(String externalPath, String fallbackClasspath) {
        if (externalPath != null && !externalPath.isBlank()) {
            try {
                Path p = Path.of(externalPath);
                if (!p.isAbsolute()) {
                    p = FMLPaths.GAMEDIR.get().resolve(externalPath);
                }
                if (Files.exists(p)) {
                    byte[] bytes = Files.readAllBytes(p);
                    ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
                    buf.put(bytes);
                    buf.flip();
                    return buf;
                }
            } catch (Exception ignored) {}
        }

        if (fallbackClasspath != null) {
            try (InputStream in = ClientGateCustomizer.class.getResourceAsStream(fallbackClasspath)) {
                if (in == null) return null;
                byte[] bytes = in.readAllBytes();
                ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
                buf.put(bytes);
                buf.flip();
                return buf;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static void applyIcon() {
        if (!GateConfig.isEnableCustomIcon()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            long windowHandle = mc.getWindow().getWindow();
            if (windowHandle == 0) return;

            String[] externalPaths = new String[]{
                GateConfig.getIconPath16(),
                GateConfig.getIconPath32(),
                GateConfig.getIconPath48()
            };

            String[] fallbackPaths = new String[]{
                "/assets/ganjagate/icons/icon_16x16.png",
                "/assets/ganjagate/icons/icon_32x32.png",
                "/assets/ganjagate/icons/icon_48x48.png"
            };

            ByteBuffer[] rawBuffers = new ByteBuffer[fallbackPaths.length];
            ByteBuffer[] pixelBuffers = new ByteBuffer[fallbackPaths.length];
            int loadedCount = 0;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer comp = stack.mallocInt(1);

                for (int i = 0; i < fallbackPaths.length; i++) {
                    rawBuffers[i] = loadResourceBytes(externalPaths[i], fallbackPaths[i]);
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
                    for (int i = 0; i < fallbackPaths.length; i++) {
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
                    GanjaGate.LOGGER.info("[GanjaGate] Custom GLFW window icon applied from config/assets!");
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
