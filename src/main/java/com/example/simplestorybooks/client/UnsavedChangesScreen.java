package com.example.simplestorybooks.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class UnsavedChangesScreen extends Screen {
    private static final Identifier BOOK_TEXTURE = new Identifier("textures/gui/book.png");
    private final Screen parent;
    private final Runnable onExit;
    
    private static final int WIDTH = 192;
    private static final int HEIGHT = 192;
    private static final float SCALE = 1.35f;

    public UnsavedChangesScreen(Screen parent, Runnable onExit) {
        super(Text.translatable("gui.simplestorybooks.unsaved.title"));
        this.parent = parent;
        this.onExit = onExit;
    }

    @Override
    protected void init() {
        int scaledWidth = (int)(WIDTH * SCALE);
        int scaledHeight = (int)(HEIGHT * SCALE);
        int xOffset = (this.width - scaledWidth) / 2;
        int yOffset = (this.height - scaledHeight) / 2;
        
        int contentX = xOffset + (int)(36 * SCALE);
        int contentWidth = (int)(120 * SCALE);
        int startY = yOffset + (int)(60 * SCALE);

        // Message
        // We'll render text in render()

        // Buttons
        int buttonWidth = (contentWidth - 10) / 2;
        
        // Exit (Discard) Button - Left
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.simplestorybooks.button.discard_exit"), b -> {
            onExit.run();
        }).dimensions(contentX, startY + (int)(40 * SCALE), buttonWidth, 20).build());

        // Return (Keep Editing) Button - Right
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.simplestorybooks.button.return_editing"), b -> {
            this.client.setScreen(parent);
        }).dimensions(contentX + buttonWidth + 10, startY + (int)(40 * SCALE), buttonWidth, 20).build());
    }
    
    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        int scaledWidth = (int)(WIDTH * SCALE);
        int scaledHeight = (int)(HEIGHT * SCALE);
        int xOffset = (this.width - scaledWidth) / 2;
        int yOffset = (this.height - scaledHeight) / 2;

        // Draw Background
        context.getMatrices().push();
        context.getMatrices().translate(xOffset, yOffset, 0);
        context.getMatrices().scale(SCALE, SCALE, 1.0f);
        context.drawTexture(BOOK_TEXTURE, 0, 0, 0, 0, WIDTH, HEIGHT);
        context.getMatrices().pop();

        super.render(context, mouseX, mouseY, delta);

        // Draw Message
        int contentX = xOffset + (int)(36 * SCALE);
        int contentWidth = (int)(120 * SCALE);
        int startY = yOffset + (int)(50 * SCALE);
        
        context.drawTextWrapped(this.textRenderer, Text.translatable("gui.simplestorybooks.message.unsaved_changes"), contentX, startY, contentWidth, 0xFF000000);
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
}
