package com.example.simplestorybooks;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Random;

public class RandomBookLootFunction extends ConditionalLootFunction {
    private final Random random = new Random();
    private final String tableId;

    protected RandomBookLootFunction(LootCondition[] conditions, String tableId) {
        super(conditions);
        this.tableId = tableId;
    }

    @Override
    public LootFunctionType getType() {
        return LootHandler.RANDOM_BOOK_FUNCTION;
    }

    @Override
    protected ItemStack process(ItemStack stack, LootContext context) {
        if (!stack.isOf(Items.WRITTEN_BOOK)) {
            return stack;
        }

        java.util.List<ConfigManager.BookData> candidates = new java.util.ArrayList<>();
        for (ConfigManager.BookData b : ConfigManager.loadedBooks) {
            if (this.tableId != null && b.lootTables != null) {
                boolean match = false;
                boolean isBlacklist = "BLACKLIST".equalsIgnoreCase(b.matchMode);
                
                // Check if "All" is present in list (case insensitive)
                boolean hasAll = false;
                for (String target : b.lootTables) {
                    if ("All".equalsIgnoreCase(target)) {
                        hasAll = true;
                        break;
                    }
                }

                if (hasAll) {
                    // If "All" is in list:
                    // In Whitelist mode: Matches everything.
                    // In Blacklist mode: Matches nothing (excludes everything).
                    match = !isBlacklist;
                } else {
                    // Normal check
                    boolean foundInList = false;
                    for (String target : b.lootTables) {
                        if (target.equals(this.tableId) || this.tableId.endsWith(target)) {
                            foundInList = true;
                            break;
                        }
                    }
                    
                    if (isBlacklist) {
                        match = !foundInList;
                    } else {
                        match = foundInList;
                    }
                }
                
                if (match) {
                    if (random.nextDouble() < b.probability) {
                        candidates.add(b);
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            // Debug log to help user understand why no books generated
            // SimpleStoryBooks.LOGGER.info("No book candidates for table: " + this.tableId);
            return ItemStack.EMPTY; 
        }

        ConfigManager.BookData bookData = candidates.get(random.nextInt(candidates.size()));

        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString("title", bookData.title);
        nbt.putString("author", bookData.author);
        
        NbtList pages = new NbtList();
        for (String pageContent : bookData.pages) {
            pages.add(NbtString.of(Text.Serializer.toJson(Text.of(pageContent))));
        }
        nbt.put("pages", pages);
        
        return stack;
    }

    public static ConditionalLootFunction.Builder<?> builder(String tableId) {
        return builder(conditions -> new RandomBookLootFunction(conditions, tableId));
    }

    public static class Serializer extends ConditionalLootFunction.Serializer<RandomBookLootFunction> {
        @Override
        public RandomBookLootFunction fromJson(JsonObject json, JsonDeserializationContext context, LootCondition[] conditions) {
            String tableId = json.has("tableId") ? json.get("tableId").getAsString() : null;
            return new RandomBookLootFunction(conditions, tableId);
        }

        @Override
        public void toJson(JsonObject json, RandomBookLootFunction object, JsonSerializationContext context) {
            super.toJson(json, object, context);
            if (object.tableId != null) {
                json.addProperty("tableId", object.tableId);
            }
        }
    }
}
