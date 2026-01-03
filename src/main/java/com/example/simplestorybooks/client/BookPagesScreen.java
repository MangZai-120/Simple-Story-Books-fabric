package com.example.simplestorybooks.client;

import com.example.simplestorybooks.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class BookPagesScreen extends Screen {
    private static final Identifier BOOK_TEXTURE = new Identifier("textures/gui/book.png");
    private final Screen parent;
    private final ConfigManager.BookData book;
    private int currentPage = 0;
    private EditBoxWidget pageContentField;
    
    private int xOffset;
    private int yOffset;
    private static final int WIDTH = 192;
    private static final int HEIGHT = 192;
    private static final float SCALE = 1.35f;

    public BookPagesScreen(Screen parent, ConfigManager.BookData book) {
        super(Text.translatable("gui.simplestorybooks.pages.title"));
        this.parent = parent;
        this.book = book;
        if (this.book.pages.isEmpty()) {
            this.book.pages.add("");
        }
    }

    @Override
    protected void init() {
        this.clearChildren();
        int scaledWidth = (int)(WIDTH * SCALE);
        int scaledHeight = (int)(HEIGHT * SCALE);
        this.xOffset = (this.width - scaledWidth) / 2;
        this.yOffset = (this.height - scaledHeight) / 2;

        // Page Content - Make it look like writing on paper
        // Position relative to the scaled book background
        int contentX = this.xOffset + (int)(36 * SCALE);
        int contentY = this.yOffset + (int)(30 * SCALE);
        int contentWidth = (int)(116 * SCALE);
        int contentHeight = (int)(100 * SCALE);

        // Pass empty text for placeholder to avoid overlap
        this.pageContentField = new EditBoxWidget(this.textRenderer, contentX, contentY, contentWidth, contentHeight, Text.of(""), Text.of(""));
        this.pageContentField.setText(book.pages.get(currentPage));
        this.addDrawableChild(this.pageContentField);

        // Prev Page
        this.addDrawableChild(ButtonWidget.builder(Text.of("<"), b -> {
            saveCurrentPage();
            if (currentPage > 0) {
                currentPage--;
                init();
            }
        }).dimensions(this.xOffset + (int)(10 * SCALE), this.yOffset + (int)(150 * SCALE), 20, 20).build());

        // Next Page / Add Page
        this.addDrawableChild(ButtonWidget.builder(Text.of(">"), b -> {
            saveCurrentPage();
            if (currentPage < book.pages.size() - 1) {
                currentPage++;
            } else {
                book.pages.add("");
                currentPage++;
            }
            init();
        }).dimensions(this.xOffset + (int)(162 * SCALE), this.yOffset + (int)(150 * SCALE), 20, 20).build());

        // Delete Page
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.simplestorybooks.button.delete_page"), b -> {
            if (book.pages.size() > 1) {
                book.pages.remove(currentPage);
                if (currentPage >= book.pages.size()) currentPage--;
                init();
            }
        }).dimensions(this.xOffset + (int)(36 * SCALE), this.yOffset + (int)(150 * SCALE), (int)(60 * SCALE), 20).build());

        // Done
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.simplestorybooks.button.done"), b -> {
            saveCurrentPage();
            this.client.setScreen(parent);
        }).dimensions(this.xOffset + (int)(100 * SCALE), this.yOffset + (int)(150 * SCALE), (int)(50 * SCALE), 20).build());
    }

    private void saveCurrentPage() {
        if (currentPage < book.pages.size()) {
            book.pages.set(currentPage, pageContentField.getText());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        context.getMatrices().push();
        context.getMatrices().translate(xOffset, yOffset, 0);
        context.getMatrices().scale(SCALE, SCALE, 1.0f);
        context.drawTexture(BOOK_TEXTURE, 0, 0, 0, 0, WIDTH, HEIGHT);
        context.getMatrices().pop();

        // Restore page number display
        context.drawText(this.textRenderer, Text.translatable("gui.simplestorybooks.label.page").append(" " + (currentPage + 1) + "/" + book.pages.size()), xOffset + (int)(36 * SCALE), yOffset + (int)(15 * SCALE), 0xFF000000, false);
        super.render(context, mouseX, mouseY, delta);
    }
}
