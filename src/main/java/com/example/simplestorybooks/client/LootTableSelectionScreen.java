package com.example.simplestorybooks.client;

import com.example.simplestorybooks.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.loot.LootTables;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LootTableSelectionScreen extends Screen {
    private static final Identifier BOOK_TEXTURE = new Identifier("textures/gui/book.png");
    private final Screen parent;
    private final ConfigManager.BookData book;
    
    private int xOffset;
    private int yOffset;
    private static final int WIDTH = 192;
    private static final int HEIGHT = 192;
    private static final float SCALE = 1.35f;

    private TextFieldWidget searchField;
    private ButtonWidget modeButton;
    private List<String> allLootTables;
    private List<String> suggestions = new ArrayList<>();
    private int selectedSuggestionIndex = -1;
    
    // Scrolling for selected list
    private int scrollOffset = 0;
    // private static final int ITEMS_VISIBLE = 6; // Removed fixed limit

    public LootTableSelectionScreen(Screen parent, ConfigManager.BookData book) {
        super(Text.translatable("gui.simplestorybooks.loottables.title"));
        this.parent = parent;
        this.book = book;
        if (this.book.matchMode == null) {
            this.book.matchMode = "WHITELIST";
        }
    }

    @Override
    protected void init() {
        int scaledWidth = (int)(WIDTH * SCALE);
        int scaledHeight = (int)(HEIGHT * SCALE);
        this.xOffset = (this.width - scaledWidth) / 2;
        this.yOffset = (this.height - scaledHeight) / 2;

        // Content area relative to the book texture (scaled)
        // Book texture writable area is roughly (36, 30) to (156, 160)
        int contentX = this.xOffset + (int)(36 * SCALE);
        int contentWidth = (int)(120 * SCALE);
        int startY = this.yOffset + (int)(15 * SCALE);

        // Mode Button
        this.modeButton = ButtonWidget.builder(getModeText(), b -> {
            if ("WHITELIST".equals(book.matchMode)) {
                book.matchMode = "BLACKLIST";
            } else {
                book.matchMode = "WHITELIST";
            }
            b.setMessage(getModeText());
        }).dimensions(contentX, startY, contentWidth, 20).build();
        this.addDrawableChild(modeButton);

        // Search Field
        this.searchField = new TextFieldWidget(this.textRenderer, contentX, startY + (int)(25 * SCALE), contentWidth, 20, Text.translatable("gui.simplestorybooks.loot_selection.search_placeholder"));
        this.searchField.setMaxLength(256);
        this.searchField.setChangedListener(this::updateSuggestions);
        this.addDrawableChild(this.searchField);

        // Done Button
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.simplestorybooks.button.done"), b -> {
            this.client.setScreen(parent);
        }).dimensions(contentX, this.yOffset + (int)(150 * SCALE), contentWidth, 20).build());

        // Initialize loot tables for suggestions
        if (allLootTables == null) {
             try {
                 this.allLootTables = LootTables.getAll().stream()
                     .map(Identifier::toString)
                     .sorted()
                     .collect(Collectors.toList());
                 this.allLootTables.add(0, "*all_chests*");
             } catch (Exception e) {
                 if (this.client != null && this.client.world != null) {
                    try {
                        this.allLootTables = this.client.world.getRegistryManager()
                            .get(RegistryKey.ofRegistry(new Identifier("minecraft", "loot_table"))).getIds().stream()
                            .map(Identifier::toString)
                            .sorted()
                            .collect(Collectors.toList());
                        this.allLootTables.add(0, "*all_chests*");
                    } catch (Exception ex) {}
                 }
             }
        }
    }

    private Text getModeText() {
        String key = "WHITELIST".equals(book.matchMode) ? "gui.simplestorybooks.loot_selection.mode.whitelist" : "gui.simplestorybooks.loot_selection.mode.blacklist";
        return Text.translatable(key);
    }

    private void updateSuggestions(String text) {
        if (searchField == null || !searchField.isFocused()) {
            suggestions.clear();
            return;
        }
        
        String query = text.trim().toLowerCase();
        if (query.isEmpty()) {
            suggestions.clear();
            return;
        }

        if (allLootTables != null) {
            this.suggestions = allLootTables.stream()
                    .filter(id -> id.toLowerCase().contains(query))
                    .limit(7)
                    .collect(Collectors.toList());
        }
        
        this.selectedSuggestionIndex = -1;
    }

    private void addEntry(String entry) {
        if (!book.lootTables.contains(entry)) {
            book.lootTables.add(entry);
            searchField.setText("");
            suggestions.clear();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        // Draw Background
        context.getMatrices().push();
        context.getMatrices().translate(xOffset, yOffset, 0);
        context.getMatrices().scale(SCALE, SCALE, 1.0f);
        context.drawTexture(BOOK_TEXTURE, 0, 0, 0, 0, WIDTH, HEIGHT);
        context.getMatrices().pop();

        super.render(context, mouseX, mouseY, delta);

        // Render Selected List
        int listX = xOffset + (int)(36 * SCALE);
        int listY = yOffset + (int)(55 * SCALE);
        int listWidth = (int)(120 * SCALE);
        int listHeight = (int)(90 * SCALE); // Available height for list
        
        context.drawText(this.textRenderer, Text.translatable("gui.simplestorybooks.loot_selection.selected"), listX, listY - 12, 0xFF000000, false);

        // Enable Scissor to clip content
        context.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int currentY = listY;
        int renderedCount = 0;
        
        for (int i = scrollOffset; i < book.lootTables.size(); i++) {
            String entry = book.lootTables.get(i);
            String display = entry.equals("*all_chests*") ? Text.translatable("gui.simplestorybooks.loot_selection.all_chests").getString() : entry;
            
            // Wrap text
            int textWidth = listWidth - 15; // Reserve space for X button
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(display), textWidth);
            int itemHeight = lines.size() * 10 + 4; // 10 per line + padding
            
            // Stop if we are out of bounds (but render at least one if it's the first one, though scissor handles clipping)
            if (currentY + itemHeight > listY + listHeight && renderedCount > 0) {
                break;
            }
            
            // Draw item background (box)
            context.fill(listX, currentY, listX + listWidth, currentY + itemHeight - 2, 0xFFDDDDDD);
            
            // Draw Text Lines
            for (int j = 0; j < lines.size(); j++) {
                context.drawText(this.textRenderer, lines.get(j), listX + 2, currentY + 2 + j * 10, 0xFF000000, false);
            }
            
            // Draw X button (centered vertically relative to item)
            int deleteX = listX + listWidth - 12;
            int deleteY = currentY + (itemHeight - 12) / 2;
            context.fill(deleteX, deleteY, deleteX + 10, deleteY + 10, 0xFFFFAAAA);
            context.drawText(this.textRenderer, "x", deleteX + 2, deleteY + 1, 0xFFFF0000, false);
            
            currentY += itemHeight;
            renderedCount++;
        }
        
        context.disableScissor();
        
        // Scrollbar indicator
        if (book.lootTables.size() > 0) {
            int scrollBarHeight = listHeight;
            int scrollBarX = listX + listWidth + 2;
            int scrollBarY = listY;
            
            // Simple scrollbar logic: handle size relative to total items vs visible items is hard with variable height.
            // We use a simplified approach: thumb position based on scrollOffset / total items.
            float ratio = (float)renderedCount / book.lootTables.size();
            ratio = Math.min(ratio, 1.0f);
            int thumbHeight = Math.max(10, (int)(scrollBarHeight * ratio));
            
            float scrollProgress = (float)scrollOffset / (book.lootTables.size() - renderedCount + 1); // Approximate
            if (book.lootTables.size() <= renderedCount) scrollProgress = 0;
            if (scrollProgress > 1) scrollProgress = 1;
            
            // Better scroll progress:
            int maxScroll = Math.max(0, book.lootTables.size() - 1);
            if (maxScroll > 0) {
                 scrollProgress = (float)scrollOffset / maxScroll;
            } else {
                scrollProgress = 0;
            }

            int thumbY = (int)((scrollBarHeight - thumbHeight) * scrollProgress);
            
            context.fill(scrollBarX, scrollBarY, scrollBarX + 2, scrollBarY + scrollBarHeight, 0xFFAAAAAA);
            context.fill(scrollBarX, scrollBarY + thumbY, scrollBarX + 2, scrollBarY + thumbY + thumbHeight, 0xFF444444);
        }

        // Render Suggestions (Overlay)
        if (searchField.isFocused() && !suggestions.isEmpty()) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200); // Topmost
            
            int x = searchField.getX();
            int y = searchField.getY() + searchField.getHeight();
            int width = searchField.getWidth();
            int height = suggestions.size() * 10;

            context.fill(x, y, x + width, y + height, 0xFF000000);
            
            for (int i = 0; i < suggestions.size(); i++) {
                int color = (i == selectedSuggestionIndex) ? 0xFFFFFF00 : 0xFFFFFFFF;
                String text = suggestions.get(i);
                if (text.equals("*all_chests*")) text = Text.translatable("gui.simplestorybooks.loot_selection.all_chests").getString();

                context.getMatrices().push();
                context.getMatrices().translate(x + 2, y + 2 + i * 10, 0);
                context.getMatrices().scale(0.7f, 0.7f, 1.0f);
                context.drawText(this.textRenderer, text, 0, 0, color, false);
                context.getMatrices().pop();
            }
            
            context.getMatrices().pop();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle delete buttons
        int listX = xOffset + (int)(36 * SCALE);
        int listY = yOffset + (int)(55 * SCALE);
        int listWidth = (int)(120 * SCALE);
        int listHeight = (int)(90 * SCALE);

        int currentY = listY;
        
        for (int i = scrollOffset; i < book.lootTables.size(); i++) {
            String entry = book.lootTables.get(i);
            String display = entry.equals("*all_chests*") ? Text.translatable("gui.simplestorybooks.loot_selection.all_chests").getString() : entry;
            
            int textWidth = listWidth - 15;
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(display), textWidth);
            int itemHeight = lines.size() * 10 + 4;
            
            if (currentY + itemHeight > listY + listHeight && i > scrollOffset) {
                break;
            }
            
            int deleteX = listX + listWidth - 12;
            int deleteY = currentY + (itemHeight - 12) / 2;
            
            // Check click on X button
            if (mouseX >= deleteX && mouseX <= deleteX + 10 && mouseY >= deleteY && mouseY <= deleteY + 10) {
                book.lootTables.remove(i);
                // Adjust scroll if needed
                if (scrollOffset > 0 && scrollOffset >= book.lootTables.size()) {
                    scrollOffset = Math.max(0, book.lootTables.size() - 1);
                }
                return true;
            }
            
            currentY += itemHeight;
        }

        // Handle suggestions click
        if (searchField.isFocused() && !suggestions.isEmpty()) {
            int x = searchField.getX();
            int y = searchField.getY() + searchField.getHeight();
            int width = searchField.getWidth();
            int height = suggestions.size() * 10;
            
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                int index = (int)((mouseY - y) / 10);
                if (index >= 0 && index < suggestions.size()) {
                    addEntry(suggestions.get(index));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField.isFocused() && !suggestions.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                selectedSuggestionIndex = (selectedSuggestionIndex + 1) % suggestions.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                selectedSuggestionIndex = (selectedSuggestionIndex - 1 + suggestions.size()) % suggestions.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                selectedSuggestionIndex = (selectedSuggestionIndex + 1) % suggestions.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                if (selectedSuggestionIndex >= 0 && selectedSuggestionIndex < suggestions.size()) {
                    addEntry(suggestions.get(selectedSuggestionIndex));
                    return true;
                } else if (!searchField.getText().isEmpty()) {
                    // Allow adding custom text even if not in suggestions (e.g. "All")
                    addEntry(searchField.getText());
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                suggestions.clear();
                return true;
            }
        } else if (searchField.isFocused() && keyCode == GLFW.GLFW_KEY_ENTER) {
             if (!searchField.getText().isEmpty()) {
                addEntry(searchField.getText());
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (amount < 0 && scrollOffset < book.lootTables.size() - 1) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
}
