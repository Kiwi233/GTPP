package gtPlusPlus.core.gui.item;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gtPlusPlus.core.container.Container_Grindle;
import gtPlusPlus.core.inventories.BaseInventoryGrindle;
import gtPlusPlus.core.item.ModItems;
import gtPlusPlus.core.lib.CORE;
import gtPlusPlus.core.util.Utils;
import gtPlusPlus.core.util.minecraft.NBTUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class GuiScreenGrindle extends GuiContainer {
    private static final Logger logger = LogManager.getLogger();
    private static final ResourceLocation bookGuiTextures = new ResourceLocation("textures/gui/book.png");
    /** The player editing the book */
    private final EntityPlayer editingPlayer;

    private final ItemStack bookObj;
    /** Whether the book is signed or can still be edited */
    private final boolean bookIsUnsigned;

    private boolean field_146481_r;
    private boolean field_146480_s;
    /** Update ticks since the gui was opened */
    private int updateCount;

    private final int bookImageWidth = 192;
    private final int bookImageHeight = 192;
    private int bookTotalPages = 1;
    private int currPage;
    private NBTTagList bookPages;
    private String bookTitle = "";
    private GuiScreenGrindle.NextPageButton buttonNextPage;
    private GuiScreenGrindle.NextPageButton buttonPreviousPage;
    private GuiButton buttonDone;
    /** The GuiButton to sign this book. */
    private GuiButton buttonSign;

    private GuiButton buttonFinalize;
    private GuiButton buttonCancel;

    // Texture
    private static final ResourceLocation iconLocation =
            new ResourceLocation(CORE.MODID, "textures/gui/itemGrindle.png");

    /** The inventory to render on screen */
    private final BaseInventoryGrindle inventory;

    public GuiScreenGrindle(final Container_Grindle containerItem, final EntityPlayer player) {
        super(containerItem);
        this.inventory = containerItem.inventory;
        this.editingPlayer = player;
        this.bookObj = this.inventory.getStackInSlot(0);
        this.bookIsUnsigned = (this.bookObj == null ? true : false);

        if (this.bookObj != null) {
            if (this.bookObj.hasTagCompound()) {
                final NBTTagCompound nbttagcompound = this.bookObj.getTagCompound();
                this.bookPages = nbttagcompound.getTagList("pages", 8);

                if (this.bookPages != null) {
                    this.bookPages = (NBTTagList) this.bookPages.copy();
                    this.bookTotalPages = this.bookPages.tagCount();

                    if (this.bookTotalPages < 1) {
                        this.bookTotalPages = 1;
                    }
                }
            }

            if ((this.bookPages == null) && this.bookIsUnsigned) {
                this.bookPages = new NBTTagList();
                this.bookPages.appendTag(new NBTTagString(""));
                this.bookTotalPages = 1;
            }
        }
    }

    /**
     * Called from the main game loop to update the screen.
     */
    @Override
    public void updateScreen() {
        super.updateScreen();
        ++this.updateCount;
    }

    /**
     * Adds the buttons (and other controls) to the screen in question.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        this.buttonList.clear();
        Keyboard.enableRepeatEvents(true);

        if (this.bookIsUnsigned) {
            this.buttonList.add(
                    this.buttonSign = new GuiButton(
                            3,
                            (this.width / 2) - 100,
                            4 + this.bookImageHeight,
                            98,
                            20,
                            I18n.format("book.signButton", new Object[0])));
            this.buttonList.add(
                    this.buttonDone = new GuiButton(
                            0,
                            (this.width / 2) + 2,
                            this.bookImageHeight - 4,
                            98,
                            20,
                            I18n.format("gui.close", new Object[0])));
            this.buttonList.add(
                    this.buttonFinalize = new GuiButton(
                            5,
                            (this.width / 2) - 100,
                            4 + this.bookImageHeight,
                            98,
                            20,
                            I18n.format("book.finalizeButton", new Object[0])));
            this.buttonList.add(
                    this.buttonCancel = new GuiButton(
                            4,
                            (this.width / 2) + 2,
                            4 + this.bookImageHeight,
                            98,
                            20,
                            I18n.format("gui.cancel", new Object[0])));
        } else {
            this.buttonList.add(
                    this.buttonDone = new GuiButton(
                            0,
                            (this.width / 2) - 100,
                            this.bookImageHeight + 100,
                            200,
                            20,
                            I18n.format("gui.done", new Object[0])));
        }

        final int i = (this.width - this.bookImageWidth) / 2;
        final byte b0 = 2;
        this.buttonList.add(this.buttonNextPage = new GuiScreenGrindle.NextPageButton(1, i + 120, b0 + 154, true));
        this.buttonList.add(this.buttonPreviousPage = new GuiScreenGrindle.NextPageButton(2, i + 38, b0 + 154, false));
        this.updateButtons();
    }

    /**
     * Called when the screen is unloaded. Used to disable keyboard repeat
     * events
     */
    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void updateButtons() {
        this.buttonNextPage.visible =
                !this.field_146480_s && ((this.currPage < (this.bookTotalPages - 1)) || this.bookIsUnsigned);
        this.buttonPreviousPage.visible = !this.field_146480_s && (this.currPage > 0);
        this.buttonDone.visible = !this.bookIsUnsigned || !this.field_146480_s;

        if (this.bookIsUnsigned) {
            this.buttonSign.visible = !this.field_146480_s;
            this.buttonCancel.visible = this.field_146480_s;
            this.buttonFinalize.visible = this.field_146480_s;
            this.buttonFinalize.enabled = this.bookTitle.trim().length() > 0;
        }
    }

    private void sendBookToServer(final boolean p_146462_1_) {
        if (this.bookIsUnsigned && this.field_146481_r) {
            if (this.bookPages != null) {
                String s;

                while (this.bookPages.tagCount() > 1) {
                    s = this.bookPages.getStringTagAt(this.bookPages.tagCount() - 1);

                    if (s.length() != 0) {
                        break;
                    }

                    this.bookPages.removeTag(this.bookPages.tagCount() - 1);
                }

                if (this.bookObj.hasTagCompound()) {
                    final NBTTagCompound nbttagcompound = this.bookObj.getTagCompound();
                    nbttagcompound.setTag("pages", this.bookPages);
                } else {
                    this.bookObj.setTagInfo("pages", this.bookPages);
                }

                s = "MC|BEdit";

                if (p_146462_1_) {
                    s = "MC|BSign";
                    this.bookObj.setTagInfo("author", new NBTTagString(this.editingPlayer.getCommandSenderName()));
                    this.bookObj.setTagInfo("title", new NBTTagString(this.bookTitle.trim()));
                    this.bookObj.func_150996_a(ModItems.itemGrindleTablet);
                }

                final ByteBuf bytebuf = Unpooled.buffer();

                try {
                    (new PacketBuffer(bytebuf)).writeItemStackToBuffer(this.bookObj);
                    this.mc.getNetHandler().addToSendQueue(new C17PacketCustomPayload(s, bytebuf));
                } catch (final Exception exception) {
                    logger.error("Couldn\'t send book info", exception);
                } finally {
                    bytebuf.release();
                }
            }
        }
    }

    @Override
    protected void actionPerformed(final GuiButton button) {
        if (button.enabled) {
            if (button.id == 0) {
                this.mc.displayGuiScreen((GuiScreen) null);
                this.sendBookToServer(false);
            } else if ((button.id == 3) && this.bookIsUnsigned) {
                this.field_146480_s = true;
            } else if (button.id == 1) {
                if (this.currPage < (this.bookTotalPages - 1)) {
                    ++this.currPage;
                } else if (this.bookIsUnsigned) {
                    this.addNewPage();

                    if (this.currPage < (this.bookTotalPages - 1)) {
                        ++this.currPage;
                    }
                }
            } else if (button.id == 2) {
                if (this.currPage > 0) {
                    --this.currPage;
                }
            } else if ((button.id == 5) && this.field_146480_s) {
                this.sendBookToServer(true);
                this.mc.displayGuiScreen((GuiScreen) null);
            } else if ((button.id == 4) && this.field_146480_s) {
                this.field_146480_s = false;
            }

            this.updateButtons();
        }
    }

    private void addNewPage() {
        if ((this.bookPages != null) && (this.bookPages.tagCount() < 50)) {
            this.bookPages.appendTag(new NBTTagString(""));
            ++this.bookTotalPages;
            this.field_146481_r = true;
        }
    }

    /**
     * Fired when a key is typed. This is the equivalent of
     * KeyListener.keyTyped(KeyEvent e).
     */
    @Override
    protected void keyTyped(final char p_73869_1_, final int p_73869_2_) {
        super.keyTyped(p_73869_1_, p_73869_2_);

        if (this.bookIsUnsigned) {
            if (this.field_146480_s) {
                this.func_146460_c(p_73869_1_, p_73869_2_);
            } else {
                this.keyTypedInBook(p_73869_1_, p_73869_2_);
            }
        }
    }

    /**
     * Processes keystrokes when editing the text of a book
     */
    private void keyTypedInBook(final char p_146463_1_, final int p_146463_2_) {
        switch (p_146463_1_) {
            case 22:
                this.func_146459_b(GuiScreen.getClipboardString());
                return;
            default:
                switch (p_146463_2_) {
                    case 14:
                        final String s = this.func_146456_p();

                        if (s.length() > 0) {
                            this.func_146457_a(s.substring(0, s.length() - 1));
                        }

                        return;
                    case 28:
                    case 156:
                        this.func_146459_b("\n");
                        return;
                    default:
                        if (ChatAllowedCharacters.isAllowedCharacter(p_146463_1_)) {
                            this.func_146459_b(Character.toString(p_146463_1_));
                        }
                }
        }
    }

    private void func_146460_c(final char p_146460_1_, final int p_146460_2_) {
        switch (p_146460_2_) {
            case 14:
                if (!this.bookTitle.isEmpty()) {
                    this.bookTitle = this.bookTitle.substring(0, this.bookTitle.length() - 1);
                    this.updateButtons();
                }

                return;
            case 28:
            case 156:
                if (!this.bookTitle.isEmpty()) {
                    this.sendBookToServer(true);
                    this.mc.displayGuiScreen((GuiScreen) null);
                }

                return;
            default:
                if ((this.bookTitle.length() < 16) && ChatAllowedCharacters.isAllowedCharacter(p_146460_1_)) {
                    this.bookTitle = this.bookTitle + Character.toString(p_146460_1_);
                    this.updateButtons();
                    this.field_146481_r = true;
                }
        }
    }

    private String func_146456_p() {
        return (this.bookPages != null) && (this.currPage >= 0) && (this.currPage < this.bookPages.tagCount())
                ? this.bookPages.getStringTagAt(this.currPage)
                : "";
    }

    private void func_146457_a(final String p_146457_1_) {
        if ((this.bookPages != null) && (this.currPage >= 0) && (this.currPage < this.bookPages.tagCount())) {
            this.bookPages.func_150304_a(this.currPage, new NBTTagString(p_146457_1_));
            this.field_146481_r = true;
        }
    }

    private void func_146459_b(final String p_146459_1_) {
        final String s1 = this.func_146456_p();
        final String s2 = s1 + p_146459_1_;
        final int i = this.fontRendererObj.splitStringWidth(s2 + "" + EnumChatFormatting.BLACK + "_", 118);

        if ((i <= 118) && (s2.length() < 256)) {
            this.func_146457_a(s2);
        }
    }

    /**
     * Draws the screen and all the components in it.
     */
    @Override
    public void drawScreen(final int p_73863_1_, final int p_73863_2_, final float p_73863_3_) {

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(iconLocation);
        final int k = (this.width - this.xSize) / 2;
        final int l2 = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(k, l2, 0, 0, this.xSize, this.ySize);

        // GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        // this.mc.getTextureManager().bindTexture(iconLocation);
        // final int k = (this.width - this.bookImageWidth) / 2;
        // this.drawTexturedModalRect(k, b0, 0, 0, this.bookImageWidth,
        //		this.bookImageHeight);

        String s;
        String s1;
        int l;
        final byte b0 = 2;

        if (this.inventory.getStackInSlot(0) != null) {
            this.fontRendererObj.drawString(
                    I18n.format("" + NBTUtils.getBookTitle(this.inventory.getStackInSlot(0)), new Object[0]),
                    10,
                    8,
                    Utils.rgbtoHexValue(125, 255, 125));
        }

        if (this.field_146480_s) {
            s = this.bookTitle;

            if (this.bookIsUnsigned) {
                if (((this.updateCount / 6) % 2) == 0) {
                    s = s + "" + EnumChatFormatting.BLACK + "_";
                } else {
                    s = s + "" + EnumChatFormatting.GRAY + "_";
                }
            }

            s1 = I18n.format("book.editTitle", new Object[0]);
            l = this.fontRendererObj.getStringWidth(s1);
            this.fontRendererObj.drawString(s1, k + 36 + ((116 - l) / 2), b0 + 16 + 16, 0);
            final int i1 = this.fontRendererObj.getStringWidth(s);
            this.fontRendererObj.drawString(s, k + 36 + ((116 - i1) / 2), b0 + 48, 0);
            final String s2 = I18n.format("book.byAuthor", new Object[] {this.editingPlayer.getCommandSenderName()});
            final int j1 = this.fontRendererObj.getStringWidth(s2);
            this.fontRendererObj.drawString(
                    EnumChatFormatting.DARK_GRAY + s2, k + 36 + ((116 - j1) / 2), b0 + 48 + 10, 0);
            final String s3 = I18n.format("book.finalizeWarning", new Object[0]);
            this.fontRendererObj.drawSplitString(s3, k + 36, b0 + 80, 116, 0);
        } else {
            s = I18n.format(
                    "book.pageIndicator",
                    new Object[] {Integer.valueOf(this.currPage + 1), Integer.valueOf(this.bookTotalPages)});
            s1 = "";

            if ((this.bookPages != null) && (this.currPage >= 0) && (this.currPage < this.bookPages.tagCount())) {
                s1 = this.bookPages.getStringTagAt(this.currPage);
            }

            if (this.bookIsUnsigned) {
                if (this.fontRendererObj.getBidiFlag()) {
                    s1 = s1 + "_";
                } else if (((this.updateCount / 6) % 2) == 0) {
                    s1 = s1 + "" + EnumChatFormatting.BLACK + "_";
                } else {
                    s1 = s1 + "" + EnumChatFormatting.GRAY + "_";
                }
            }

            l = this.fontRendererObj.getStringWidth(s);
            this.fontRendererObj.drawString(s, ((k - l) + this.bookImageWidth) - 44, b0 + 16, 0);
            // this.fontRendererObj.drawString(s, k+36, b0 + 16, 0);
            this.fontRendererObj.drawSplitString(s1, k + 36, b0 + 16 + 16, 116, 0);
            // this.fontRendererObj.drawSplitString(s1, k, b0 + 16 + 16, 116, 0);
        }

        super.drawScreen(p_73863_1_, p_73863_2_, p_73863_3_);
    }

    @SideOnly(Side.CLIENT)
    static class NextPageButton extends GuiButton {
        private final boolean field_146151_o;

        public NextPageButton(
                final int p_i1079_1_, final int p_i1079_2_, final int p_i1079_3_, final boolean p_i1079_4_) {
            super(p_i1079_1_, p_i1079_2_, p_i1079_3_, 23, 13, "");
            this.field_146151_o = p_i1079_4_;
        }

        /**
         * Draws this button to the screen.
         */
        @Override
        public void drawButton(final Minecraft p_146112_1_, final int p_146112_2_, final int p_146112_3_) {
            if (this.visible) {
                final boolean flag = (p_146112_2_ >= this.xPosition)
                        && (p_146112_3_ >= this.yPosition)
                        && (p_146112_2_ < (this.xPosition + this.width))
                        && (p_146112_3_ < (this.yPosition + this.height));
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                p_146112_1_.getTextureManager().bindTexture(GuiScreenGrindle.bookGuiTextures);
                int k = 0;
                int l = 192;

                if (flag) {
                    k += 23;
                }

                if (!this.field_146151_o) {
                    l += 13;
                }

                this.drawTexturedModalRect(this.xPosition, this.yPosition, k, l, 23, 13);
            }
        }
    }

    /**
     * Draw the background layer for the GuiContainer (everything behind the
     * items)
     */
    @Override
    protected void drawGuiContainerBackgroundLayer(final float par1, final int par2, final int par3) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(iconLocation);
        final int k = (this.width - this.xSize) / 2;
        final int l = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(k, l, 0, 0, this.xSize, this.ySize);
        final int i1;
        // drawPlayerModel(k + 51, l + 75, 30, k + 51 - this.xSize_lo, (l + 75)
        // - 50 - this.ySize_lo, this.mc.thePlayer);
    }
}
