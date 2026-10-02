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
 * Registra la configuración y el comando exclusivamente en el cliente.
 */
public final class SkyFogClient implements ClientModInitializer {
    private static KeyMapping openScreenKey;

    @Override
    public void onInitializeClient() {
        SkyFogConfig.initializeManaged();
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
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommands.literal("skyfog")
                .executes(context -> {
                    context.getSource().getClient().execute(SkyFogConfig::openEditor);
                    return 1;
                })
                .then(toggleCommand())
                .then(startCommand())
                .then(endCommand())
                .then(colorCommand())));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommands.literal("sf")
                .executes(context -> {
                    context.getSource().getClient().execute(SkyFogConfig::openEditor);
                    return 1;
                })
                .then(toggleCommand())
                .then(startCommand())
                .then(endCommand())
                .then(colorCommand())));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> toggleCommand() {
        return ClientCommands.literal("toggle").executes(context -> {
            SkyFogConfig config = SkyFogConfig.get();
            config.setEnabled(!config.enabled());
            SkyFogConfig.save();
            context.getSource().sendFeedback(Component.literal("SkyFog " + (config.enabled() ? "activada" : "desactivada")));
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
                        context.getSource().sendError(Component.literal("start debe ser menor que end"));
                        return 0;
                    }
                    config.setStart(value);
                    SkyFogConfig.save();
                    context.getSource().sendFeedback(Component.literal("Configuración de SkyFog actualizada"));
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
                        context.getSource().sendError(Component.literal("end debe ser mayor que start"));
                        return 0;
                    }
                    config.setEnd(value);
                    SkyFogConfig.save();
                    context.getSource().sendFeedback(Component.literal("Configuración de SkyFog actualizada"));
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
                            context.getSource().sendFeedback(Component.literal("Color de SkyFog actualizado"));
                            return 1;
                        }))));
    }
}
