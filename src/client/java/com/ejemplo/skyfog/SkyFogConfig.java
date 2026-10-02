package com.ejemplo.skyfog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Client configuration for SkyFog. ManagedConfig is the only normal writer.
 */
public final class SkyFogConfig extends Config {
    private static final Gson MIGRATION_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skyfog.json");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static SkyFogConfig instance;
    private static ManagedConfig<SkyFogConfig> managed;
    private static String cachedPreview;
    private static float[] cachedRgb;

    @Expose
    @Category(name = "General", desc = "SkyFog activation and presets.")
    public General general = new General();

    @Expose
    @Category(name = "Fog", desc = "Fog start and end distances.")
    public Fog fog = new Fog();

    @Expose
    @Category(name = "Color", desc = "Fog RGB color.")
    public Color color = new Color();

    private static int toByte(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }

    private static float fromByte(int value) {
        return Math.max(0, Math.min(255, value)) / 255.0F;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float clampFog(float value) {
        return Math.max(0.0F, Math.min(300.0F, value));
    }

    private static String previewFromRgb(float r, float g, float b) {
        return "0:255:" + toByte(r) + ":" + toByte(g) + ":" + toByte(b);
    }

    private static int[] parsePreview(String preview) {
        if (preview == null) return null;
        String[] parts = preview.split(":", -1);
        if (parts.length != 5) return null;
        try {
            int r = Integer.parseInt(parts[2]);
            int g = Integer.parseInt(parts[3]);
            int b = Integer.parseInt(parts[4]);
            if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) return null;
            return new int[] {r, g, b};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean nearlyEqual(float first, float second) {
        return Math.abs(first - second) <= 0.005F;
    }

    private static float[] presetValues(Preset preset) {
        return switch (preset) {
            case DIANA_DARK -> new float[] {8.0F, 80.0F, 0.40F};
            case LIGHT_GRAY -> new float[] {8.0F, 80.0F, 0.65F};
            case WHITE -> new float[] {8.0F, 80.0F, 0.90F};
            case CUSTOM -> null;
        };
    }

    private static boolean isNamedPreset(Preset preset) {
        return preset != Preset.CUSTOM;
    }

    private static Preset parsePreset(Object value) {
        if (value instanceof Preset preset) return preset;
        if (!(value instanceof String text)) return Preset.DIANA_DARK;
        return switch (text) {
            case "DIANA_DARK", "Diana oscura", "Diana gris", "Oscura" -> Preset.DIANA_DARK;
            case "LIGHT_GRAY", "Gris claro" -> Preset.LIGHT_GRAY;
            case "WHITE", "Niebla blanca", "Blanca" -> Preset.WHITE;
            case "CUSTOM", "Personalizado", "Personalizada" -> Preset.CUSTOM;
            default -> Preset.DIANA_DARK;
        };
    }

    private static Language parseLanguage(Object value) {
        if (value instanceof Language language) return language;
        if (value instanceof String text && "es".equalsIgnoreCase(text)) return Language.SPANISH;
        return Language.ENGLISH;
    }

    private void setPreviewFromRgb(float r, float g, float b) {
        r = clamp01(Float.isFinite(r) ? r : 0.40F);
        g = clamp01(Float.isFinite(g) ? g : 0.40F);
        b = clamp01(Float.isFinite(b) ? b : 0.40F);
        color.colorPreview = previewFromRgb(r, g, b);
        color.r = r;
        color.g = g;
        color.b = b;
        cachedPreview = null;
    }

    private void applyPreset() {
        switch (general.preset) {
            case DIANA_DARK -> {
                fog.start = 8.0F;
                fog.end = 80.0F;
                setPreviewFromRgb(0.40F, 0.40F, 0.40F);
            }
            case LIGHT_GRAY -> {
                fog.start = 8.0F;
                fog.end = 80.0F;
                setPreviewFromRgb(0.65F, 0.65F, 0.65F);
            }
            case WHITE -> {
                fog.start = 8.0F;
                fog.end = 80.0F;
                setPreviewFromRgb(0.90F, 0.90F, 0.90F);
            }
            default -> {
            }
        }
    }

    private void sanitizeFog() {
        if (!Float.isFinite(fog.start)) fog.start = 8.0F;
        if (!Float.isFinite(fog.end)) fog.end = 80.0F;
        fog.start = clampFog(fog.start);
        fog.end = clampFog(fog.end);
        if (fog.end <= fog.start) {
            if (fog.start >= 300.0F) fog.start = 299.0F;
            fog.end = Math.min(300.0F, fog.start + 1.0F);
        }
    }

    private void updateLegacyRgbFields() {
        float[] rgb = parsedRgb();
        color.r = rgb[0];
        color.g = rgb[1];
        color.b = rgb[2];
    }

    private float[] parsedRgb() {
        if (!color.colorPreview.equals(cachedPreview)) {
            int[] rgb = parsePreview(color.colorPreview);
            if (rgb == null) {
                setPreviewFromRgb(color.r, color.g, color.b);
                rgb = parsePreview(color.colorPreview);
            }
            cachedPreview = color.colorPreview;
            cachedRgb = new float[] {fromByte(rgb[0]), fromByte(rgb[1]), fromByte(rgb[2])};
        }
        return cachedRgb;
    }

    private void markPresetAsCustomIfChanged() {
        float[] expected = presetValues(general.preset);
        int[] rgb = parsePreview(color.colorPreview);
        if (expected != null && (rgb == null
            || !nearlyEqual(fog.start, expected[0])
            || !nearlyEqual(fog.end, expected[1])
            || !nearlyEqual(fromByte(rgb[0]), expected[2])
            || !nearlyEqual(fromByte(rgb[1]), expected[2])
            || !nearlyEqual(fromByte(rgb[2]), expected[2]))) {
            general.preset = Preset.CUSTOM;
            general.lastAppliedPreset = Preset.CUSTOM;
        }
    }

    private void ensureLiveState() {
        if (general == null) general = new General();
        if (fog == null) fog = new Fog();
        if (color == null) color = new Color();
        general.preset = parsePreset(general.preset);
        general.lastAppliedPreset = parsePreset(general.lastAppliedPreset);
        if (!isValidPreview(color.colorPreview)) setPreviewFromRgb(color.r, color.g, color.b);
        if (general.preset != general.lastAppliedPreset) {
            if (isNamedPreset(general.preset)) applyPreset();
            general.lastAppliedPreset = general.preset;
        } else {
            markPresetAsCustomIfChanged();
        }
        sanitizeFog();
        general.darkness = clamp01(Float.isFinite(general.darkness) ? general.darkness : 0.0F);
    }

    private static boolean isValidPreview(String preview) {
        return parsePreview(preview) != null;
    }

    private void normalize() {
        ensureLiveState();
        if (general.preset == Preset.CUSTOM) updateLegacyRgbFields();
    }

    public static SkyFogConfig get() {
        if (instance == null) initializeManaged();
        return instance;
    }

    public static void initializeManaged() {
        if (managed == null) {
            migrateLegacyFile();
            managed = ManagedConfig.create(FILE.toFile(), SkyFogConfig.class);
            instance = managed.getInstance();
            instance.normalize();
            instance.validate();
            instance.saveNow();
        }
    }

    public static void openEditor() {
        initializeManaged();
        managed.openConfigGui();
    }

    public static void save() {
        get().saveNow();
    }

    /**
     * Hash of persistent fields used to detect GUI changes.
     */
    public int persistentHash() {
        ensureLiveState();
        return Objects.hash(
            general.enabled,
            general.preset,
            general.lastAppliedPreset,
            general.skyMatchesFog,
            general.darkness,
            general.hideSun,
            general.hideMoon,
            general.hideStars,
            general.hideClouds,
            general.applyInOtherDimensions,
            general.language,
            fog.start,
            fog.end,
            color.colorPreview
        );
    }

    @Override
    public void saveNow() {
        normalize();
        validate();
        // ManagedConfig registers saveToFile() in saveRunnables.
        super.saveNow();
    }

    public boolean enabled() {
        return general.enabled;
    }

    public void setEnabled(boolean value) {
        general.enabled = value;
    }

    public float start() {
        ensureLiveState();
        return fog.start;
    }

    public void setStart(float value) {
        ensureLiveState();
        fog.start = clampFog(value);
        if (fog.end <= fog.start) fog.end = Math.min(300.0F, fog.start + 1.0F);
        markPresetAsCustomIfChanged();
    }

    public float end() {
        ensureLiveState();
        return fog.end;
    }

    public void setEnd(float value) {
        ensureLiveState();
        fog.end = clampFog(value);
        if (fog.end <= fog.start) fog.start = Math.max(0.0F, fog.end - 1.0F);
        markPresetAsCustomIfChanged();
    }

    public float red() {
        ensureLiveState();
        return parsedRgb()[0];
    }

    public float green() {
        ensureLiveState();
        return parsedRgb()[1];
    }

    public float blue() {
        ensureLiveState();
        return parsedRgb()[2];
    }

    public float effectiveRed() {
        return red() * (1.0F - general.darkness);
    }

    public float effectiveGreen() {
        return green() * (1.0F - general.darkness);
    }

    public float effectiveBlue() {
        return blue() * (1.0F - general.darkness);
    }

    public boolean appliesTo(net.minecraft.client.multiplayer.ClientLevel level) {
        return level != null && (general.applyInOtherDimensions
            || level.dimension() == net.minecraft.world.level.Level.OVERWORLD);
    }

    public boolean appliesToCurrentDimension() {
        return appliesTo(net.minecraft.client.Minecraft.getInstance().level);
    }

    public int effectiveColorArgb() {
        return (0xFF << 24)
            | (toByte(effectiveRed()) << 16)
            | (toByte(effectiveGreen()) << 8)
            | toByte(effectiveBlue());
    }

    public void setColor(float r, float g, float b) {
        ensureLiveState();
        general.preset = Preset.CUSTOM;
        general.lastAppliedPreset = Preset.CUSTOM;
        setPreviewFromRgb(r, g, b);
    }

    private static void migrateLegacyFile() {
        if (!Files.exists(FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("general")) {
                if (!root.get("general").isJsonObject()) throw new JsonSyntaxException("general must be an object");
                JsonObject general = root.getAsJsonObject("general");
                boolean changed = false;
                requireBooleanIfPresent(general, "enabled");
                requireBooleanIfPresent(general, "skyMatchesFog");
                requireBooleanIfPresent(general, "hideSun");
                requireBooleanIfPresent(general, "hideMoon");
                requireBooleanIfPresent(general, "hideStars");
                requireBooleanIfPresent(general, "hideClouds");
                requireBooleanIfPresent(general, "applyInOtherDimensions");
                requireNumberIfPresent(general, "darkness");
                if (general.has("preset")) {
                    if (!general.get("preset").isJsonPrimitive() || !general.getAsJsonPrimitive("preset").isString()) {
                        throw new JsonSyntaxException("preset must be a string");
                    }
                    String old = general.get("preset").getAsString();
                    String current = parsePreset(old).name();
                    if (!current.equals(old)) {
                        general.addProperty("preset", current);
                        changed = true;
                    }
                }
                if (general.has("lastAppliedPreset")) {
                    if (!general.get("lastAppliedPreset").isJsonPrimitive() || !general.getAsJsonPrimitive("lastAppliedPreset").isString()) {
                        throw new JsonSyntaxException("lastAppliedPreset must be a string");
                    }
                    if (general.has("language")) {
                        if (!general.get("language").isJsonPrimitive()) throw new JsonSyntaxException("language must be a string");
                        String old = general.get("language").getAsString();
                        String current = parseLanguage(old).name();
                        if (!current.equals(old)) {
                            general.addProperty("language", current);
                            changed = true;
                        }
                    }
                    String old = general.get("lastAppliedPreset").getAsString();
                    String current = parsePreset(old).name();
                    if (!current.equals(old)) {
                        general.addProperty("lastAppliedPreset", current);
                        changed = true;
                    }
                }
                validateNestedObject(root, "fog", "start", "end");
                validateNestedObject(root, "color", "colorPreview", "r", "g", "b");
                if (changed) writeAtomic(MIGRATION_GSON.toJson(root));
                return;
            }

            SkyFogConfig migrated = new SkyFogConfig();
            if (root.has("enabled")) migrated.general.enabled = root.get("enabled").getAsBoolean();
            if (root.has("start")) migrated.fog.start = root.get("start").getAsFloat();
            if (root.has("end")) migrated.fog.end = root.get("end").getAsFloat();
            if (root.has("r")) migrated.color.r = root.get("r").getAsFloat();
            if (root.has("g")) migrated.color.g = root.get("g").getAsFloat();
            if (root.has("b")) migrated.color.b = root.get("b").getAsFloat();
            migrated.general.preset = Preset.CUSTOM;
            migrated.general.lastAppliedPreset = Preset.CUSTOM;
            migrated.color.colorPreview = previewFromRgb(migrated.color.r, migrated.color.g, migrated.color.b);
            writeAtomic(MIGRATION_GSON.toJson(migrated));
        } catch (IOException | RuntimeException exception) {
            quarantineCorruptConfig(exception);
        }
    }

    private static void requireBooleanIfPresent(JsonObject object, String name) {
        if (object.has(name) && (!object.get(name).isJsonPrimitive() || !object.getAsJsonPrimitive(name).isBoolean())) {
            throw new JsonSyntaxException(name + " must be boolean");
        }
    }

    private static void requireNumberIfPresent(JsonObject object, String name) {
        if (object.has(name) && (!object.get(name).isJsonPrimitive() || !object.getAsJsonPrimitive(name).isNumber())) {
            throw new JsonSyntaxException(name + " must be numeric");
        }
    }

    private static void validateNestedObject(JsonObject root, String objectName, String... numericOrStringNames) {
        if (!root.has(objectName)) return;
        if (!root.get(objectName).isJsonObject()) throw new JsonSyntaxException(objectName + " must be an object");
        JsonObject object = root.getAsJsonObject(objectName);
        for (String name : numericOrStringNames) {
            if (!object.has(name)) continue;
            if (!object.get(name).isJsonPrimitive()) throw new JsonSyntaxException(name + " must be primitive");
            if ("colorPreview".equals(name) && !object.getAsJsonPrimitive(name).isString()) {
                throw new JsonSyntaxException(name + " must be a string");
            }
            if (!"colorPreview".equals(name) && !object.getAsJsonPrimitive(name).isNumber()) {
                throw new JsonSyntaxException(name + " must be numeric");
            }
        }
    }

    private static void writeAtomic(String content) throws IOException {
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void quarantineCorruptConfig(Exception exception) {
        try {
            if (Files.exists(FILE)) {
                String suffix = String.valueOf(System.currentTimeMillis());
                Path backup = FILE.resolveSibling("skyfog.json." + suffix + ".bak");
                int counter = 1;
                while (Files.exists(backup)) {
                    backup = FILE.resolveSibling("skyfog.json." + suffix + "." + counter++ + ".bak");
                }
                Files.move(FILE, backup);
            }
        } catch (IOException backupException) {
            LOGGER.warn("Could not quarantine corrupt SkyFog config", backupException);
        }
        LOGGER.warn("SkyFog config was invalid; defaults will be used", exception);
    }

    private static void resetDefaults() {
        SkyFogConfig config = get();
        config.general.preset = Preset.DIANA_DARK;
        config.general.lastAppliedPreset = Preset.CUSTOM;
        config.general.enabled = true;
        config.general.language = Language.ENGLISH;
        config.general.darkness = 0.0F;
        config.general.skyMatchesFog = true;
        config.general.hideSun = true;
        config.general.hideMoon = true;
        config.general.hideStars = true;
        config.general.hideClouds = true;
        config.general.applyInOtherDimensions = false;
        config.normalize();
        config.saveNow();
    }

    private void validate() {
        try {
            normalize();
        } catch (RuntimeException exception) {
            LOGGER.warn("SkyFog config values were invalid; defaults will be used", exception);
            general = new General();
            fog = new Fog();
            color = new Color();
            normalize();
        }
    }

    @Override
    public StructuredText getTitle() {
        return StructuredText.of("SkyFog");
    }

    public static final class General {
        @Expose
        @ConfigOption(name = "Fog enabled", desc = "Enable custom fog.")
        @ConfigEditorBoolean
        public boolean enabled = true;

        @Expose
        @ConfigOption(name = "Preset", desc = "Apply a fog preset.")
        @ConfigEditorDropdown
        public Preset preset = Preset.DIANA_DARK;

        @Expose
        public Preset lastAppliedPreset = Preset.CUSTOM;

        @Expose
        @ConfigOption(name = "Language", desc = "Language used by SkyFog chat messages.")
        @ConfigEditorDropdown(values = {"en", "es"})
        public Language language = Language.ENGLISH;

        @Expose
        @ConfigOption(name = "Sky matches fog", desc = "Use exactly the fog color for the sky.")
        @ConfigEditorBoolean
        public boolean skyMatchesFog = true;

        @Expose
        @ConfigOption(name = "Darkness", desc = "Multiply the base color by (1 - this value).")
        @ConfigEditorSlider(minValue = 0.0F, maxValue = 1.0F, minStep = 0.01F)
        public float darkness = 0.0F;

        @Expose
        @ConfigOption(name = "Hide sun", desc = "Hide the sun.")
        @ConfigEditorBoolean
        public boolean hideSun = true;

        @Expose
        @ConfigOption(name = "Hide moon", desc = "Hide the moon.")
        @ConfigEditorBoolean
        public boolean hideMoon = true;

        @Expose
        @ConfigOption(name = "Hide stars", desc = "Hide the stars.")
        @ConfigEditorBoolean
        public boolean hideStars = true;

        @Expose
        @ConfigOption(name = "Hide clouds", desc = "Hide the clouds.")
        @ConfigEditorBoolean
        public boolean hideClouds = true;

        @Expose
        @ConfigOption(name = "Other dimensions", desc = "Also apply SkyFog in the Nether and End.")
        @ConfigEditorBoolean
        public boolean applyInOtherDimensions = false;

        @ConfigOption(name = "Reset defaults", desc = "Restore the initial SkyFog configuration.")
        @ConfigEditorButton(buttonText = "Reset")
        public transient Runnable resetDefaults = SkyFogConfig::resetDefaults;
    }

    public enum Preset {
        DIANA_DARK,
        LIGHT_GRAY,
        WHITE,
        CUSTOM;

        @Override
        public String toString() {
            return switch (this) {
                case DIANA_DARK -> "Diana dark";
                case LIGHT_GRAY -> "Light gray";
                case WHITE -> "White";
                case CUSTOM -> "Custom";
            };
        }
    }

    public enum Language {
        ENGLISH,
        SPANISH;

        @Override
        public String toString() {
            return this == ENGLISH ? "English" : "Español";
        }
    }

    public static final class Fog {
        @Expose
        @ConfigOption(name = "Start", desc = "Distance in blocks where fog starts.")
        @ConfigEditorSlider(minValue = 0.0F, maxValue = 300.0F, minStep = 1.0F)
        public float start = 8.0F;

        @Expose
        @ConfigOption(name = "End", desc = "Distance in blocks where fog ends.")
        @ConfigEditorSlider(minValue = 0.0F, maxValue = 300.0F, minStep = 1.0F)
        public float end = 80.0F;
    }

    public static final class Color {
        @Expose
        @ConfigOption(name = "Color", desc = "Visual fog color selector.")
        @ConfigEditorColour
        public String colorPreview = "0:255:102:102:102";

        @Expose
        public float r = 0.40F;

        @Expose
        public float g = 0.40F;

        @Expose
        public float b = 0.40F;
    }
}
