package com.example.gulp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Every ON/OFF setting in one place (opened from Settings > Toggles...). Saved to config/gulp-client.properties. */
public class TogglesScreen extends Screen {
    private static final int W = 250;
    private static final int H = 200;

    private static final String INSTANT_TIP =
            "Shows a Digest button next to each mob in your stomach that digests it right away. Off by default.";
    private static final String RIGHT_CLICK_TIP =
            "Right-click an item in your stomach storage to eat it. Hold to keep eating, shift for the whole stack. "
            + "Turn off to get the normal right-click back (picking up half a stack).";

    private Button levelUpButton, actionButton, roleTagButton, rightClickButton, cpmButton, instantButton, tipsButton;
    private Boolean shownTips; // which tooltip state is currently applied to the buttons (null = none yet)

    public TogglesScreen() {
        super(Component.literal("Gulp Toggles"));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private static String onOff(boolean on) { return on ? "ON" : "OFF"; }

    private void refresh() {
        levelUpButton.setMessage(Component.literal("Level-up messages: " + onOff(GulpConfig.levelUpMessages)));
        actionButton.setMessage(Component.literal("Action messages: " + onOff(GulpConfig.actionMessages)));
        roleTagButton.setMessage(Component.literal("Role in name tags: " + onOff(GulpConfig.showRoleTags)));
        rightClickButton.setMessage(Component.literal("Right-click to eat: " + onOff(GulpConfig.rightClickEat)));
        cpmButton.setMessage(Component.literal("CPM belly sliders: " + onOff(GulpConfig.cpmSliders)
                + (CpmBridge.available() ? "" : " (CPM not found)")));
        instantButton.setMessage(Component.literal("Instant digest button: " + onOff(ClientState.isInstantDigest())));
        tipsButton.setMessage(Component.literal("Hover help tips: " + onOff(GulpConfig.helpTooltips)));
        if (shownTips == null || shownTips.booleanValue() != GulpConfig.helpTooltips) { // only rebuilt when the setting changes
            shownTips = GulpConfig.helpTooltips;
            boolean on = GulpConfig.helpTooltips;
            instantButton.setTooltip(on ? Tooltip.create(Component.literal(INSTANT_TIP)) : null);
            rightClickButton.setTooltip(on ? Tooltip.create(Component.literal(RIGHT_CLICK_TIP)) : null);
        }
    }

    @Override
    protected void init() {
        ScreenLoop.start();
        int x = left();
        int y = top();
        int bw = W - 30;

        // The short chat message when you level up.
        levelUpButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.levelUpMessages = !GulpConfig.levelUpMessages;
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 22, bw, 19).build();

        // The short action-bar messages when you do something: Gulp! Swallowed Cow, Too big for your stomach, ...
        actionButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.actionMessages = !GulpConfig.actionMessages;
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 43, bw, 19).build();

        // A coloured [Pred] / [Prey] / [Both] / [Neither] after every player's name above their head. Only on your screen.
        roleTagButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.showRoleTags = !GulpConfig.showRoleTags;
            GulpConfig.save();
            ClientRoleTags.refreshAll();
            refresh();
        }).bounds(x + 15, y + 64, bw, 19).build();

        // Right-click an item in the stomach storage to eat it (hold = keep eating). Off gives the normal right-click back.
        rightClickButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.rightClickEat = !GulpConfig.rightClickEat;
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 85, bw, 19).build();

        // Custom Player Models: your model's belly animations follow how full you are.
        cpmButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.cpmSliders = !GulpConfig.cpmSliders;
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 106, bw, 19).build();

        // Whether the Digest button shows next to each mob on the stomach screen. Kept on the server, off by default.
        instantButton = Button.builder(Component.empty(), btn -> Net.send(17, ClientState.isInstantDigest() ? 0 : 1))
                .bounds(x + 15, y + 127, bw, 19).build();

        // The explanatory tooltips when you hover over Gulp's buttons and screens.
        tipsButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.helpTooltips = !GulpConfig.helpTooltips;
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 148, bw, 19).build();

        addRenderableWidget(levelUpButton);
        addRenderableWidget(actionButton);
        addRenderableWidget(roleTagButton);
        addRenderableWidget(rightClickButton);
        addRenderableWidget(cpmButton);
        addRenderableWidget(instantButton);
        addRenderableWidget(tipsButton);

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> this.minecraft.setScreen(new SettingsScreen()))
                .bounds(x + 8, y + H - 28, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(x + W - 68, y + H - 28, 60, 20).build());

        shownTips = null; // buttons were just rebuilt, so give them their tooltips again
        refresh();
    }

    @Override
    public void tick() {
        refresh(); // the instant-digest label follows what the server says
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left();
        int y = top();

        g.fill(x, y, x + W, y + H, 0xE6141414);
        g.fill(x, y, x + W, y + 1, 0xFF666666);
        g.fill(x, y + H - 1, x + W, y + H, 0xFF666666);
        g.fill(x, y, x + 1, y + H, 0xFF666666);
        g.fill(x + W - 1, y, x + W, y + H, 0xFF666666);

        g.drawString(font, "Toggles", x + 8, y + 8, 0xFFFFFF, true);

        super.render(g, mouseX, mouseY, partialTick);
    }
}
