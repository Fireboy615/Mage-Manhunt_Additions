package net.fireboy.mageadditions.client.screen;

import net.fireboy.mageadditions.network.payload.SaveEquipmentPresetPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Small naming dialog used to snapshot the OP's current vanilla inventory/armor/offhand as a reusable loadout. */
public final class SaveEquipmentPresetScreen extends Screen {
    private final Screen parent;
    private EditBox nameBox;

    public SaveEquipmentPresetScreen(Screen parent) {
        super(Component.translatable("screen.mageadditions.loadout.save_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int y = height / 2 - 34;
        nameBox = addRenderableWidget(new EditBox(font, center - 120, y, 240, 22, Component.empty()));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.translatable("screen.mageadditions.loadout.name_hint"));
        addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.loadout.save"), b -> save())
            .bounds(center - 120, y + 34, 116, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> Minecraft.getInstance().setScreen(parent))
            .bounds(center + 4, y + 34, 116, 22).build());
        setInitialFocus(nameBox);
    }

    private void save() {
        String name = nameBox.getValue().strip();
        if (name.isEmpty()) return;
        PacketDistributor.sendToServer(new SaveEquipmentPresetPayload(name));
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 62, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.mageadditions.loadout.save_help"), width / 2, height / 2 - 48, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
