package io.github.miniai_saka.eez;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Config {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("japan-eez.json");
    public int territorialDistance = 12;
    public int eezDistance = 200;
    public int extendedDistance = 350;
    public boolean extendedShelf = true;
    public boolean notifications = true;
    public boolean airspace = true;
    public String defaultOwner = "日本";
    public int undergroundBottomY = -64;
    public int displayTopY = 320;
    public int airspaceTopY = 320;
    public int renderRadius = 128;
    public int surveyStep = 4;
    public int samplesPerTick = 2048;
    public int updateTicks = 40;
    public int maxMarkers = 8192;
    public int maxRegions = 256;
    public int maxVertices = 256;
    public boolean showThroughBlocks = true;
    public boolean placementRequiresOp = false;
    public Map<String, Boolean> markerEnabled = new LinkedHashMap<>();
    public Map<String, Integer> colors = new LinkedHashMap<>();
    public List<String> extraShallowBiomes = new ArrayList<>();
    public List<String> extraDeepBiomes = new ArrayList<>();
    public String territoryMessage = "{owner}の領域です。";
    public String territorialMessage = "{owner}の領海です。";
    public String eezMessage = "EEZ範囲内 ~漁業・採掘可能~";
    public String shelfMessage = "延長大陸棚 ~海底資源の採掘可能~";
    public String airspaceMessage = "{owner}の領空です。";

    public Config() {
        for (String s : List.of("triangulation_point", "benchmark", "metal_tack", "survey_nail", "boundary_stake")) markerEnabled.put(s, true);
        int[] c = {0xFFD166, 0xFFFFFF, 0x7EE787, 0x58A6FF, 0xC084FC, 0xFF9F43, 0xFF6B8A};
        for (int i = 0; i < Layer.values().length; i++) colors.put(Layer.values()[i].key, c[i]);
    }
    public void validate() {
        if (territorialDistance < 1 || territorialDistance > 64 || eezDistance < territorialDistance || eezDistance > 512
            || extendedDistance < eezDistance || extendedDistance > 768) throw new IllegalArgumentException("距離は 1≦領海≦64、領海≦EEZ≦512、EEZ≦延長≦768 にしてください。");
        if (undergroundBottomY < -2048 || displayTopY > 4096 || displayTopY <= undergroundBottomY
            || airspaceTopY <= undergroundBottomY || airspaceTopY > 4096) throw new IllegalArgumentException("高度の上下限が不正です。");
        if (renderRadius < 32 || renderRadius > 256 || (surveyStep != 1 && surveyStep != 2 && surveyStep != 4 && surveyStep != 8)) throw new IllegalArgumentException("表示半径32〜256、測定間隔1/2/4/8を指定してください。");
        if (samplesPerTick < 128 || samplesPerTick > 8192 || updateTicks < 10 || updateTicks > 1200
            || maxMarkers < 1 || maxMarkers > 100000 || maxRegions < 1 || maxRegions > 2048 || maxVertices < 3 || maxVertices > 1024) throw new IllegalArgumentException("処理量の設定が範囲外です。");
        if (defaultOwner == null || defaultOwner.isBlank() || defaultOwner.length() > 64) throw new IllegalArgumentException("領有者名は1〜64文字です。");
        if (markerEnabled == null || colors == null || extraShallowBiomes == null || extraDeepBiomes == null) throw new IllegalArgumentException("設定項目をnullにはできません。");
        Config defaults = new Config();
        defaults.markerEnabled.forEach(markerEnabled::putIfAbsent); defaults.colors.forEach(colors::putIfAbsent);
        for (String s : List.of(territoryMessage, territorialMessage, eezMessage, shelfMessage, airspaceMessage))
            if (s == null || s.length() > 256) throw new IllegalArgumentException("通知文は256文字以内です。");
    }
    public static Config load() throws Exception {
        if (!Files.exists(FILE)) { Config c = new Config(); c.save(); return c; }
        Config c;
        try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) { c = GSON.fromJson(reader, Config.class); }
        if (c == null) throw new IllegalArgumentException("設定が空です。");
        c.validate(); return c;
    }
    public void save() throws Exception {
        validate(); Files.createDirectories(FILE.getParent());
        Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
        Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
    }
}
