package com.reflectshield.client.gui;

import com.reflectshield.common.config.ModConfig;
import com.reflectshield.common.util.ItemMatcher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 反弹盾配置界面。
 *
 * 布局：
 *   ┌─────────────────────────────────────────────┐
 *   │  标题（居中）                                │
 *   ├──────────────────┬──────────────────────────┤
 *   │ 左列（可滚动）   │ 右列（白名单，可滚动）   │
 *   │  数值参数        │  物品白名单              │
 *   │  勾选框          │  ...                     │
 *   │  模式按钮        │  +/-                     │
 *   ├──────────────────┴──────────────────────────┤  ← 分隔线
 *   │  [恢复默认值]   [保存]   [取消]             │  ← 固定底部
 *   └─────────────────────────────────────────────┘
 *
 * 左列内容若超出可见高度可用鼠标滚轮（在左列区域内）滚动。
 * 右列白名单同理。
 * 底部按钮区始终固定可见。
 */
public class ConfigScreen extends Screen {

    private final Screen lastScreen;

    // ── 数值输入框 ──
    private EditBox speedField;
    private EditBox durationField;
    private EditBox cooldownField;
    private EditBox widthField;
    private EditBox heightField;
    private EditBox depthField;
    private EditBox distanceField;

    // ── 模式按钮 / 勾选框 ──
    private Button modeButton;
    private Button fireballModeButton;
    private Checkbox debugCheckbox;
    private Checkbox playerHitboxCheckbox;

    private int currentMode;
    private int currentFireballMode;

    // ── 白名单滚动列表 ──
    private final List<EditBox> whitelistRows = new ArrayList<>();
    private int wlScroll = 0;

    // ── 左列滚动 ──
    private int leftScroll = 0;

    // ────────────────────────────────────────────
    // 布局常量
    // ────────────────────────────────────────────

    // 底部按钮栏高度（含分隔线上方空白）
    private static final int BOTTOM_BAR_H = 30;
    // 标题区高度
    private static final int TITLE_H = 24;

    // 左列
    private static final int LEFT_X      = 8;
    private static final int LEFT_W      = 248;   // 标签 + 输入框区域总宽
    private static final int LABEL_X     = LEFT_X;
    private static final int FIELD_X     = LEFT_X + 130;
    private static final int FIELD_W     = 110;
    private static final int FIELD_H     = 16;
    private static final int ROW_H       = 22;
    private static final int BTN_W       = LEFT_W;
    private static final int BTN_H       = 18;

    // 右列（白名单）
    private static final int WL_COL_X   = 264;
    private static final int WL_NUM_W   = 14;   // 行号宽度
    private static final int WL_W       = 164;
    private static final int WL_H       = 16;
    private static final int WL_ROW_H   = 20;
    private static final int WL_TITLE_Y = 8;    // 相对于内容区顶部

    // ────────────────────────────────────────────
    // 运行时计算（init 中填充）
    // ────────────────────────────────────────────

    /** 内容区顶部 Y（屏幕绝对坐标） */
    private int contentTop;
    /** 内容区底部 Y（屏幕绝对坐标，底部按钮栏上方分隔线位置） */
    private int contentBottom;
    /** 内容区可用高度 */
    private int contentH;

    /** 左列内容总高度（所有控件展开后） */
    private int leftTotalH;
    /** 白名单内容总高度 */
    private int wlTotalH() { return whitelistRows.size() * WL_ROW_H; }

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable("gui.reflectshield.config.title"));
        this.lastScreen = lastScreen;
    }

    // ════════════════════════════════════════════
    // init
    // ════════════════════════════════════════════

    @Override
    protected void init() {
        currentMode = ModConfig.REFLECT_MODE.get();
        currentFireballMode = ModConfig.FIREBALL_MODE.get();
        leftScroll = 0;
        wlScroll = 0;
        whitelistRows.clear();

        contentTop    = TITLE_H;
        contentBottom = this.height - BOTTOM_BAR_H;
        contentH      = contentBottom - contentTop;

        // ── 左列控件（y 为虚拟坐标，相对于内容区顶部，不含 scroll）──
        int y = 4;

        speedField    = makeField(y, String.valueOf(ModConfig.REFLECT_SPEED_MULTIPLIER.get()), "-?\\d*\\.?\\d*"); y += ROW_H;
        durationField = makeField(y, String.valueOf(ModConfig.SHIELD_DURATION_MS.get()),        "\\d*");           y += ROW_H;
        cooldownField = makeField(y, String.valueOf(ModConfig.SHIELD_COOLDOWN_MS.get()),         "\\d*");           y += ROW_H;
        widthField    = makeField(y, String.valueOf(ModConfig.SHIELD_WIDTH.get()),               "-?\\d*\\.?\\d*"); y += ROW_H;
        heightField   = makeField(y, String.valueOf(ModConfig.SHIELD_HEIGHT.get()),              "-?\\d*\\.?\\d*"); y += ROW_H;
        depthField    = makeField(y, String.valueOf(ModConfig.SHIELD_DEPTH.get()),               "-?\\d*\\.?\\d*"); y += ROW_H;
        distanceField = makeField(y, String.valueOf(ModConfig.SHIELD_DISTANCE.get()),            "-?\\d*\\.?\\d*"); y += ROW_H + 6;

        playerHitboxCheckbox = addRenderableWidget(new Checkbox(
                LABEL_X, contentTop + y, BTN_W, FIELD_H,
                Component.translatable("gui.reflectshield.config.player_hitbox"),
                ModConfig.SHIELD_USE_PLAYER_HITBOX.get()
        ));
        y += ROW_H;

        debugCheckbox = addRenderableWidget(new Checkbox(
                LABEL_X, contentTop + y, BTN_W, FIELD_H,
                Component.translatable("gui.reflectshield.config.debug"),
                ModConfig.DEBUG_SHOW_SHIELD.get()
        ));
        y += ROW_H + 4;

        modeButton = addRenderableWidget(Button.builder(getModeComponent(), btn -> {
            currentMode = 1 - currentMode;
            btn.setMessage(getModeComponent());
        }).bounds(LABEL_X, contentTop + y, BTN_W, BTN_H).build());
        y += BTN_H + 4;

        fireballModeButton = addRenderableWidget(Button.builder(getFireballModeComponent(), btn -> {
            currentFireballMode = (currentFireballMode + 1) % 3;
            btn.setMessage(getFireballModeComponent());
        }).bounds(LABEL_X, contentTop + y, BTN_W, BTN_H).build());
        y += BTN_H + 4;

        leftTotalH = y;

        // ── 右列：白名单 ──
        List<? extends String> wl = ModConfig.ITEM_WHITELIST.get();
        for (int i = 0; i < Math.max(1, wl.size()); i++) {
            String val = i < wl.size() ? wl.get(i) : "";
            addWhitelistRow(val);
        }

        // +/- 按钮（固定在右下角，不随滚动移动）
        int wlBtnY = contentBottom - 20;
        addRenderableWidget(Button.builder(
                Component.literal("+"), btn -> onAddRow()
        ).bounds(WL_COL_X, wlBtnY, 20, 16).build());
        addRenderableWidget(Button.builder(
                Component.literal("-"), btn -> onRemoveRow()
        ).bounds(WL_COL_X + 24, wlBtnY, 20, 16).build());

        // ── 底部固定按钮 ──
        int bY = this.height - 22;
        int cx = this.width / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.reflectshield.config.reset"), btn -> resetToDefaults()
        ).bounds(cx - 150, bY, 90, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.reflectshield.config.save"), btn -> { saveConfig(); onClose(); }
        ).bounds(cx - 45, bY, 90, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.reflectshield.config.cancel"), btn -> onClose()
        ).bounds(cx + 60, bY, 90, 20).build());

        applyLeftScroll();
        updateWlPositions();
    }

    // ════════════════════════════════════════════
    // 左列滚动
    // ════════════════════════════════════════════

    private void applyLeftScroll() {
        // 7 个数值框
        EditBox[] fields = { speedField, durationField, cooldownField,
                widthField, heightField, depthField, distanceField };
        int y = 4;
        for (EditBox f : fields) {
            int absY = contentTop + y - leftScroll;
            f.setY(absY);
            boolean vis = absY >= contentTop && absY + FIELD_H <= contentBottom;
            f.active  = vis;
            f.visible = vis;
            y += ROW_H;
        }
        y += 6;

        // 两个 Checkbox（只移动 Y）
        setWidgetY(playerHitboxCheckbox, contentTop + y - leftScroll, contentTop, contentBottom);
        y += ROW_H;
        setWidgetY(debugCheckbox, contentTop + y - leftScroll, contentTop, contentBottom);
        y += ROW_H + 4;

        // 两个模式按钮
        setWidgetY(modeButton, contentTop + y - leftScroll, contentTop, contentBottom);
        y += BTN_H + 4;
        setWidgetY(fireballModeButton, contentTop + y - leftScroll, contentTop, contentBottom);
    }

    private void setWidgetY(net.minecraft.client.gui.components.AbstractWidget w, int absY, int top, int bot) {
        w.setY(absY);
        boolean vis = absY >= top && absY + w.getHeight() <= bot;
        w.active  = vis;
        w.visible = vis;
    }

    // ════════════════════════════════════════════
    // 白名单行管理
    // ════════════════════════════════════════════

    /** 白名单可见高度 = 内容区高度 - 标题行高 - +/- 按钮高度 */
    private int wlVisibleH() {
        return contentH - WL_ROW_H - 20 - 4;
    }

    private void addWhitelistRow(String value) {
        EditBox box = new EditBox(font, WL_COL_X + WL_NUM_W, 0, WL_W, WL_H, Component.empty());
        box.setMaxLength(200);
        box.setValue(value);
        whitelistRows.add(box);
        addRenderableWidget(box);
        updateWlPositions();
    }

    private void onAddRow() {
        addWhitelistRow("");
        wlScroll = Math.max(0, wlTotalH() - wlVisibleH());
        updateWlPositions();
    }

    private void onRemoveRow() {
        if (whitelistRows.isEmpty()) return;
        EditBox last = whitelistRows.remove(whitelistRows.size() - 1);
        removeWidget(last);
        wlScroll = Math.min(wlScroll, Math.max(0, wlTotalH() - wlVisibleH()));
        updateWlPositions();
    }

    private void updateWlPositions() {
        int wlContentTop = contentTop + WL_ROW_H; // 标题行下方
        int wlContentBot = contentBottom - 20 - 4; // +/- 按钮上方
        for (int i = 0; i < whitelistRows.size(); i++) {
            EditBox box = whitelistRows.get(i);
            int rowY = wlContentTop + i * WL_ROW_H - wlScroll;
            box.setY(rowY);
            boolean vis = rowY >= wlContentTop && rowY + WL_H <= wlContentBot;
            box.setFocused(box.isFocused() && vis);
            box.active  = vis;
            box.visible = true; // 渲染由 scissor 裁剪
        }
    }

    // ════════════════════════════════════════════
    // 鼠标滚轮
    // ════════════════════════════════════════════

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (my < contentTop || my > contentBottom) return super.mouseScrolled(mx, my, delta);

        // 右列
        if (mx >= WL_COL_X) {
            int maxS = Math.max(0, wlTotalH() - wlVisibleH());
            wlScroll = (int) Math.max(0, Math.min(maxS, wlScroll - delta * WL_ROW_H));
            updateWlPositions();
            return true;
        }

        // 左列
        if (mx >= LEFT_X && mx <= LEFT_X + LEFT_W) {
            int maxS = Math.max(0, leftTotalH - contentH);
            leftScroll = (int) Math.max(0, Math.min(maxS, leftScroll - delta * ROW_H));
            applyLeftScroll();
            return true;
        }

        return super.mouseScrolled(mx, my, delta);
    }

    // ════════════════════════════════════════════
    // 渲染
    // ════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        // 标题
        g.drawCenteredString(font, this.title, this.width / 2, 7, 0xFFFFFF);

        // 分隔线（内容区与底部按钮栏之间）
        g.fill(4, contentBottom, this.width - 4, contentBottom + 1, 0x88FFFFFF);

        // ── 左列（scissor 裁剪到内容区） ──
        g.enableScissor(LEFT_X, contentTop, LEFT_X + LEFT_W, contentBottom);

        // 标签
        String[] labelKeys = {
            "gui.reflectshield.config.speed",
            "gui.reflectshield.config.duration",
            "gui.reflectshield.config.cooldown",
            "gui.reflectshield.config.width",
            "gui.reflectshield.config.height",
            "gui.reflectshield.config.depth",
            "gui.reflectshield.config.distance",
        };
        int ly = contentTop + 4 + 4 - leftScroll; // +4 居中对齐输入框
        for (String key : labelKeys) {
            if (ly + font.lineHeight >= contentTop && ly <= contentBottom)
                g.drawString(font, Component.translatable(key), LABEL_X, ly, 0xE0E0E0);
            ly += ROW_H;
        }

        g.disableScissor();

        // 左列控件（Checkbox / Button 自身已在 applyLeftScroll 设置 visible=false，无需再 scissor）
        // 数值框需要 scissor（EditBox.render 自己不裁剪）
        g.enableScissor(LEFT_X, contentTop, LEFT_X + LEFT_W, contentBottom);
        for (var w : this.renderables) {
            if (w == playerHitboxCheckbox || w == debugCheckbox
                    || w == modeButton || w == fireballModeButton) continue;
            if (whitelistRows.contains(w)) continue;
            // 只渲染左列的 EditBox（数值框）
            if (w instanceof EditBox eb && eb.getX() == FIELD_X) {
                eb.render(g, mx, my, pt);
            }
        }
        g.disableScissor();

        // 左列 Checkbox 和 Button（已由 visible 控制，直接渲染）
        playerHitboxCheckbox.render(g, mx, my, pt);
        debugCheckbox.render(g, mx, my, pt);
        modeButton.render(g, mx, my, pt);
        fireballModeButton.render(g, mx, my, pt);

        // ── 右列（白名单） ──
        int wlContentTop = contentTop + WL_ROW_H;
        int wlContentBot = contentBottom - 20 - 4;

        // 白名单标题（不随滚动）
        g.drawString(font, Component.translatable("gui.reflectshield.config.whitelist"),
                WL_COL_X + WL_NUM_W, contentTop + 4, 0xFFD700);

        g.enableScissor(WL_COL_X, wlContentTop, WL_COL_X + WL_NUM_W + WL_W + 8, wlContentBot);
        for (int i = 0; i < whitelistRows.size(); i++) {
            EditBox box = whitelistRows.get(i);
            int rowY = box.getY();
            if (rowY + WL_H < wlContentTop || rowY > wlContentBot) continue;
            g.drawString(font, String.valueOf(i + 1), WL_COL_X, rowY + 4, 0x888888);
            box.render(g, mx, my, pt);
        }
        g.disableScissor();

        // 白名单滚动条
        int visH = wlVisibleH();
        int totH = wlTotalH();
        if (totH > visH && visH > 0) {
            int thumbH  = Math.max(10, visH * visH / totH);
            int thumbY  = wlContentTop + (int) ((long) wlScroll * (visH - thumbH) / (totH - visH));
            int scrollX = WL_COL_X + WL_NUM_W + WL_W + 2;
            g.fill(scrollX, wlContentTop, scrollX + 3, wlContentBot, 0x44FFFFFF);
            g.fill(scrollX, thumbY, scrollX + 3, thumbY + thumbH, 0xCCFFFFFF);
        }

        // 底部按钮（+/-, 重置/保存/取消）及其他 widget
        for (var w : this.renderables) {
            if (w instanceof EditBox eb && eb.getX() == FIELD_X) continue; // 已渲染
            if (whitelistRows.contains(w)) continue; // 已渲染
            if (w == playerHitboxCheckbox || w == debugCheckbox
                    || w == modeButton || w == fireballModeButton) continue; // 已渲染
            w.render(g, mx, my, pt);
        }
    }

    // ════════════════════════════════════════════
    // 保存 / 重置
    // ════════════════════════════════════════════

    private void resetToDefaults() {
        speedField.setValue("1.5");
        durationField.setValue("200");
        cooldownField.setValue("500");
        widthField.setValue("1.5");
        heightField.setValue("1.5");
        depthField.setValue("0.3");
        distanceField.setValue("1.2");
        currentMode = 0;
        modeButton.setMessage(getModeComponent());
        currentFireballMode = 1;
        fireballModeButton.setMessage(getFireballModeComponent());

        for (EditBox row : whitelistRows) removeWidget(row);
        whitelistRows.clear();
        wlScroll = 0;
        addWhitelistRow("minecraft:*_sword");
    }

    private void saveConfig() {
        trySetDouble(speedField,    0.1, 10.0,  v -> ModConfig.REFLECT_SPEED_MULTIPLIER.set(v));
        trySetInt   (durationField, 50,  2000,  v -> ModConfig.SHIELD_DURATION_MS.set(v));
        trySetInt   (cooldownField, 0,   10000, v -> ModConfig.SHIELD_COOLDOWN_MS.set(v));
        trySetDouble(widthField,    0.5, 5.0,   v -> ModConfig.SHIELD_WIDTH.set(v));
        trySetDouble(heightField,   0.5, 5.0,   v -> ModConfig.SHIELD_HEIGHT.set(v));
        trySetDouble(depthField,    0.1, 2.0,   v -> ModConfig.SHIELD_DEPTH.set(v));
        trySetDouble(distanceField, 0.5, 5.0,   v -> ModConfig.SHIELD_DISTANCE.set(v));

        ModConfig.REFLECT_MODE.set(currentMode);
        ModConfig.FIREBALL_MODE.set(currentFireballMode);
        ModConfig.DEBUG_SHOW_SHIELD.set(debugCheckbox.selected());
        ModConfig.SHIELD_USE_PLAYER_HITBOX.set(playerHitboxCheckbox.selected());

        List<String> newWl = new ArrayList<>();
        for (EditBox row : whitelistRows) {
            String s = row.getValue().trim();
            if (!s.isEmpty()) newWl.add(s);
        }
        ModConfig.ITEM_WHITELIST.set(newWl);
        ModConfig.SPEC.save();
        ItemMatcher.recompile(newWl);
    }

    // ════════════════════════════════════════════
    // 关闭 / 工具方法
    // ════════════════════════════════════════════

    @Override
    public void onClose() {
        assert this.minecraft != null;
        this.minecraft.setScreen(lastScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private EditBox makeField(int virtualY, String value, String filter) {
        EditBox box = addRenderableWidget(
                new EditBox(font, FIELD_X, contentTop + virtualY, FIELD_W, FIELD_H, Component.empty()));
        box.setValue(value);
        box.setFilter(s -> s.matches(filter));
        return box;
    }

    private void trySetDouble(EditBox box, double min, double max, java.util.function.Consumer<Double> setter) {
        try {
            double v = Double.parseDouble(box.getValue());
            setter.accept(Math.max(min, Math.min(max, v)));
        } catch (NumberFormatException ignored) {}
    }

    private void trySetInt(EditBox box, int min, int max, java.util.function.Consumer<Integer> setter) {
        try {
            int v = Integer.parseInt(box.getValue());
            setter.accept(Math.max(min, Math.min(max, v)));
        } catch (NumberFormatException ignored) {}
    }

    private Component getModeComponent() {
        return currentMode == 0
                ? Component.translatable("gui.reflectshield.config.mode.crosshair")
                : Component.translatable("gui.reflectshield.config.mode.physics");
    }

    private Component getFireballModeComponent() {
        String key = switch (currentFireballMode) {
            case 0 -> "gui.reflectshield.config.fireball_mode.ignore";
            case 1 -> "gui.reflectshield.config.fireball_mode.fix";
            default -> "gui.reflectshield.config.fireball_mode.takeover";
        };
        return Component.translatable(key);
    }
}
