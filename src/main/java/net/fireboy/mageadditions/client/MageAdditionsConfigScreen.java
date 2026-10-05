package net.fireboy.mageadditions.client;

import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Mage Additions' configuration landing page.
 *
 * The landing page is only navigation. Each module opens its own page where the
 * module's master enable/disable switch is always the first control at the top.
 */
public final class MageAdditionsConfigScreen extends Screen {
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_WIDTH = 300;

    private final Screen parent;

    public MageAdditionsConfigScreen(Screen parent) {
        super(Component.literal("Mage Additions Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int width = contentWidth();
        int left = this.width / 2 - width / 2;
        int y = 50;

        for (ModuleTab tab : ModuleTab.values()) {
            this.addRenderableWidget(
                    Button.builder(Component.literal(tab.displayName), button -> openModule(tab))
                            .bounds(left, y, width, BUTTON_HEIGHT)
                            .build()
            );
            y += 25;
        }

        this.addRenderableWidget(
                Button.builder(Component.literal("Done"), button -> onClose())
                        .bounds(left, Math.min(y + 12, this.height - 34), width, BUTTON_HEIGHT)
                        .build()
        );
    }

    private void openModule(ModuleTab tab) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ModuleConfigScreen(this, tab));
        }
    }

    private int contentWidth() {
        return Math.min(BUTTON_WIDTH, Math.max(220, this.width - 40));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(
                this.font,
                Component.literal("Choose a module to configure"),
                this.width / 2,
                36,
                0xA0A0A0
        );
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /** A module-specific page with its master toggle pinned at the top. */
    private static final class ModuleConfigScreen extends Screen {
        private static final int CONTENT_WIDTH = 300;
        private static final int CONTROL_HEIGHT = 22;

        private final Screen parent;
        private final ModuleTab tab;

        private boolean enabled;
        private boolean wizardArmorToughness;
        private boolean readOnlyRemoteServer;
        private Button enabledButton;
        private Button wizardArmorToughnessButton;
        private Component status = Component.empty();

        private ModuleConfigScreen(Screen parent, ModuleTab tab) {
            super(Component.literal(tab.displayName));
            this.parent = parent;
            this.tab = tab;
            CastTimeOverrides.ModuleStates states = CastTimeOverrides.moduleStates();
            this.enabled = tab.enabled(states);
            this.wizardArmorToughness = states.wizardArmorToughness();
        }

        @Override
        protected void init() {
            this.readOnlyRemoteServer = this.minecraft != null
                    && this.minecraft.getConnection() != null
                    && !this.minecraft.hasSingleplayerServer();

            // Refresh from the authoritative local snapshot each time this page
            // is reopened, including after returning from one of its editors.
            CastTimeOverrides.ModuleStates states = CastTimeOverrides.moduleStates();
            this.enabled = this.tab.enabled(states);
            this.wizardArmorToughness = states.wizardArmorToughness();

            int width = Math.min(CONTENT_WIDTH, Math.max(220, this.width - 40));
            int left = this.width / 2 - width / 2;
            int y = 58;

            // Master module switch: intentionally always the first control.
            this.enabledButton = this.addRenderableWidget(
                    Button.builder(moduleLabel(this.tab.displayName, this.enabled), button -> toggleModule())
                            .bounds(left, y, width, 26)
                            .build()
            );
            this.enabledButton.active = !this.readOnlyRemoteServer;

            y += 42;
            switch (this.tab) {
                case BALANCE_TWEAKS -> {
                    this.addRenderableWidget(
                            Button.builder(Component.literal("Open Spell Manager"), button -> {
                                if (this.minecraft != null) {
                                    this.minecraft.setScreen(new SpellManagerScreen(this));
                                }
                            }).bounds(left, y, width, CONTROL_HEIGHT).build()
                    );
                    this.wizardArmorToughnessButton = this.addRenderableWidget(
                            Button.builder(wizardArmorToughnessLabel(), button -> toggleWizardArmorToughness())
                                    .bounds(left, y + 30, width, CONTROL_HEIGHT)
                                    .build()
                    );
                    this.wizardArmorToughnessButton.active = !this.readOnlyRemoteServer;
                }
                case SPELL_REWORKS -> {
                    int reworkY = y;
                    for (ReworkEditorRegistry.Entry entry : ReworkEditorRegistry.entries()) {
                        this.addRenderableWidget(
                                Button.builder(
                                        Component.literal("Open " + entry.displayName() + " Rework"),
                                        button -> {
                                            if (this.minecraft != null) {
                                                this.minecraft.setScreen(entry.createScreen(this));
                                            }
                                        }
                                ).bounds(left, reworkY, width, CONTROL_HEIGHT).build()
                        );
                        reworkY += 30;
                    }
                }
                case CUSTOM_SPELLS -> this.addRenderableWidget(
                        Button.builder(Component.literal("Open Custom Spell Manager"), button -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new SpellManagerScreen(
                                        this,
                                        MageAdditions.MODID,
                                        Component.literal("Mage Additions - Custom Spells")
                                ));
                            }
                        }).bounds(left, y, width, CONTROL_HEIGHT).build()
                );
                case MINIGAME -> {
                    // The master switch controls the complete minigame system.
                }
                case SERVER_ADDITIONS -> {
                    // Domain-specific shape/radius testing currently lives in
                    // config/mage_additions_domain.json; this is the master switch.
                }
                case LOOT_CHANGES -> {
                    // This module only needs its master switch. The supplied
                    // loot-table pack is applied automatically on resource load.
                }
                case EXPERIMENTAL -> {
                    // Reserved for future opt-in testing controls.
                }
            }

            this.addRenderableWidget(
                    Button.builder(Component.literal("Back"), button -> onClose())
                            .bounds(left, this.height - 34, width, CONTROL_HEIGHT)
                            .build()
            );
        }

        private void toggleModule() {
            boolean previous = this.enabled;
            this.enabled = !this.enabled;

            CastTimeOverrides.ModuleStates current = CastTimeOverrides.moduleStates();
            CastTimeOverrides.ModuleStates updated = this.tab.withEnabled(current, this.enabled);
            CastTimeOverrides.ReloadResult result = CastTimeOverrides.saveModuleStates(updated);

            if (!result.success()) {
                this.enabled = previous;
                String error = result.error() == null ? "Unknown error" : result.error();
                this.status = Component.literal("Could not save: " + error)
                        .withStyle(ChatFormatting.RED);
            } else {
                this.status = Component.literal(this.enabled ? "Enabled." : "Disabled.")
                        .withStyle(this.enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW);

                if (this.tab == ModuleTab.LOOT_CHANGES
                        && this.minecraft != null
                        && this.minecraft.hasSingleplayerServer()
                        && this.minecraft.getSingleplayerServer() != null) {
                    var server = this.minecraft.getSingleplayerServer();
                    server.execute(() -> server.reloadResources(server.getPackRepository().getSelectedIds()));
                    this.status = Component.literal(
                                    (this.enabled ? "Enabled." : "Disabled.") + " Reloading loot tables..."
                            )
                            .withStyle(this.enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
                }
            }

            if (this.enabledButton != null) {
                this.enabledButton.setMessage(moduleLabel(this.tab.displayName, this.enabled));
            }
        }

        private Component wizardArmorToughnessLabel() {
            return moduleLabel("Wizard Armour Toughness", this.wizardArmorToughness);
        }

        private void toggleWizardArmorToughness() {
            boolean previous = this.wizardArmorToughness;
            this.wizardArmorToughness = !this.wizardArmorToughness;

            CastTimeOverrides.ModuleStates current = CastTimeOverrides.moduleStates();
            CastTimeOverrides.ModuleStates updated = new CastTimeOverrides.ModuleStates(
                    current.balanceTweaks(),
                    current.spellReworks(),
                    current.customSpells(),
                    current.minigame(),
                    current.serverAdditions(),
                    this.wizardArmorToughness,
                    current.lootChanges(),
                    current.experimental()
            );
            CastTimeOverrides.ReloadResult result = CastTimeOverrides.saveModuleStates(updated);

            if (!result.success()) {
                this.wizardArmorToughness = previous;
                String error = result.error() == null ? "Unknown error" : result.error();
                this.status = Component.literal("Could not save: " + error).withStyle(ChatFormatting.RED);
            } else {
                this.status = Component.literal(
                                "Wizard Armour Toughness " + (this.wizardArmorToughness ? "enabled." : "disabled.")
                        )
                        .withStyle(this.wizardArmorToughness ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
            }

            if (this.wizardArmorToughnessButton != null) {
                this.wizardArmorToughnessButton.setMessage(wizardArmorToughnessLabel());
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics, mouseX, mouseY, partialTick);
            super.render(graphics, mouseX, mouseY, partialTick);

            graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("Module settings"),
                    this.width / 2,
                    36,
                    0xA0A0A0
            );

            Component description = switch (this.tab) {
                case BALANCE_TWEAKS -> Component.literal(
                        "Per-spell balance overrides plus global equipment balance tweaks such as Wizard Armour toughness."
                );
                case SPELL_REWORKS -> Component.literal(
                        "Dedicated replacements and larger behaviour changes for existing Iron's Spells spells."
                );
                case CUSTOM_SPELLS -> Component.literal(
                        "Mage Additions custom spells. Open the manager to configure Piercing, Mace Infusion, Mirror Image, and future custom spells."
                );
                case MINIGAME -> Component.literal(
                        "Master switch for team selection, match setup, protection, borders, scoring, match controls and other minigame features."
                );
                case SERVER_ADDITIONS -> Component.literal(
                        "General server mechanics kept separate from Mage Manhunt and spell tweaks. Currently contains the Domain Relic."
                );
                case LOOT_CHANGES -> Component.literal(
                        "Uses the custom Iron's loot tables for bookshelves, magic treasure, mage drops, curios, ink, and pyromancer supplies."
                );
                case EXPERIMENTAL -> Component.literal(
                        "Opt-in testing features that are kept separate from normal balance and rework settings."
                );
            };

            if (this.height >= 220) {
                graphics.drawWordWrap(
                        this.font,
                        description.copy().withStyle(ChatFormatting.GRAY),
                        Math.max(12, this.width / 2 - 150),
                        this.tab == ModuleTab.SPELL_REWORKS
                                ? 110 + ReworkEditorRegistry.entries().size() * 30
                                : this.tab == ModuleTab.BALANCE_TWEAKS ? 166 : 136,
                        Math.min(300, this.width - 24),
                        0xFFFFFF
                );
            }

            if (this.readOnlyRemoteServer) {
                graphics.drawCenteredString(
                        this.font,
                        Component.literal("Remote server: this module switch is read-only here.")
                                .withStyle(ChatFormatting.RED),
                        this.width / 2,
                        this.height - 50,
                        0xFFFFFF
                );
            } else if (!this.status.getString().isEmpty()) {
                graphics.drawCenteredString(
                        this.font,
                        this.status,
                        this.width / 2,
                        this.height - 50,
                        0xFFFFFF
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

    private static Component moduleLabel(String name, boolean enabled) {
        MutableComponent label = Component.literal(name + ": ");
        return label.append(
                Component.literal(enabled ? "ENABLED" : "DISABLED")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)
        );
    }

    private enum ModuleTab {
        BALANCE_TWEAKS("Balance Tweaks") {
            @Override
            boolean enabled(CastTimeOverrides.ModuleStates states) {
                return states.balanceTweaks();
            }

            @Override
            CastTimeOverrides.ModuleStates withEnabled(CastTimeOverrides.ModuleStates states, boolean enabled) {
                return new CastTimeOverrides.ModuleStates(
                        enabled,
                        states.spellReworks(),
                        states.customSpells(),
                        states.minigame(),
                        states.serverAdditions(),
                        states.wizardArmorToughness(),
                        states.lootChanges(),
                        states.experimental()
                );
            }
        },
        SPELL_REWORKS("Spell Reworks") {
            @Override
            boolean enabled(CastTimeOverrides.ModuleStates states) {
                return states.spellReworks();
            }

            @Override
            CastTimeOverrides.ModuleStates withEnabled(CastTimeOverrides.ModuleStates states, boolean enabled) {
                return new CastTimeOverrides.ModuleStates(
                        states.balanceTweaks(),
                        enabled,
                        states.customSpells(),
                        states.minigame(),
                        states.serverAdditions(),
                        states.wizardArmorToughness(),
                        states.lootChanges(),
                        states.experimental()
                );
            }
        },
        CUSTOM_SPELLS("Custom Spells") {
            @Override
            boolean enabled(CastTimeOverrides.ModuleStates states) {
                return states.customSpells();
            }

            @Override
            CastTimeOverrides.ModuleStates withEnabled(CastTimeOverrides.ModuleStates states, boolean enabled) {
                return new CastTimeOverrides.ModuleStates(
                        states.balanceTweaks(),
                        states.spellReworks(),
                        enabled,
                        states.minigame(),
                        states.serverAdditions(),
                        states.wizardArmorToughness(),
                        states.lootChanges(),
                        states.experimental()
                );
            }
        },
        MINIGAME("Minigame") {
            @Override
            boolean enabled(CastTimeOverrides.ModuleStates states) {
                return states.minigame();
            }

            @Override
            CastTimeOverrides.ModuleStates withEnabled(CastTimeOverrides.ModuleStates states, boolean enabled) {
                return new CastTimeOverrides.ModuleStates(
                        states.balanceTweaks(),
                        states.spellReworks(),
                        states.customSpells(),
                        enabled,
                        states.serverAdditions(),
                        states.wizardArmorToughness(),
                        states.lootChanges(),
                        states.experimental()
                );
            }
        },
        SERVER_ADDITIONS("Server Additions") {
            @Override
            boolean enabled(CastTimeOverrides.ModuleStates states) {
                return states.serverAdditions();
            }

            @Override
            CastTimeOverrides.ModuleStates withEnabled(CastTimeOverrides.ModuleStates states, boolean enabled) {
                return new CastTimeOverrides.ModuleStates(
                        states.balanceTweaks(),
                        states.spellReworks(),
                        states.customSpells(),
                        states.minigame(),
                        enabled,
                        states.wizardArmorToughness(),
                        states.lootChanges(),
                        states.experimental()
                );
            }
        },
        LOOT_CHANGES("Loot Changes") {
            @Override
            boolean enabled(CastTimeOverrides.ModuleStates states) {
                return states.lootChanges();
            }

            @Override
            CastTimeOverrides.ModuleStates withEnabled(CastTimeOverrides.ModuleStates states, boolean enabled) {
                return new CastTimeOverrides.ModuleStates(
                        states.balanceTweaks(),
                        states.spellReworks(),
                        states.customSpells(),
                        states.minigame(),
                        states.serverAdditions(),
                        states.wizardArmorToughness(),
                        enabled,
                        states.experimental()
                );
            }
        },
        EXPERIMENTAL("Experimental") {
            @Override
            boolean enabled(CastTimeOverrides.ModuleStates states) {
                return states.experimental();
            }

            @Override
            CastTimeOverrides.ModuleStates withEnabled(CastTimeOverrides.ModuleStates states, boolean enabled) {
                return new CastTimeOverrides.ModuleStates(
                        states.balanceTweaks(),
                        states.spellReworks(),
                        states.customSpells(),
                        states.minigame(),
                        states.serverAdditions(),
                        states.wizardArmorToughness(),
                        states.lootChanges(),
                        enabled
                );
            }
        };

        private final String displayName;

        ModuleTab(String displayName) {
            this.displayName = displayName;
        }

        abstract boolean enabled(CastTimeOverrides.ModuleStates states);

        abstract CastTimeOverrides.ModuleStates withEnabled(CastTimeOverrides.ModuleStates states, boolean enabled);
    }
}
