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
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Punto único de lectura y escritura de la configuración de SkyFog.
 */
public final class SkyFogConfig extends Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skyfog.json");

    @Expose
    @Category(name = "General", desc = "Activación y presets de SkyFog.")
    public General general = new General();

    @Expose
    @Category(name = "Niebla", desc = "Distancias de inicio y final de la niebla.")
    public Fog fog = new Fog();

    @Expose
    @Category(name = "Color", desc = "Color RGB de la niebla.")
    public Color color = new Color();

    @Expose
    @Category(name = "HUD", desc = "Preparado para futuros elementos HUD.")
    public Hud hud = new Hud();

    private static SkyFogConfig instance;
    private static ManagedConfig<SkyFogConfig> managed;

    public SkyFogConfig() {
    }

    // Formato legacy que espera MoulConfig: velocidad:alpha:r:g:b
    private static String legacyColorFromRgb(float r, float g, float b) {
        return "0:255:" + toByte(r) + ":" + toByte(g) + ":" + toByte(b);
    }

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

    private static int[] parseLegacyPreview(String preview) {
        if (preview == null) {
            return null;
        }
        String[] parts = preview.split(":", -1);
        if (parts.length != 5) {
            return null;
        }
        try {
            return new int[] {
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4])
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void applyPreset(SkyFogConfig config) {
        switch (config.general.preset) {
            case "Diana oscura", "Diana gris", "Oscura" -> {
                config.fog.start = 8.0F;
                config.fog.end = 80.0F;
                config.color.r = 0.40F;
                config.color.g = 0.40F;
                config.color.b = 0.40F;
                config.general.preset = "Diana oscura";
            }
            case "Gris claro" -> {
                config.fog.start = 8.0F;
                config.fog.end = 80.0F;
                config.color.r = 0.65F;
                config.color.g = 0.65F;
                config.color.b = 0.65F;
            }
            case "Niebla blanca", "Blanca" -> {
                config.fog.start = 8.0F;
                config.fog.end = 80.0F;
                config.color.r = 0.90F;
                config.color.g = 0.90F;
                config.color.b = 0.90F;
                config.general.preset = "Niebla blanca";
            }
            default -> {
            }
        }
    }

    private static void normalize(SkyFogConfig config) {
        if (config.general == null) config.general = new General();
        if (config.fog == null) config.fog = new Fog();
        if (config.color == null) config.color = new Color();
        if (config.hud == null) config.hud = new Hud();

        if (config.general.preset == null) {
            config.general.preset = "Diana oscura";
        }
        if ("Personalizada".equals(config.general.preset)) {
            config.general.preset = "Personalizado";
        }

        if (!Float.isFinite(config.fog.start)) config.fog.start = 8.0F;
        if (!Float.isFinite(config.fog.end)) config.fog.end = 80.0F;
        if (!Float.isFinite(config.color.r)) config.color.r = 0.62F;
        if (!Float.isFinite(config.color.g)) config.color.g = 0.62F;
        if (!Float.isFinite(config.color.b)) config.color.b = 0.62F;
        if (!Float.isFinite(config.general.darkness)) config.general.darkness = 0.0F;

        if (!"Personalizado".equals(config.general.preset)) {
            applyPreset(config);
        } else {
            int[] rgb = parseLegacyPreview(config.color.colorPreview);
            if (rgb != null) {
                config.color.r = fromByte(rgb[0]);
                config.color.g = fromByte(rgb[1]);
                config.color.b = fromByte(rgb[2]);
            }
        }

        config.fog.start = clampFog(config.fog.start);
        config.fog.end = clampFog(config.fog.end);
        if (config.fog.end <= config.fog.start) {
            if (config.fog.start >= 300.0F) {
                config.fog.start = 299.0F;
                config.fog.end = 300.0F;
            } else {
                config.fog.end = config.fog.start + 1.0F;
            }
        }

        config.color.r = clamp01(config.color.r);
        config.color.g = clamp01(config.color.g);
        config.color.b = clamp01(config.color.b);
        config.general.darkness = clamp01(config.general.darkness);
        config.color.colorPreview = legacyColorFromRgb(config.color.r, config.color.g, config.color.b);
    }

    private static void normalizeColorPreview(SkyFogConfig config) {
        String preview = config.color.colorPreview;
        if (preview == null || preview.split(":", -1).length != 5) {
            config.color.colorPreview = "0:255:158:158:158";
        }
    }

    public static SkyFogConfig get() {
        if (instance == null) {
            instance = loadLegacyOrDefaults();
        }
        return instance;
    }

    public static void initializeManaged() {
        if (managed == null) {
            migrateLegacyFile();
            managed = ManagedConfig.create(FILE.toFile(), SkyFogConfig.class);
            instance = managed.getInstance();
            normalize(instance);
            instance.validate();
            save();
        }
    }

    public static void openEditor() {
        initializeManaged();
        managed.openConfigGui();
    }

    public static void save() {
        SkyFogConfig config = get();
        normalize(config);
        config.validate();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo guardar " + FILE, exception);
        }
    }

    @Override
    public void saveNow() {
        save();
    }

    public boolean enabled() {
        return general.enabled;
    }

    public void setEnabled(boolean value) {
        general.enabled = value;
    }

    public float start() {
        if (isPreset()) {
            return 8.0F;
        }
        return fog.start;
    }

    public void setStart(float value) {
        general.preset = "Personalizado";
        fog.start = value;
    }

    public float end() {
        if (isPreset()) {
            return 80.0F;
        }
        return fog.end;
    }

    public void setEnd(float value) {
        general.preset = "Personalizado";
        fog.end = value;
    }

    public float red() {
        return color.r;
    }

    public float green() {
        return color.g;
    }

    public float blue() {
        return color.b;
    }

    private boolean isPreset() {
        return "Diana oscura".equals(general.preset)
            || "Gris claro".equals(general.preset)
            || "Niebla blanca".equals(general.preset);
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
        general.preset = "Personalizado";
        color.r = r;
        color.g = g;
        color.b = b;
        color.colorPreview = legacyColorFromRgb(r, g, b);
    }

    private static SkyFogConfig loadLegacyOrDefaults() {
        if (!Files.exists(FILE)) {
            SkyFogConfig config = new SkyFogConfig();
            instance = config;
            save();
            return config;
        }
        try {
            SkyFogConfig config = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), SkyFogConfig.class);
            if (config == null) {
                throw new JsonSyntaxException("El archivo está vacío");
            }
            normalizeColorPreview(config);
            normalize(config);
            config.validate();
            return config;
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo leer " + FILE, exception);
        }
    }

    private static void migrateLegacyFile() {
        if (!Files.exists(FILE)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("enabled") && root.has("general")) {
                return;
            }
            SkyFogConfig migrated = new SkyFogConfig();
            if (root.has("enabled")) migrated.general.enabled = root.get("enabled").getAsBoolean();
            if (root.has("start")) migrated.fog.start = root.get("start").getAsFloat();
            if (root.has("end")) migrated.fog.end = root.get("end").getAsFloat();
            if (root.has("r")) migrated.color.r = root.get("r").getAsFloat();
            if (root.has("g")) migrated.color.g = root.get("g").getAsFloat();
            if (root.has("b")) migrated.color.b = root.get("b").getAsFloat();
            migrated.general.preset = "Personalizado";
            normalizeColorPreview(migrated);
            Files.writeString(FILE, GSON.toJson(migrated), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("No se pudo migrar " + FILE, exception);
        }
    }

    private void validate() {
        if (!Float.isFinite(start()) || !Float.isFinite(end()) || !Float.isFinite(red()) || !Float.isFinite(green()) || !Float.isFinite(blue())) {
            throw new JsonSyntaxException("Los valores de skyfog.json deben ser finitos");
        }
        if (start() < 0.0F || end() <= start() || end() > 300.0F) {
            throw new JsonSyntaxException("La niebla debe tener 0 <= start < end <= 300");
        }
        if (red() < 0.0F || red() > 1.0F || green() < 0.0F || green() > 1.0F || blue() < 0.0F || blue() > 1.0F) {
            throw new JsonSyntaxException("Los componentes r, g y b deben estar entre 0 y 1");
        }
    }

    @Override
    public StructuredText getTitle() {
        return StructuredText.of("SkyFog");
    }

    public static final class General {
        @Expose
        @ConfigOption(name = "Niebla activada", desc = "Activa o desactiva la niebla personalizada.")
        @ConfigEditorBoolean(runnableId = 0)
        public boolean enabled = true;

        @Expose
        @ConfigOption(name = "Preset", desc = "Preset visual para la niebla.")
        @ConfigEditorDropdown(values = {"Diana oscura", "Gris claro", "Niebla blanca", "Personalizado"})
        public String preset = "Diana oscura";

        @Expose
        @ConfigOption(name = "El cielo coincide con la niebla", desc = "Usa exactamente el color de la niebla para el cielo.")
        @ConfigEditorBoolean(runnableId = 0)
        public boolean skyMatchesFog = true;

        @Expose
        @ConfigOption(name = "Oscurecimiento", desc = "Multiplica el color base por (1 - este valor).")
        @ConfigEditorSlider(minValue = 0.0F, maxValue = 1.0F, minStep = 0.01F)
        public float darkness = 0.0F;

        @Expose
        @ConfigOption(name = "Ocultar sol", desc = "Oculta el sol del cielo.")
        @ConfigEditorBoolean(runnableId = 0)
        public boolean hideSun = true;

        @Expose
        @ConfigOption(name = "Ocultar luna", desc = "Oculta la luna del cielo.")
        @ConfigEditorBoolean(runnableId = 0)
        public boolean hideMoon = true;

        @Expose
        @ConfigOption(name = "Ocultar estrellas", desc = "Oculta las estrellas del cielo.")
        @ConfigEditorBoolean(runnableId = 0)
        public boolean hideStars = true;

        @Expose
        @ConfigOption(name = "Ocultar nubes", desc = "Oculta las nubes.")
        @ConfigEditorBoolean(runnableId = 0)
        public boolean hideClouds = true;

        @Expose
        @ConfigOption(name = "Aplicar en otras dimensiones", desc = "Aplica SkyFog también en Nether y End.")
        @ConfigEditorBoolean(runnableId = 0)
        public boolean applyInOtherDimensions = false;
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
        public String colorPreview = "0:255:158:158:158";

        @Expose
        @ConfigOption(name = "Rojo", desc = "Componente roja del color de niebla.")
        @ConfigEditorSlider(minValue = 0.0F, maxValue = 1.0F, minStep = 0.01F)
        public float r = 0.62F;

        @Expose
        @ConfigOption(name = "Verde", desc = "Componente verde del color de niebla.")
        @ConfigEditorSlider(minValue = 0.0F, maxValue = 1.0F, minStep = 0.01F)
        public float g = 0.62F;

        @Expose
        @ConfigOption(name = "Azul", desc = "Componente azul del color de niebla.")
        @ConfigEditorSlider(minValue = 0.0F, maxValue = 1.0F, minStep = 0.01F)
        public float b = 0.62F;
    }

    public static final class Hud {
        @Expose
        @ConfigOption(name = "Editor HUD", desc = "Reservado para posiciones HUD arrastrables.")
        @ConfigEditorBoolean(runnableId = 0)
        public boolean enabled = false;
    }
}
