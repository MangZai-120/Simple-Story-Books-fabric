package com.example.simplestorybooks.client;

import com.example.simplestorybooks.ConfigManager;
import com.example.simplestorybooks.ModNetworkingConstants;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.lang.reflect.Type;
import java.util.List;

public class SimpleStoryBooksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworkingConstants.OPEN_GUI_ID, (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> {
                Gson gson = new Gson();
                Type listType = new TypeToken<List<ConfigManager.BookData>>(){}.getType();
                List<ConfigManager.BookData> books = gson.fromJson(json, listType);
                client.setScreen(new BookManagementScreen(books));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModNetworkingConstants.SYNC_BOOKS_ID, (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> {
                if (client.currentScreen instanceof BookManagementScreen screen) {
                    Gson gson = new Gson();
                    Type listType = new TypeToken<List<ConfigManager.BookData>>(){}.getType();
                    List<ConfigManager.BookData> books = gson.fromJson(json, listType);
                    screen.updateBooks(books);
                }
            });
        });
    }
}
