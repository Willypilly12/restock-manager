package dev.restockmanager.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.restockmanager.core.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Settings file, named profile files and share codes. Every load path validates and clamps
 * what it reads, and only changes the live settings after the whole input has been accepted.
 */
public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final int VERSION = 3;

    private static final class Data {
        int version = 0;   // files from before versioning have no field, so they read as 0 and get migrated
        boolean globalEnabled = true, countOffhand = true, restockedNotice = true, notifIcon = true, useGlobalSound = false;
        int regearMinIncrease = 16, notifSeconds = 4, notifScale = 100, textScale = 100, notifAlpha = 100, notifAnim = 0;
        Double notifX, notifY;   // legacy single position (before there were two notifications); null in new files
        NotifChannel hotbarNotif = new NotifChannel(0.985, 0.03);
        NotifChannel lowNotif = new NotifChannel(0.015, 0.97);
        boolean hudEnabled = false, hudWarn = true, hudNames = true;
        int hudScale = 100, colWarn = 0xFF5A5A;
        double hudX = 0.01, hudY = 0.3;
        int colBg = 0x000000, colBorder = 0x2A2A3A, colText = 0xF2F6FA, colHotbar = 0x36A9E8, colLow = 0xF5A623, colOk = 0x3DDC84;
        String globalSound = "minecraft:block.note_block.pling";
        List<TrackedRule> hotbarRules = new ArrayList<>();
        List<TrackedRule> lowKitRules = new ArrayList<>();
    }

    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("restockmanager.json"); }
    static Path profilesDir() { return FabricLoader.getInstance().getConfigDir().resolve("restockmanager-profiles"); }

    // ---------- main settings file ----------
    public static void load(AlertEngine e) {
        Path p = path();
        if (!Files.exists(p)) { save(e); return; }
        try {
            applyJson(e, Files.readString(p, StandardCharsets.UTF_8));
            save(e);   // persists any migration
        } catch (Exception ex) {
            try { Files.move(p, p.resolveSibling("restockmanager.json.broken"), StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException ignored) { }
            save(e);
        }
    }

    public static void save(AlertEngine e) {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling("restockmanager.json.tmp");
            Files.writeString(tmp, toJson(e), StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) { /* never crash the game over settings */ }
    }

    // ---------- profiles and share codes ----------
    public static String shareCode(AlertEngine e) { return ShareCode.encode(toJson(e)); }

    public static void importShareCode(AlertEngine e, String code) throws Exception {
        applyJson(e, ShareCode.decode(code));
        save(e);
    }

    static String cleanName(String raw) {
        String s = raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9 _-]", "");
        if (s.length() > 32) s = s.substring(0, 32).trim();
        return s.isEmpty() ? "profile" : s;
    }

    public static void saveProfile(AlertEngine e, String name) throws IOException {
        Path dir = profilesDir();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(cleanName(name) + ".json"), toJson(e), StandardCharsets.UTF_8);
    }

    public static List<String> listProfiles() {
        List<String> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(profilesDir(), "*.json")) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                out.add(n.substring(0, n.length() - 5));
            }
        } catch (Exception ignored) { }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out.size() > 50 ? out.subList(0, 50) : out;
    }

    public static void loadProfile(AlertEngine e, String name) throws Exception {
        applyJson(e, Files.readString(profilesDir().resolve(cleanName(name) + ".json"), StandardCharsets.UTF_8));
        save(e);
    }

    // ---------- JSON <-> engine ----------
    static String toJson(AlertEngine e) {
        Data d = new Data();
        d.version = VERSION;
        d.globalEnabled = e.globalEnabled; d.countOffhand = e.countOffhand;
        d.restockedNotice = e.restockedNotice; d.regearMinIncrease = e.regearMinIncrease;
        d.hotbarNotif = e.hotbarNotif; d.lowNotif = e.lowNotif;
        d.hudEnabled = e.hudEnabled; d.hudWarn = e.hudWarn; d.hudNames = e.hudNames; d.hudScale = e.hudScale;
        d.hudX = e.hudX; d.hudY = e.hudY; d.colWarn = e.colWarn;
        d.notifIcon = e.notifIcon; d.notifSeconds = e.notifSeconds;
        d.notifScale = e.notifScale; d.textScale = e.textScale; d.notifAlpha = e.notifAlpha; d.notifAnim = e.notifAnim;
        d.colBg = e.colBg; d.colBorder = e.colBorder; d.colText = e.colText;
        d.colHotbar = e.colHotbar; d.colLow = e.colLow; d.colOk = e.colOk;
        d.useGlobalSound = e.useGlobalSound; d.globalSound = e.globalSound;
        d.hotbarRules = e.hotbarRules; d.lowKitRules = e.lowKitRules;
        return GSON.toJson(d);
    }

    static void applyJson(AlertEngine e, String json) throws Exception {
        Data d = GSON.fromJson(json, Data.class);
        if (d == null) throw new IllegalArgumentException("empty");

        AlertEngine fresh = new AlertEngine();
        Defaults.populate(fresh);
        List<TrackedRule> hot = sanitize(d.hotbarRules), low = sanitize(d.lowKitRules);
        if (d.version < 2) {   // pre-v2 file: current default items, carrying over the user's own choices by key
            hot = migrate(fresh.hotbarRules, hot);
            low = migrate(fresh.lowKitRules, low);
        } else {
            if (hot.isEmpty()) hot = fresh.hotbarRules;
            if (low.isEmpty()) low = fresh.lowKitRules;
        }

        // everything validated: now change the live settings
        e.globalEnabled = d.globalEnabled; e.countOffhand = d.countOffhand; e.restockedNotice = d.restockedNotice;
        e.regearMinIncrease = clamp(d.regearMinIncrease, 1, 100000);
        NotifChannel hn = d.hotbarNotif == null ? new NotifChannel(0.985, 0.03) : d.hotbarNotif;
        NotifChannel ln = d.lowNotif == null ? new NotifChannel(0.015, 0.97) : d.lowNotif;
        if (d.version < 3 && d.notifX != null && d.notifY != null
                && (Math.abs(d.notifX - 0.5) > 0.001 || Math.abs(d.notifY - 0.06) > 0.001)) {
            hn.x = d.notifX; hn.y = d.notifY;   // you had moved the old single notification: keep that spot for the hotbar one
        }
        hn.clampValues(); ln.clampValues();
        e.hotbarNotif.copyFrom(hn); e.lowNotif.copyFrom(ln);
        e.hudEnabled = d.hudEnabled; e.hudWarn = d.hudWarn; e.hudNames = d.hudNames;
        e.hudScale = clamp(d.hudScale, 50, 250);
        e.hudX = Double.isNaN(d.hudX) ? 0.01 : Math.max(0, Math.min(1, d.hudX));
        e.hudY = Double.isNaN(d.hudY) ? 0.3 : Math.max(0, Math.min(1, d.hudY));
        e.colWarn = d.colWarn & 0xFFFFFF;
        e.notifIcon = d.notifIcon; e.notifSeconds = clamp(d.notifSeconds, 1, 30);
        e.notifScale = clamp(d.notifScale, 50, 250); e.textScale = clamp(d.textScale, 50, 250);
        e.notifAlpha = clamp(d.notifAlpha, 10, 100); e.notifAnim = clamp(d.notifAnim, 0, 3);
        e.colBg = d.colBg & 0xFFFFFF; e.colBorder = d.colBorder & 0xFFFFFF; e.colText = d.colText & 0xFFFFFF;
        e.colHotbar = d.colHotbar & 0xFFFFFF; e.colLow = d.colLow & 0xFFFFFF; e.colOk = d.colOk & 0xFFFFFF;
        e.useGlobalSound = d.useGlobalSound;
        e.globalSound = (d.globalSound == null || d.globalSound.isBlank()) ? "minecraft:block.note_block.pling" : cut(d.globalSound, 100);
        e.hotbarRules.clear(); e.hotbarRules.addAll(hot);
        e.lowKitRules.clear(); e.lowKitRules.addAll(low);
        e.reset();
    }

    private static List<TrackedRule> migrate(List<TrackedRule> defaults, List<TrackedRule> old) {
        for (TrackedRule def : defaults)
            for (TrackedRule o : old)
                if (o.key.equals(def.key)) { RuleMerge.copyUserSettings(o, def); break; }
        return defaults;
    }

    private static List<TrackedRule> sanitize(List<TrackedRule> in) {
        List<TrackedRule> out = new ArrayList<>();
        if (in == null) return out;
        Set<String> seen = new HashSet<>();
        for (TrackedRule r : in) {
            if (r == null || r.key == null || r.displayName == null || r.matchers == null) continue;
            if (r.key.length() > 40 || !seen.add(r.key)) continue;
            r.matchers.removeIf(Objects::isNull);
            if (r.matchers.isEmpty()) continue;
            r.displayName = cut(r.displayName, 40);
            r.message = r.message == null ? "" : cut(r.message, 120);
            r.sound = (r.sound == null || r.sound.isBlank()) ? "minecraft:block.note_block.pling" : cut(r.sound, 100);
            r.icon = (r.icon == null || r.icon.isBlank()) ? null : cut(r.icon, 100);
            r.threshold = clamp(r.threshold, 0, 9999);
            r.volume = Math.max(0f, Math.min(1f, r.volume));
            r.cooldownMs = Math.max(0, Math.min(3_600_000L, r.cooldownMs));
            r.priority = clamp(r.priority, -100, 100);
            out.add(r);
            if (out.size() >= 40) break;
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }
}
