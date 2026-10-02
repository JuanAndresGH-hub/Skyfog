package com.ejemplo.skyfog;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Registers the client-only configuration and commands.
 */
public final class SkyFogClient implements ClientModInitializer {
    private static KeyMapping openScreenKey;
    private static int lastSavedConfigHash;
    private static int pendingConfigSaveTicks;

    @Override
    public void onInitializeClient() {
        SkyFogConfig.initializeManaged();
        lastSavedConfigHash = SkyFogConfig.get().persistentHash();
        openScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.skyfog.open_config",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("skyfog", "general"))
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.consumeClick()) {
                SkyFogConfig.openEditor();
            }
            SkyFogConfig config = SkyFogConfig.get();
            int currentHash = config.persistentHash();
            if (currentHash != lastSavedConfigHash) {
                lastSavedConfigHash = currentHash;
                pendingConfigSaveTicks = 5;
            } else if (pendingConfigSaveTicks > 0 && --pendingConfigSaveTicks == 0) {
                config.saveNow();
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(command("skyfog"));
            dispatcher.register(command("sf"));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String literal) {
        return ClientCommands.literal(literal)
            .executes(context -> {
                context.getSource().getClient().execute(SkyFogConfig::openEditor);
                return 1;
            })
            .then(toggleCommand())
            .then(startCommand())
            .then(endCommand())
            .then(colorCommand());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> toggleCommand() {
        return ClientCommands.literal("toggle").executes(context -> {
            SkyFogConfig config = SkyFogConfig.get();
            config.setEnabled(!config.enabled());
            SkyFogConfig.save();
            context.getSource().sendFeedback(message(config, config.enabled() ? "enabled" : "disabled"));
            return 1;
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> startCommand() {
        return ClientCommands.literal("start")
            .then(ClientCommands.argument("valor", FloatArgumentType.floatArg(0.0F, 300.0F))
                .executes(context -> {
                    SkyFogConfig config = SkyFogConfig.get();
                    float value = FloatArgumentType.getFloat(context, "valor");
                    if (value >= config.end()) {
                        context.getSource().sendError(message(config, "start_invalid"));
                        return 0;
                    }
                    config.setStart(value);
                    SkyFogConfig.save();
                    context.getSource().sendFeedback(message(config, "updated"));
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> endCommand() {
        return ClientCommands.literal("end")
            .then(ClientCommands.argument("valor", FloatArgumentType.floatArg(0.0F, 300.0F))
                .executes(context -> {
                    SkyFogConfig config = SkyFogConfig.get();
                    float value = FloatArgumentType.getFloat(context, "valor");
                    if (value <= config.start()) {
                        context.getSource().sendError(message(config, "end_invalid"));
                        return 0;
                    }
                    config.setEnd(value);
                    SkyFogConfig.save();
                    context.getSource().sendFeedback(message(config, "updated"));
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> colorCommand() {
        return ClientCommands.literal("color")
            .then(ClientCommands.argument("r", FloatArgumentType.floatArg(0.0F, 1.0F))
                .then(ClientCommands.argument("g", FloatArgumentType.floatArg(0.0F, 1.0F))
                    .then(ClientCommands.argument("b", FloatArgumentType.floatArg(0.0F, 1.0F))
                        .executes(context -> {
                            SkyFogConfig config = SkyFogConfig.get();
                            config.setColor(
                                FloatArgumentType.getFloat(context, "r"),
                                FloatArgumentType.getFloat(context, "g"),
                                FloatArgumentType.getFloat(context, "b")
                            );
                            SkyFogConfig.save();
                            context.getSource().sendFeedback(message(config, "color_updated"));
                            return 1;
                        }))));
    }

    private static Component message(SkyFogConfig config, String key) {
        String language = "es".equals(config.general.language) ? "es" : "en";
        return Component.translatable("chat.skyfog." + key + "." + language);
    }
}
