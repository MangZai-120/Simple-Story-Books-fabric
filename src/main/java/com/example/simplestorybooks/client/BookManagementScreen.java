package com.example.simplestorybooks.client;

import com.example.simplestorybooks.ConfigManager;
import com.example.simplestorybooks.ModNetworkingConstants;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.loot.LootTables;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BookManagementScreen extends Screen {
    private static final Identifier BOOK_TEXTURE = new Identifier("textures/gui/book.png");
    private final List<ConfigManager.BookData> books;
    private ConfigManager.BookData currentBook;
    private String originalBookJson;
    private boolean isEditing = false;
    private boolean isNewBook = false;
    private Text warningMessage = null;

    // Layout constants
    private int xOffset;
    private int yOffset;
    private static final int WIDTH = 192;
    private static final int HEIGHT = 192;
    private static final float SCALE = 1.35f;

    // Widgets
    private TextFieldWidget titleField;
    private TextFieldWidget authorField;
    private TextFieldWidget probabilityField;
    private ButtonWidget lootTablesButton;
    private ButtonWidget saveButton;
    private ButtonWidget deleteButton;
    private ButtonWidget cancelButton;
    private ButtonWidget editContentButton;
    private ButtonWidget newBookButton;
    
    // Autocomplete - Removed
    
    // Pagination for list
    private int listPage = 0;
    private static final int BOOKS_PER_PAGE = 4;

    public BookManagementScreen(List<ConfigManager.BookData> books) {
        super(Text.translatable("gui.simplestorybooks.manager.title"));
        this.books = new ArrayList<>();
        // Deep copy to avoid modifying the original list directly until saved
        Gson gson = new Gson();
        for (ConfigManager.BookData b : books) {
            this.books.add(gson.fromJson(gson.toJson(b), ConfigManager.BookData.class));
        }
    }

    public void updateBooks(List<ConfigManager.BookData> newBooks) {
        this.books.clear();
        Gson gson = new Gson();
        for (ConfigManager.BookData b : newBooks) {
            this.books.add(gson.fromJson(gson.toJson(b), ConfigManager.BookData.class));
        }
        // If we are in list view, refresh to show new data
        if (!isEditing) {
            this.init();
        }
    }

    @Override
    protected void init() {
        // Calculate centered position for the scaled book
        int scaledWidth = (int)(WIDTH * SCALE);
        int scaledHeight = (int)(HEIGHT * SCALE);
        this.xOffset = (this.width - scaledWidth) / 2;
        this.yOffset = (this.height - scaledHeight) / 2;

        if (isEditing) {
            initEditScreen();
        } else {
            initListScreen();
        }
    }

    private void initListScreen() {
        this.clearChildren();
        this.warningMessage = null;
        
        // Add "New Book" button
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.simplestorybooks.button.new_book"), button -> {
            this.currentBook = new ConfigManager.BookData();
            this.currentBook.title = "New Book";
            this.currentBook.author = "Author";
            this.currentBook.pages = new ArrayList<>();
            this.currentBook.pages.add(""); // Empty content by default
            this.originalBookJson = new Gson().toJson(this.currentBook);
            this.isNewBook = true;
            this.isEditing = true;
            this.init();
        }).dimensions(this.xOffset + (int)(36 * SCALE), this.yOffset + (int)(150 * SCALE), (int)(120 * SCALE), 20).build());

        // List books
        int start = listPage * BOOKS_PER_PAGE;
        int end = Math.min(start + BOOKS_PER_PAGE, books.size());
        
        for (int i = start; i < end; i++) {
            ConfigManager.BookData b = books.get(i);
            int y = this.yOffset + (int)(20 * SCALE) + (i - start) * (int)(25 * SCALE);
            
            this.addDrawableChild(ButtonWidget.builder(Text.of(truncate(b.title, 15)), button -> {
                this.currentBook = b;
                this.originalBookJson = new Gson().toJson(b);
                this.isNewBook = false;
                this.isEditing = true;
                this.init();
            }).dimensions(this.xOffset + (int)(36 * SCALE), y, (int)(120 * SCALE), 20).build());
        }

        // Pagination buttons
        if (listPage > 0) {
            this.addDrawableChild(ButtonWidget.builder(Text.of("<"), b -> {
                listPage--;
                init();
            }).dimensions(this.xOffset + (int)(10 * SCALE), this.yOffset + (int)(150 * SCALE), 20, 20).build());
        }
        if (end < books.size()) {
            this.addDrawableChild(ButtonWidget.builder(Text.of(">"), b -> {
                listPage++;
                init();
            }).dimensions(this.xOffset + (int)(162 * SCALE), this.yOffset + (int)(150 * SCALE), 20, 20).build());
        }
    }

    private void initEditScreen() {
        this.clearChildren();

        int fieldX = this.xOffset + (int)(36 * SCALE); // Align with buttons
        int fieldWidth = (int)(120 * SCALE); // Wider fields
        int startY = this.yOffset + (int)(12 * SCALE); // Moved up slightly
        int gap = (int)(24 * SCALE); // Reduced gap slightly

        // Title
        this.titleField = new TextFieldWidget(this.textRenderer, fieldX, startY + (int)(10 * SCALE), fieldWidth, 14, Text.translatable("gui.simplestorybooks.label.title"));
        this.titleField.setMaxLength(50);
        this.titleField.setText(currentBook.title);
        this.addDrawableChild(this.titleField);

        // Author
        this.authorField = new TextFieldWidget(this.textRenderer, fieldX, startY + gap + (int)(10 * SCALE), fieldWidth, 14, Text.translatable("gui.simplestorybooks.label.author"));
        this.authorField.setMaxLength(50);
        this.authorField.setText(currentBook.author);
        this.addDrawableChild(this.authorField);

        // Probability
        this.probabilityField = new TextFieldWidget(this.textRenderer, fieldX, startY + gap * 2 + (int)(10 * SCALE), fieldWidth, 14, Text.translatable("gui.simplestorybooks.label.probability"));
        this.probabilityField.setText(String.valueOf(currentBook.probability));
        this.addDrawableChild(this.probabilityField);

        // Loot Tables Button
        this.lootTablesButton = ButtonWidget.builder(Text.translatable("gui.simplestorybooks.label.loottables"), b -> {
            this.client.setScreen(new LootTableSelectionScreen(this, currentBook));
        }).dimensions(fieldX, startY + gap * 3 + (int)(5 * SCALE), fieldWidth, 20).build();
        this.addDrawableChild(this.lootTablesButton);

        // Edit Content Button
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.simplestorybooks.button.edit_pages"), b -> {
            this.client.setScreen(new BookPagesScreen(this, currentBook));
        }).dimensions(fieldX, startY + gap * 4 + (int)(5 * SCALE), fieldWidth, 20).build());

        // Save & Cancel (Side by side)
        int buttonWidth = (fieldWidth - 4) / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.simplestorybooks.button.save"), b -> {
            String newTitle = titleField.getText();
            for (ConfigManager.BookData book : books) {
                if (book != currentBook && book.title.equals(newTitle)) {
                    this.warningMessage = Text.translatable("gui.simplestorybooks.error.name_taken").formatted(Formatting.RED);
                    return;
                }
            }

            saveCurrentBook();
            sendSavePacket(currentBook);
            this.originalBookJson = new Gson().toJson(currentBook); // Update snapshot
            this.isEditing = false;
            if (isNewBook) {
                this.books.add(currentBook);
            }
            this.init();
        }).dimensions(fieldX, startY + gap * 4 + (int)(28 * SCALE), buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.simplestorybooks.button.cancel"), b -> {
            checkUnsavedChanges(() -> {
                revertChanges();
                this.isEditing = false;
                this.client.setScreen(this);
            });
        }).dimensions(fieldX + buttonWidth + 4, startY + gap * 4 + (int)(28 * SCALE), buttonWidth, 20).build());

        // Delete (Bottom) - Moved up
        if (!isNewBook) {
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.simplestorybooks.button.delete_book"), b -> {
                sendDeletePacket(currentBook.title);
                this.books.remove(currentBook);
                this.isEditing = false;
                this.init();
            }).dimensions(fieldX, startY + gap * 4 + (int)(50 * SCALE), fieldWidth, 20).build());
        }
    }

    private void checkUnsavedChanges(Runnable onConfirmExit) {
        // Sync UI to currentBook so that if we return, the data is preserved
        if (isEditing) {
             if (titleField != null) currentBook.title = titleField.getText();
             if (authorField != null) currentBook.author = authorField.getText();
             if (probabilityField != null) {
                 try {
                     currentBook.probability = Double.parseDouble(probabilityField.getText());
                 } catch (Exception e) {}
             }
        }

        if (hasUnsavedChanges()) {
            this.client.setScreen(new UnsavedChangesScreen(this, onConfirmExit));
        } else {
            onConfirmExit.run();
        }
    }

    private boolean hasUnsavedChanges() {
        if (currentBook == null || originalBookJson == null) return false;
        String currentJson = new Gson().toJson(currentBook);
        return !currentJson.equals(originalBookJson);
    }

    private void revertChanges() {
        if (originalBookJson != null) {
            ConfigManager.BookData original = new Gson().fromJson(originalBookJson, ConfigManager.BookData.class);
            currentBook.title = original.title;
            currentBook.author = original.author;
            currentBook.probability = original.probability;
            currentBook.lootTables = original.lootTables;
            currentBook.pages = original.pages;
            currentBook.matchMode = original.matchMode;
        }
    }

    @Override
    public void close() {
        if (isEditing) {
            checkUnsavedChanges(() -> {
                revertChanges();
                this.isEditing = false;
                this.client.setScreen(this);
            });
        } else {
            super.close();
        }
    }

    // ... saveCurrentBook, sendSavePacket, sendDeletePacket ...
    private void saveCurrentBook() {
        currentBook.title = titleField.getText();
        currentBook.author = authorField.getText();
        try {
            currentBook.probability = Double.parseDouble(probabilityField.getText());
        } catch (NumberFormatException e) {
            currentBook.probability = 1.0;
        }
        // Loot tables are updated directly in LootTableSelectionScreen
    }

    private void sendSavePacket(ConfigManager.BookData book) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(new Gson().toJson(book));
        ClientPlayNetworking.send(ModNetworkingConstants.SAVE_BOOK_ID, buf);
    }

    private void sendDeletePacket(String title) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(title);
        ClientPlayNetworking.send(ModNetworkingConstants.DELETE_BOOK_ID, buf);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        // Scale background
        context.getMatrices().push();
        context.getMatrices().translate(xOffset, yOffset, 0);
        context.getMatrices().scale(SCALE, SCALE, 1.0f);
        context.drawTexture(BOOK_TEXTURE, 0, 0, 0, 0, WIDTH, HEIGHT);
        context.getMatrices().pop();
        
        if (isEditing) {
            int startY = yOffset + (int)(12 * SCALE);
            int gap = (int)(24 * SCALE);
            int labelX = xOffset + (int)(36 * SCALE);
            
            context.drawText(this.textRenderer, Text.translatable("gui.simplestorybooks.label.title"), labelX, startY, 0xFF000000, false);
            context.drawText(this.textRenderer, Text.translatable("gui.simplestorybooks.label.author"), labelX, startY + gap, 0xFF000000, false);
            context.drawText(this.textRenderer, Text.translatable("gui.simplestorybooks.label.probability"), labelX, startY + gap * 2, 0xFF000000, false);
            // Loot tables label is now on the button
            
            if (this.warningMessage != null) {
                context.drawText(this.textRenderer, this.warningMessage, labelX, yOffset + (int)(175 * SCALE), 0xFF000000, false);
            }
        } else {
            context.drawText(this.textRenderer, Text.translatable("gui.simplestorybooks.label.book_list").append(" (" + (listPage + 1) + ")"), xOffset + (int)(36 * SCALE), yOffset + (int)(10 * SCALE), 0xFF000000, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    // Removed renderSuggestions and updateSuggestions methods as they are moved to LootTableSelectionScreen

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private String truncate(String s, int len) {
        if (s.length() <= len) return s;
        return s.substring(0, len) + "...";
    }
}
