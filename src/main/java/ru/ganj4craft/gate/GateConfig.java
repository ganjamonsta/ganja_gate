package ru.ganj4craft.gate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Конфигурация GanjaGate.
 *
 * Автоматически генерирует whitelist из:
 * 1. Загруженных серверных модов (ModList.get())
 * 2. Всех .jar файлов в mods/ и client_mods/ (включая вложенные JarJar моды в META-INF/jarjar/ и META-INF/jars/)
 *
 * Формат: config/ganjagate.json
 */
public class GateConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("ganjagate.json");

    // Моды, которые ВСЕГДА разрешены (системные/ванильные/библиотеки)
    private static final Set<String> SYSTEM_MODS = Set.of(
        "minecraft", "neoforge", "ganjagate",
        "fml", "forge", "neoforged", "java"
    );

    private static Set<String> whitelist = new HashSet<>();
    private static boolean strictMode = true;
    private static boolean autoUpdateWhitelist = true;
    private static String kickMessage = "§c[GanjaGate] Обнаружены неразрешённые моды!\\n\\n§fЗапрещённые моды:\\n§e{mods}\\n\\n§7Удалите их и перезайдите.";

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                JsonObject root = GSON.fromJson(json, JsonObject.class);

                strictMode = root.has("strict") ? root.get("strict").getAsBoolean() : true;
                autoUpdateWhitelist = root.has("auto_update_whitelist") ? root.get("auto_update_whitelist").getAsBoolean() : true;
                
                if (root.has("kick_message")) {
                    kickMessage = root.get("kick_message").getAsString();
                }

                if (root.has("whitelist")) {
                    whitelist.clear();
                    for (JsonElement el : root.getAsJsonArray("whitelist")) {
                        whitelist.add(el.getAsString().toLowerCase());
                    }
                }

                GanjaGate.LOGGER.info("[GanjaGate] Config loaded from {}", CONFIG_PATH);
            } catch (Exception e) {
                GanjaGate.LOGGER.error("[GanjaGate] Failed to load config, regenerating", e);
                generateDefault();
            }
        } else {
            generateDefault();
        }

        // Автоматически добавить моды из папки mods/ и client_mods/ в whitelist
        if (autoUpdateWhitelist) {
            updateWhitelistFromServer();
        }

        // Системные моды всегда разрешены
        whitelist.addAll(SYSTEM_MODS);
    }

    /**
     * Сканирует моды на сервере:
     * 1. Загруженные NeoForge
     * 2. Все jar файлы в папке mods/ (включая client-*.jar и JarJar)
     * 3. Все jar файлы в папке client_mods/
     */
    private static void updateWhitelistFromServer() {
        Set<String> discoveredMods = new HashSet<>();

        // 1. Загруженные серверные моды
        ModList.get().getMods().forEach(modInfo -> {
            discoveredMods.add(modInfo.getModId().toLowerCase());
        });

        // 2. Сканируем папку mods/ и client_mods/ для поиска client-*.jar и JarJar модов
        Path gameDir = FMLPaths.GAMEDIR.get();
        scanDirectoryForModIds(gameDir.resolve("mods"), discoveredMods);
        scanDirectoryForModIds(gameDir.resolve("client_mods"), discoveredMods);

        int added = 0;
        for (String modId : discoveredMods) {
            if (whitelist.add(modId)) {
                added++;
            }
        }

        if (added > 0) {
            GanjaGate.LOGGER.info("[GanjaGate] Auto-added {} mods (including client & JarJar mods) to whitelist (total: {})", added, whitelist.size());
            save();
        }
    }

    /**
     * Просканировать директорию на наличие JAR файлов и извлечь их modId
     */
    private static void scanDirectoryForModIds(Path dir, Set<String> outModIds) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jarPath : stream) {
                try (InputStream fis = Files.newInputStream(jarPath)) {
                    scanZipStream(fis, outModIds, 0);
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            GanjaGate.LOGGER.warn("[GanjaGate] Failed to scan directory {}", dir, e);
        }
    }

    /**
     * Рекурсивно сканирует ZipInputStream для поиска манифестов (TOML/JSON) и вложенных JarJar архивов
     */
    private static void scanZipStream(InputStream inStream, Set<String> outModIds, int depth) {
        if (depth > 2) return; // Предотвращение рекурсии
        try (ZipInputStream zipIn = new ZipInputStream(inStream)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.equals("META-INF/neoforge.mods.toml") || name.equals("META-INF/mods.toml")) {
                    parseTomlManifest(zipIn, outModIds);
                } else if (name.equals("fabric.mod.json") || name.equals("quilt.mod.json")) {
                    parseJsonManifest(zipIn, outModIds);
                } else if ((name.startsWith("META-INF/jarjar/") || name.startsWith("META-INF/jars/")) && name.endsWith(".jar")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zipIn.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    scanZipStream(new ByteArrayInputStream(baos.toByteArray()), outModIds, depth + 1);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void parseTomlManifest(InputStream is, Set<String> outModIds) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            boolean inModsBlock = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) continue;

                if (trimmed.startsWith("[[")) {
                    inModsBlock = trimmed.startsWith("[[mods]]");
                } else if (inModsBlock && trimmed.contains("=")) {
                    String[] parts = trimmed.split("=", 2);
                    if (parts[0].trim().equals("modId")) {
                        String modId = parts[1].trim();
                        // Убираем кавычки и комментарии в конце строки (#mandatory и т.д.)
                        int commentIdx = modId.indexOf('#');
                        if (commentIdx >= 0) modId = modId.substring(0, commentIdx).trim();
                        modId = modId.replace("\"", "").replace("'", "").toLowerCase();

                        if (!modId.isEmpty() && !modId.contains("${")) {
                            outModIds.add(modId);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void parseJsonManifest(InputStream is, Set<String> outModIds) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                if (json.has("id")) {
                    outModIds.add(json.get("id").getAsString().toLowerCase());
                }
                if (json.has("provides") && json.get("provides").isJsonArray()) {
                    for (JsonElement el : json.getAsJsonArray("provides")) {
                        outModIds.add(el.getAsString().toLowerCase());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void generateDefault() {
        whitelist.clear();
        whitelist.addAll(SYSTEM_MODS);
        updateWhitelistFromServer();
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("strict", strictMode);
            root.addProperty("auto_update_whitelist", autoUpdateWhitelist);
            root.addProperty("kick_message", kickMessage);

            JsonArray arr = new JsonArray();
            whitelist.stream().sorted().forEach(arr::add);
            root.add("whitelist", arr);

            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(root));
        } catch (IOException e) {
            GanjaGate.LOGGER.error("[GanjaGate] Failed to save config", e);
        }
    }

    public static Set<String> getWhitelist() {
        return Collections.unmodifiableSet(whitelist);
    }

    public static boolean isStrictMode() {
        return strictMode;
    }

    public static String getKickMessage() {
        return kickMessage;
    }

    public static List<String> checkClientMods(Collection<String> clientModIds) {
        List<String> forbidden = new ArrayList<>();
        for (String modId : clientModIds) {
            String lower = modId.toLowerCase();
            if (!whitelist.contains(lower) && !SYSTEM_MODS.contains(lower)) {
                forbidden.add(modId);
            }
        }
        return forbidden;
    }
}
