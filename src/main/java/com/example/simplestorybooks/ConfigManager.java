package com.example.simplestorybooks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("simplestorybooks");
    private static final Path BOOKS_DIR = CONFIG_DIR.resolve("books");
    private static final File CONFIG_FILE = CONFIG_DIR.resolve("Setting.json").toFile();

    public static float lootChance = 1.0f;
    public static List<String> targetLootTables = new ArrayList<>();
    public static List<BookData> loadedBooks = new ArrayList<>();
    // Optional remote index URL to allow adding/removing books without editing local files
    public static String remoteIndexUrl = null;

    public static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (!Files.exists(BOOKS_DIR)) {
                Files.createDirectories(BOOKS_DIR);
            }

            // Load Main Config
            if (CONFIG_FILE.exists()) {
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json.has("loot_chance")) {
                        lootChance = json.get("loot_chance").getAsFloat();
                    }
                    if (json.has("remote_index")) {
                        remoteIndexUrl = json.get("remote_index").getAsString();
                    }
                    if (json.has("loot_tables")) {
                        // Support both Array (old format) and Object (new format with comments)
                        if (json.get("loot_tables").isJsonArray()) {
                            JsonArray array = json.getAsJsonArray("loot_tables");
                            array.forEach(element -> targetLootTables.add(element.getAsString()));
                        } else if (json.get("loot_tables").isJsonObject()) {
                            JsonObject obj = json.getAsJsonObject("loot_tables");
                            targetLootTables.addAll(obj.keySet());
                        }
                    }
                }
            } else {
                createDefaultConfig();
            }

            // Load Books
            loadBooks();

        } catch (IOException e) {
            SimpleStoryBooks.LOGGER.error("Failed to load config", e);
        }
    }

    private static void createDefaultConfig() {
        targetLootTables.add("minecraft:chests/simple_dungeon");
        targetLootTables.add("minecraft:chests/abandoned_mineshaft");
        targetLootTables.add("minecraft:chests/village/village_plains_house");
        targetLootTables.add("minecraft:chests/stronghold_library");

        JsonObject json = new JsonObject();
        json.addProperty("_comment", "Configuration for Simple Story Books / 简单故事书配置");
        json.addProperty("_instruction", "Modify 'loot_chance' for probability (0.0-1.0). 'loot_tables' keys are the loot tables, values are comments. / 修改 'loot_chance' 调整概率 (0.0-1.0)。'loot_tables' 的键是战利品表，值是注释。");
        
        json.addProperty("loot_chance", lootChance);
        
        JsonObject lootTablesObj = new JsonObject();
        lootTablesObj.addProperty("minecraft:chests/simple_dungeon", "Dungeon / 地牢");
        lootTablesObj.addProperty("minecraft:chests/abandoned_mineshaft", "Mineshaft / 废弃矿井");
        lootTablesObj.addProperty("minecraft:chests/village/village_plains_house", "Village Plains House / 平原村庄房屋");
        lootTablesObj.addProperty("minecraft:chests/stronghold_library", "Stronghold Library / 要塞图书馆");
        lootTablesObj.addProperty("minecraft:chests/end_city_treasure", "End City Treasure / 末地城宝藏 (Disabled by default / 默认未启用)");
        lootTablesObj.addProperty("minecraft:chests/woodland_mansion", "Woodland Mansion / 林地府邸 (Disabled by default / 默认未启用)");
        
        // Add enabled tables that might not be in the list above (though in default config they match)
        for (String table : targetLootTables) {
            if (!lootTablesObj.has(table)) {
                lootTablesObj.addProperty(table, "Custom / 自定义");
            }
        }
        
        json.add("loot_tables", lootTablesObj);
        json.addProperty("remote_index", "");

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            SimpleStoryBooks.LOGGER.error("Failed to create default config", e);
        }
    }


    private static void loadBooks() {
        loadedBooks.clear();
        // 1) Load bundled books from mod resources (optional index.json)
        loadBundledBooks();

        // 1.5) Load remote books if configured
        loadRemoteBooks();

        // 2) Load local config books and let them override bundled/remote ones (by title)
        File[] files = BOOKS_DIR.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                    BookData book = GSON.fromJson(reader, BookData.class);
                    if (book != null && book.isValid()) {
                        // replace any bundled book with same title
                        removeBookByTitle(book.title);
                        loadedBooks.add(book);
                        SimpleStoryBooks.LOGGER.info("Loaded book: " + book.title + " from " + file.getName());
                    }
                } catch (Exception e) {
                    SimpleStoryBooks.LOGGER.error("Failed to load book: " + file.getName(), e);
                }
            }
        }
        SimpleStoryBooks.LOGGER.info("Loaded " + loadedBooks.size() + " books.");
    }

    /**
     * Load bundled books listed in /assets/simplestorybooks/books/index.json inside the mod jar.
     * The index should be a JSON array of filenames, e.g. ["story1.json","story2.json"]
     */
    private static void loadBundledBooks() {
        try (InputStreamReader ix = new InputStreamReader(SimpleStoryBooks.class.getResourceAsStream("/assets/simplestorybooks/books/index.json"), StandardCharsets.UTF_8)) {
            JsonArray index = GSON.fromJson(ix, JsonArray.class);
            if (index == null) return;

            for (int i = 0; i < index.size(); i++) {
                String fname = index.get(i).getAsString();
                try (InputStreamReader reader = new InputStreamReader(SimpleStoryBooks.class.getResourceAsStream("/assets/simplestorybooks/books/" + fname), StandardCharsets.UTF_8)) {
                    if (reader == null) {
                        SimpleStoryBooks.LOGGER.warn("Bundled book not found in jar: " + fname);
                        continue;
                    }
                    BookData book = GSON.fromJson(reader, BookData.class);
                    if (book != null && book.isValid()) {
                        // only add if not already present (local config overrides later)
                        if (!containsBookTitle(book.title)) {
                            loadedBooks.add(book);
                            SimpleStoryBooks.LOGGER.info("Loaded bundled book: " + book.title + " from assets/" + fname);
                        }
                    }
                } catch (Exception e) {
                    SimpleStoryBooks.LOGGER.error("Failed to load bundled book: " + fname, e);
                }
            }
        } catch (Exception ignored) {
            // No index or resource not present: that's fine
        }
    }

    /**
     * If `remoteIndexUrl` is set in Setting.json, try to fetch index JSON from remote and load listed books.
     * The remote index should be an array of filenames relative to the base URL.
     */
    private static void loadRemoteBooks() {
        if (remoteIndexUrl == null || remoteIndexUrl.isEmpty()) return;
        try {
            JsonArray index = fetchJsonArrayFromUrl(remoteIndexUrl);
            if (index == null) return;
            for (int i = 0; i < index.size(); i++) {
                String fname = index.get(i).getAsString();
                try {
                    String base = remoteIndexUrl;
                    // strip file portion if present
                    int lastSlash = base.lastIndexOf('/');
                    if (lastSlash >= 0) base = base.substring(0, lastSlash + 1);
                    String fileUrl = base + fname;
                    JsonObject bookJson = fetchJsonObjectFromUrl(fileUrl);
                    if (bookJson == null) {
                        SimpleStoryBooks.LOGGER.warn("Remote book not found: " + fileUrl);
                        continue;
                    }
                    BookData book = GSON.fromJson(bookJson, BookData.class);
                    if (book != null && book.isValid()) {
                        if (!containsBookTitle(book.title)) {
                            loadedBooks.add(book);
                            SimpleStoryBooks.LOGGER.info("Loaded remote book: " + book.title + " from " + fileUrl);
                        }
                    }
                } catch (Exception e) {
                    SimpleStoryBooks.LOGGER.error("Failed to load remote book entry", e);
                }
            }
        } catch (Exception e) {
            SimpleStoryBooks.LOGGER.error("Failed to fetch remote index: " + remoteIndexUrl, e);
        }
    }

    private static JsonArray fetchJsonArrayFromUrl(String urlStr) {
        try (InputStream in = openUrlStream(urlStr);
             InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return GSON.fromJson(isr, JsonArray.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject fetchJsonObjectFromUrl(String urlStr) {
        try (InputStream in = openUrlStream(urlStr);
             InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return GSON.fromJson(isr, JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static InputStream openUrlStream(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "SimpleStoryBooks-Mod/1.0");
        return conn.getInputStream();
    }

    private static boolean containsBookTitle(String title) {
        for (BookData b : loadedBooks) {
            if (b.title != null && b.title.equals(title)) return true;
        }
        return false;
    }

    private static void removeBookByTitle(String title) {
        loadedBooks.removeIf(b -> b.title != null && b.title.equals(title));
    }

    public static BookData getRandomBook(Random random) {
        if (loadedBooks.isEmpty()) return null;
        return loadedBooks.get(random.nextInt(loadedBooks.size()));
    }

    public static void saveBookToFile(BookData book) {
        // Remove existing entry if any
        removeBookByTitle(book.title);
        loadedBooks.add(book);
        
        // Save to file
        try {
            if (!Files.exists(BOOKS_DIR)) {
                Files.createDirectories(BOOKS_DIR);
            }
            // Sanitize filename
            String filename = book.title.replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + ".json";
            File file = BOOKS_DIR.resolve(filename).toFile();
            
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(book, writer);
            }
            SimpleStoryBooks.LOGGER.info("Saved book: " + book.title);
        } catch (IOException e) {
            SimpleStoryBooks.LOGGER.error("Failed to save book: " + book.title, e);
        }
    }

    public static void deleteBookFile(String title) {
        removeBookByTitle(title);
        // Try to find the file
        File[] files = BOOKS_DIR.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                    BookData book = GSON.fromJson(reader, BookData.class);
                    if (book != null && title.equals(book.title)) {
                        reader.close();
                        file.delete();
                        SimpleStoryBooks.LOGGER.info("Deleted book file: " + file.getName());
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public static class BookData {
        public String title;
        public String author;
        public List<String> pages = new ArrayList<>();
        public double probability = 1.0;
        public List<String> lootTables = new ArrayList<>();
        public String matchMode = "WHITELIST"; // WHITELIST, BLACKLIST

        public boolean isValid() {
            return pages != null && !pages.isEmpty() && title != null && !title.isEmpty();
        }
    }
}
