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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuración cliente de SkyFog. ManagedConfig es el único escritor normal.
 */
public final class SkyFogConfig extends Config {
    private static final Gson MIGRATION_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skyfog.json");
    private static SkyFogConfig instance;
    private static ManagedConfig<SkyFogConfig> managed;
    private static String cachedPreview;
    private static float[] cachedRgb;

    @Expose
    @Category(name = "General", desc = "Activación y presets de SkyFog.")
    public General general = new General();

    @Expose
    @Category(name = "Niebla", desc = "Distancias de inicio y final de la niebla.")
    public Fog fog = new Fog();

    @Expose
    @Category(name = "Color", desc = "Color RGB de la niebla.")
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

    private static float[] presetValues(String preset) {
        return switch (preset) {
            case "Diana oscura" -> new float[] {8.0F, 80.0F, 0.40F};
            case "Gris claro" -> new float[] {8.0F, 80.0F, 0.65F};
            case "Niebla blanca" -> new float[] {8.0F, 80.0F, 0.90F};
            default -> null;
        };
    }

    private static boolean isNamedPreset(String preset) {
        return presetValues(preset) != null;
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
            case "Diana oscura", "Diana gris", "Oscura" -> {
                general.preset = "Diana oscura";
                fog.start = 8.0F;
                fog.end = 80.0F;
                setPreviewFromRgb(0.40F, 0.40F, 0.40F);
            }
            case "Gris claro" -> {
                fog.start = 8.0F;
                fog.end = 80.0F;
                setPreviewFromRgb(0.65F, 0.65F, 0.65F);
            }
            case "Niebla blanca", "Blanca" -> {
                general.preset = "Niebla blanca";
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
            general.preset = "Personalizado";
            general.lastAppliedPreset = "Personalizado";
        }
    }

    private void ensureLiveState() {
        if (general == null) general = new General();
        if (fog == null) fog = new Fog();
        if (color == null) color = new Color();
        if (general.preset == null) general.preset = "Diana oscura";
        if (general.lastAppliedPreset == null) general.lastAppliedPreset = "";
        if ("Diana gris".equals(general.preset) || "Oscura".equals(general.preset)) {
            general.preset = "Diana oscura";
        } else if ("Blanca".equals(general.preset)) {
            general.preset = "Niebla blanca";
        } else if ("Personalizada".equals(general.preset)) {
            general.preset = "Personalizado";
        }
        if (!isValidPreview(color.colorPreview)) setPreviewFromRgb(color.r, color.g, color.b);
        if (!general.preset.equals(general.lastAppliedPreset)) {
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
        if ("Personalizado".equals(general.preset)) updateLegacyRgbFields();
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
     * Huella de los campos persistentes para detectar cambios hechos por la GUI.
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
            fog.start,
            fog.end,
            color.colorPreview
        );
    }

    @Override
    public void saveNow() {
        normalize();
        validate();
        // ManagedConfig ya registró saveToFile() en saveRunnables.
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
        general.preset = "Personalizado";
        general.lastAppliedPreset = "Personalizado";
        setPreviewFromRgb(r, g, b);
    }

    private static void migrateLegacyFile() {
        if (!Files.exists(FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("general")) return;
            SkyFogConfig migrated = new SkyFogConfig();
            if (root.has("enabled")) migrated.general.enabled = root.get("enabled").getAsBoolean();
            if (root.has("start")) migrated.fog.start = root.get("start").getAsFloat();
            if (root.has("end")) migrated.fog.end = root.get("end").getAsFloat();
            if (root.has("r")) migrated.color.r = root.get("r").getAsFloat();
            if (root.has("g")) migrated.color.g = root.get("g").getAsFloat();
            if (root.has("b")) migrated.color.b = root.get("b").getAsFloat();
            migrated.general.preset = "Personalizado";
            migrated.color.colorPreview = previewFromRgb(migrated.color.r, migrated.color.g, migrated.color.b);
            Files.writeString(FILE, MIGRATION_GSON.toJson(migrated), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("No se pudo migrar " + FILE, exception);
        }
    }

    private static void resetDefaults() {
        SkyFogConfig config = get();
        config.general.preset = "Diana oscura";
        config.general.lastAppliedPreset = "";
        config.general.enabled = true;
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
        if (!Float.isFinite(start()) || !Float.isFinite(end())
            || !Float.isFinite(red()) || !Float.isFinite(green()) || !Float.isFinite(blue())) {
            throw new JsonSyntaxException("Los valores de skyfog.json deben ser finitos");
        }
        if (start() < 0.0F || end() <= start() || end() > 300.0F) {
            throw new JsonSyntaxException("La niebla debe tener 0 <= start < end <= 300");
        }
    }

    @Override
    public StructuredText getTitle() {
        return StructuredText.of("SkyFog");
    }

    public static final class General {
        @Expose
        @ConfigOption(name = "Niebla activada", desc = "Activa o desactiva la niebla personalizada.")
        @ConfigEditorBoolean
        public boolean enabled = true;

        @Expose
        @ConfigOption(name = "Preset", desc = "Acción visual para la niebla.")
        @ConfigEditorDropdown(values = {"Diana oscura", "Gris claro", "Niebla blanca", "Personalizado"})
        public String preset = "Diana oscura";

        @Expose
        public String lastAppliedPreset = "";

        @Expose
        @ConfigOption(name = "El cielo coincide con la niebla", desc = "Usa exactamente el color de la niebla para el cielo.")
        @ConfigEditorBoolean
        public boolean skyMatchesFog = true;

        @Expose
        @ConfigOption(name = "Oscurecimiento", desc = "Multiplica el color base por (1 - este valor).")
        @ConfigEditorSlider(minValue = 0.0F, maxValue = 1.0F, minStep = 0.01F)
        public float darkness = 0.0F;

        @Expose
        @ConfigOption(name = "Ocultar sol", desc = "Oculta el sol del cielo.")
        @ConfigEditorBoolean
        public boolean hideSun = true;

        @Expose
        @ConfigOption(name = "Ocultar luna", desc = "Oculta la luna del cielo.")
        @ConfigEditorBoolean
        public boolean hideMoon = true;

        @Expose
        @ConfigOption(name = "Ocultar estrellas", desc = "Oculta las estrellas del cielo.")
        @ConfigEditorBoolean
        public boolean hideStars = true;

        @Expose
        @ConfigOption(name = "Ocultar nubes", desc = "Oculta las nubes.")
        @ConfigEditorBoolean
        public boolean hideClouds = true;

        @Expose
        @ConfigOption(name = "Aplicar en otras dimensiones", desc = "Aplica SkyFog también en Nether y End.")
        @ConfigEditorBoolean
        public boolean applyInOtherDimensions = false;

        @ConfigOption(name = "Valores por defecto", desc = "Restaura la configuración inicial de SkyFog.")
        @ConfigEditorButton(buttonText = "Restablecer")
        public transient Runnable resetDefaults = SkyFogConfig::resetDefaults;
    }

    public static final class Fog {
        @Expose
        @ConfigOption(name = "Inicio", desc = "Distancia en bloques donde empieza la niebla.")
        @ConfigEditorSlider(minValue = 0.0F, maxValue = 300.0F, minStep = 1.0F)
        public float start = 8.0F;

        @Expose
        @ConfigOption(name = "Fin", desc = "Distancia en bloques donde termina la niebla.")
        @ConfigEditorSlider(minValue = 0.0F, maxValue = 300.0F, minStep = 1.0F)
        public float end = 80.0F;
    }

    public static final class Color {
        @Expose
        @ConfigOption(name = "Color", desc = "Selector visual del color de niebla.")
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
