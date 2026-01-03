package com.example.simplestorybooks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;

public class SimpleStoryBooks implements ModInitializer {
	public static final String MOD_ID = "simplestorybooks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Simple Story Books mod!");
        
        // Initialize Config
        ConfigManager.loadConfig();
        
        // Register Loot Table Events
        LootHandler.register();

        // Register Reload Command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("ssbreload")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> {
                    ConfigManager.loadConfig();
                    context.getSource().sendFeedback(() -> Text.translatable("message.simplestorybooks.reloaded"), false);
                    return 1;
                }));
            
            dispatcher.register(CommandManager.literal("simplestory")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player != null) {
                        // Send book list to client to OPEN GUI
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeString(new Gson().toJson(ConfigManager.loadedBooks));
                        ServerPlayNetworking.send(player, ModNetworkingConstants.OPEN_GUI_ID, buf);
                    }
                    return 1;
                }));
        });

        // Register Packet Receivers
        ServerPlayNetworking.registerGlobalReceiver(ModNetworkingConstants.SAVE_BOOK_ID, (server, player, handler, buf, responseSender) -> {
            if (!player.hasPermissionLevel(2)) return;
            String json = buf.readString();
            server.execute(() -> {
                ConfigManager.BookData book = new Gson().fromJson(json, ConfigManager.BookData.class);
                if (book != null && book.isValid()) {
                    ConfigManager.saveBookToFile(book);
                    player.sendMessage(Text.translatable("message.simplestorybooks.saved", book.title), false);
                    broadcastBookUpdate(server);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ModNetworkingConstants.DELETE_BOOK_ID, (server, player, handler, buf, responseSender) -> {
            if (!player.hasPermissionLevel(2)) return;
            String title = buf.readString();
            server.execute(() -> {
                ConfigManager.deleteBookFile(title);
                player.sendMessage(Text.translatable("message.simplestorybooks.deleted", title), false);
                broadcastBookUpdate(server);
            });
        });
	}

    private void broadcastBookUpdate(net.minecraft.server.MinecraftServer server) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(new Gson().toJson(ConfigManager.loadedBooks));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.hasPermissionLevel(2)) {
                ServerPlayNetworking.send(p, ModNetworkingConstants.SYNC_BOOKS_ID, buf);
            }
        }
    }
}
