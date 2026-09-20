package net.fireboy.mageadditions.client;

import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Mage Additions' config landing page.
 *
 * The module switches stay here, while spell-by-spell configuration lives in
 * {@link SpellManagerScreen}. This keeps the main config screen simple even as
 * the spell editor grows.
 */
public final class MageAdditionsConfigScreen extends Screen {
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;

    private boolean balanceTweaks;
    private boolean spellReworks;
    private boolean customSpells;
    private boolean experimental;

    private Button balanceButton;
    private Button reworksButton;
    private Button customSpellsButton;
    private Button experimentalButton;
    private Button saveButton;

    private Component statusMessage = Component.empty();
    private boolean readOnlyRemoteServer;

    public MageAdditionsConfigScreen(Screen parent) {
        super(Component.literal("Mage Additions Configuration"));
        this.parent = parent;

        CastTimeOverrides.ModuleStates states = CastTimeOverrides.moduleStates();
        this.balanceTweaks = states.balanceTweaks();
        this.spellReworks = states.spellReworks();
        this.customSpells = states.customSpells();
        this.experimental = states.experimental();
    }

    @Override
    protected void init() {
        // Gameplay settings are server-authoritative. A remote client can browse
        // the Spell Manager but cannot save server settings from this first UI.
        this.readOnlyRemoteServer = this.minecraft != null
                && this.minecraft.getConnection() != null
                && !this.minecraft.hasSingleplayerServer();

        int left = this.width / 2 - BUTTON_WIDTH / 2;
        int y = 54;

        this.addRenderableWidget(
                Button.builder(Component.literal("Open Spell Manager"), button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SpellManagerScreen(this));
                    }
                }).bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()
        );

        y += 34;
        this.balanceButton = this.addRenderableWidget(
                Button.builder(moduleLabel("Balance Tweaks", this.balanceTweaks), button -> {
                    this.balanceTweaks = !this.balanceTweaks;
                    button.setMessage(moduleLabel("Balance Tweaks", this.balanceTweaks));
                }).bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()
        );

        y += 28;
        this.reworksButton = this.addRenderableWidget(
                Button.builder(moduleLabel("Spell Reworks", this.spellReworks), button -> {
                    this.spellReworks = !this.spellReworks;
                    button.setMessage(moduleLabel("Spell Reworks", this.spellReworks));
                }).bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()
        );

        y += 28;
        this.customSpellsButton = this.addRenderableWidget(
                Button.builder(moduleLabel("Custom Spells", this.customSpells), button -> {
                    this.customSpells = !this.customSpells;
                    button.setMessage(moduleLabel("Custom Spells", this.customSpells));
                }).bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()
        );

        y += 28;
        this.experimentalButton = this.addRenderableWidget(
                Button.builder(moduleLabel("Experimental", this.experimental), button -> {
                    this.experimental = !this.experimental;
                    button.setMessage(moduleLabel("Experimental", this.experimental));
                }).bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()
        );

        y += 36;
        int halfWidth = (BUTTON_WIDTH - 6) / 2;
        this.saveButton = this.addRenderableWidget(
                Button.builder(Component.literal("Save & Close"), button -> saveAndClose())
                        .bounds(left, y, halfWidth, BUTTON_HEIGHT)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), button -> this.onClose())
                        .bounds(left + halfWidth + 6, y, halfWidth, BUTTON_HEIGHT)
                        .build()
        );

        y += 26;
        this.addRenderableWidget(
                Button.builder(Component.literal("Reset module defaults"), button -> resetDefaults())
                        .bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build()
        );

        setEditingEnabled(!this.readOnlyRemoteServer);
    }

    private void setEditingEnabled(boolean enabled) {
        this.balanceButton.active = enabled;
        this.reworksButton.active = enabled;
        this.customSpellsButton.active = enabled;
        this.experimentalButton.active = enabled;
        this.saveButton.active = enabled;
    }

    private void resetDefaults() {
        CastTimeOverrides.ModuleStates defaults = CastTimeOverrides.ModuleStates.defaults();
        this.balanceTweaks = defaults.balanceTweaks();
        this.spellReworks = defaults.spellReworks();
        this.customSpells = defaults.customSpells();
        this.experimental = defaults.experimental();

        this.balanceButton.setMessage(moduleLabel("Balance Tweaks", this.balanceTweaks));
        this.reworksButton.setMessage(moduleLabel("Spell Reworks", this.spellReworks));
        this.customSpellsButton.setMessage(moduleLabel("Custom Spells", this.customSpells));
        this.experimentalButton.setMessage(moduleLabel("Experimental", this.experimental));
        this.statusMessage = Component.literal("Defaults selected - press Save & Close to apply.")
                .withStyle(ChatFormatting.YELLOW);
    }

    private void saveAndClose() {
        CastTimeOverrides.ReloadResult result = CastTimeOverrides.saveModuleStates(
                new CastTimeOverrides.ModuleStates(
                        this.balanceTweaks,
                        this.spellReworks,
                        this.customSpells,
                        this.experimental
                )
        );

        if (!result.success()) {
            String error = result.error() == null ? "Unknown error" : result.error();
            this.statusMessage = Component.literal("Could not save: " + error)
                    .withStyle(ChatFormatting.RED);
            return;
        }

        this.statusMessage = Component.literal("Saved.").withStyle(ChatFormatting.GREEN);
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private static Component moduleLabel(String name, boolean enabled) {
        MutableComponent label = Component.literal(name + ": ");
        return label.append(
                Component.literal(enabled ? "ON" : "OFF")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(
                this.font,
                Component.literal("Modules and spell balancing"),
                this.width / 2,
                36,
                0xA0A0A0
        );

        if (this.readOnlyRemoteServer) {
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("Remote server: browsing is available, editing is read-only for now.")
                            .withStyle(ChatFormatting.RED),
                    this.width / 2,
                    this.height - 28,
                    0xFFFFFF
            );
        } else if (!this.statusMessage.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.statusMessage, this.width / 2, this.height - 28, 0xFFFFFF);
        } else {
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("Spell Manager currently provides automatic discovery + search; editing comes next."),
                    this.width / 2,
                    this.height - 28,
                    0x808080
            );
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
