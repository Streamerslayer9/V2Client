package com.example.gulp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Client-side settings. Saved to config/gulp-client.properties. All the ON/OFF switches live in TogglesScreen. */
public class SettingsScreen extends Screen {
    private static final int W = 250;
    private static final int H = 182;

    private static final String SWALLOW_TIP =
            "Which mod does your swallowing. Gulp: eat mobs and players with V and your belly grows. Flamevore: Flamevore does it; "
            + "Gulp's mob eating and belly are off, but item swallowing, level, perks and item storage stay.";

    private Button bellyButton, roleButton, swallowButton, perkMenuButton;
    private Role shownRole;
    private boolean shownTips;

    public SettingsScreen() {
        super(Component.literal("Gulp Settings"));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private void refresh() {
        bellyButton.setMessage(Component.literal("Belly style: " + GulpConfig.bellyStyle.label));
        perkMenuButton.setMessage(Component.literal("Perk menu: " + (GulpConfig.legacyPerkMenu ? "Legacy" : "New look")));
        swallowButton.setMessage(Component.literal(swallowLabel()));
        swallowButton.active = ClientState.flamevoreActive(); // nothing to choose between without Flamevore on the server
        Role role = ClientState.role();
        roleButton.setMessage(Component.literal("Role: ").append(Component.literal(role.label).withStyle(st -> st.withColor(role.color & 0xFFFFFF))));
        // the tooltip is only rebuilt when the role or the hover-tips setting changes
        if (role != shownRole || shownTips != GulpConfig.helpTooltips) {
            shownRole = role;
            shownTips = GulpConfig.helpTooltips;
            roleButton.setTooltip(shownTips
                    ? Tooltip.create(Component.literal(role.description + "\n\nClick to switch: Pred, Prey, Both, Neither."))
                    : null);
            swallowButton.setTooltip(shownTips ? Tooltip.create(Component.literal(SWALLOW_TIP)) : null);
        }
    }

    private static String swallowLabel() {
        if (!ClientState.flamevoreActive()) return "Swallowing: Gulp (Flamevore not found)";
        SwallowSystem system = ClientState.swallowSystem();
        return "Swallowing: " + (system == SwallowSystem.UNSET ? "choose..." : system.label);
    }

    @Override
    protected void init() {
        ScreenLoop.start();
        int x = left();
        int y = top();

        bellyButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.bellyStyle = GulpConfig.bellyStyle.next();
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 22, W - 30, 19).build();

        // Your role is kept on the server (it decides who can swallow whom), not in your local settings file.
        roleButton = Button.builder(Component.empty(), btn -> Net.send(8, ClientState.role().next().ordinal()))
                .bounds(x + 15, y + 43, W - 30, 19).build();

        // Which mod does the swallowing (only changeable when Flamevore is installed). Kept on the server like your role.
        swallowButton = Button.builder(Component.empty(), btn -> {
            if (!ClientState.flamevoreActive()) return;
            if (ClientState.needsSwallowChoice()) {
                this.minecraft.setScreen(new SwallowChoiceScreen());
            } else {
                Net.send(19, (ClientState.usesGulpSwallow() ? SwallowSystem.FLAMEVORE : SwallowSystem.GULP).ordinal());
            }
        }).bounds(x + 15, y + 64, W - 30, 19).build();

        perkMenuButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.legacyPerkMenu = !GulpConfig.legacyPerkMenu;
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 85, W - 30, 19).build();

        addRenderableWidget(bellyButton);
        addRenderableWidget(roleButton);
        addRenderableWidget(swallowButton);
        addRenderableWidget(perkMenuButton);
        addRenderableWidget(Button.builder(Component.literal("Sounds..."), btn -> this.minecraft.setScreen(new SoundsScreen()))
                .bounds(x + 15, y + 106, W - 30, 19).build());
        // All the ON/OFF switches: messages, name tags, right-click eat, CPM sliders, instant digest, hover tips.
        addRenderableWidget(Button.builder(Component.literal("Toggles..."), btn -> this.minecraft.setScreen(new TogglesScreen()))
                .bounds(x + 15, y + 127, W - 30, 19).build());

        // With a role that can't swallow there is no stomach menu, so Back just closes.
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
                    if (ClientState.role().canEat()) this.minecraft.setScreen(new StomachScreen()); else onClose();
                })
                .bounds(x + 8, y + H - 28, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(x + W - 68, y + H - 28, 60, 20).build());

        shownRole = null; // buttons were just rebuilt, so give the role button its tooltip again
        refresh();
    }

    @Override
    public void tick() {
        refresh(); // the role label follows what the server says
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

        g.drawString(font, "Settings", x + 8, y + 8, 0xFFFFFF, true);

        super.render(g, mouseX, mouseY, partialTick);
    }
}
