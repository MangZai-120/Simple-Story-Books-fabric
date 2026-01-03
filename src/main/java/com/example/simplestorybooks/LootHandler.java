package com.example.simplestorybooks;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class LootHandler {
    public static LootFunctionType RANDOM_BOOK_FUNCTION;

    public static void register() {
        // Register Loot Function
        RANDOM_BOOK_FUNCTION = Registry.register(Registries.LOOT_FUNCTION_TYPE, 
            new Identifier(SimpleStoryBooks.MOD_ID, "random_book"), 
            new LootFunctionType(new RandomBookLootFunction.Serializer()));

        // Register Event
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            String tableId = id.toString();
            boolean shouldInject = ConfigManager.targetLootTables.contains(tableId);
            
            // Check if any book specifically targets this table
            if (!shouldInject) {
                for (ConfigManager.BookData book : ConfigManager.loadedBooks) {
                    if (book.lootTables != null && book.lootTables.contains(tableId)) {
                        shouldInject = true;
                        break;
                    }
                }
            }

            if (shouldInject) {
                LootPool.Builder poolBuilder = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(ConfigManager.lootChance))
                        .with(ItemEntry.builder(Items.WRITTEN_BOOK)
                                .apply(RandomBookLootFunction.builder(tableId)));

                tableBuilder.pool(poolBuilder);
            }
        });
    }
}
