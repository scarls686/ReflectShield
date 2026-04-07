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
 * 所有水平布局基于 this.width 做相对计算，适配不同分辨率和 GUI 缩放。
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
    // 固定尺寸常量（行高、间距等，不随屏幕缩放）
    // ────────────────────────────────────────────

    private static final int BOTTOM_BAR_H = 30;
    private static final int TITLE_H      = 24;
    private static final int FIELD_H      = 16;
    private static final int ROW_H        = 22;
    private static final int BTN_H        = 18;
    private static final int WL_NUM_W     = 14;
    private static final int WL_H         = 16;
    private static final int WL_ROW_H     = 20;
    private static final int MARGIN       = 8;
    private static final int COL_GAP      = 8;
    private static final int SCROLLBAR_W  = 6;

    // ────────────────────────────────────────────
    // 运行时布局变量（init 中根据屏幕尺寸计算）
    // ────────────────────────────────────────────

    private int leftX;
    private int leftW;
    private int labelX;
    private int fieldX;
    private int fieldW;
    private int btnW;

    private int wlColX;
    private int wlW;

    /** 内容区顶部 Y */
    private int contentTop;
    /** 内容区底部 Y */
    private int contentBottom;
    /** 内容区可用高度 */
    private int contentH;

    /** 左列内容总高度 */
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

        // ── 计算布局 ──
        contentTop    = TITLE_H;
        contentBottom = this.height - BOTTOM_BAR_H;
        contentH      = contentBottom - contentTop;

        leftX  = MARGIN;
        leftW  = (int) ((this.width - MARGIN * 2 - COL_GAP) * 0.55);
        labelX = leftX;
        fieldW = Math.max(60, (int) (leftW * 0.45));
        fieldX = leftX + leftW - fieldW;
        btnW   = leftW;

        wlColX = leftX + leftW + COL_GAP;
        wlW    = this.width - wlColX - WL_NUM_W - MARGIN - SCROLLBAR_W;
        wlW    = Math.max(60, wlW);

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
                labelX, contentTop + y, btnW, FIELD_H,
                Component.translatable("gui.reflectshield.config.player_hitbox"),
                ModConfig.SHIELD_USE_PLAYER_HITBOX.get()
        ));
        y += ROW_H;

        debugCheckbox = addRenderableWidget(new Checkbox(
                labelX, contentTop + y, btnW, FIELD_H,
                Component.translatable("gui.reflectshield.config.debug"),
                ModConfig.DEBUG_SHOW_SHIELD.get()
        ));
        y += ROW_H + 4;

        modeButton = addRenderableWidget(Button.builder(getModeComponent(), btn -> {
            currentMode = 1 - currentMode;
            btn.setMessage(getModeComponent());
        }).bounds(labelX, contentTop + y, btnW, BTN_H).build());
        y += BTN_H + 4;

        fireballModeButton = addRenderableWidget(Button.builder(getFireballModeComponent(), btn -> {
            currentFireballMode = (currentFireballMode + 1) % 3;
            btn.setMessage(getFireballModeComponent());
        }).bounds(labelX, contentTop + y, btnW, BTN_H).build());
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
        ).bounds(wlColX, wlBtnY, 20, 16).build());
        addRenderableWidget(Button.builder(
                Component.literal("-"), btn -> onRemoveRow()
        ).bounds(wlColX + 24, wlBtnY, 20, 16).build());

        // ── 底部固定按钮 ──
        int bY = this.height - 22;
        int cx = this.width / 2;
        int bottomBtnW = Math.min(90, (this.width - 40) / 3);
        int bottomGap = 15;
        int totalBottomW = bottomBtnW * 3 + bottomGap * 2;
        int bx = cx - totalBottomW / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.reflectshield.config.reset"), btn -> resetToDefaults()
        ).bounds(bx, bY, bottomBtnW, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.reflectshield.config.save"), btn -> { saveConfig(); onClose(); }
        ).bounds(bx + bottomBtnW + bottomGap, bY, bottomBtnW, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.reflectshield.config.cancel"), btn -> onClose()
        ).bounds(bx + (bottomBtnW + bottomGap) * 2, bY, bottomBtnW, 20).build());

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
            f.setX(fieldX);
            f.setWidth(fieldW);
            boolean vis = absY >= contentTop && absY + FIELD_H <= contentBottom;
            f.active  = vis;
            f.visible = vis;
            y += ROW_H;
        }
        y += 6;

        // 两个 Checkbox
        setWidgetY(playerHitboxCheckbox, contentTop + y - leftScroll, contentTop, contentBottom);
        playerHitboxCheckbox.setX(labelX);
        y += ROW_H;
        setWidgetY(debugCheckbox, contentTop + y - leftScroll, contentTop, contentBottom);
        debugCheckbox.setX(labelX);
        y += ROW_H + 4;

        // 两个模式按钮
        setWidgetY(modeButton, contentTop + y - leftScroll, contentTop, contentBottom);
        modeButton.setX(labelX);
        modeButton.setWidth(btnW);
        y += BTN_H + 4;
        setWidgetY(fireballModeButton, contentTop + y - leftScroll, contentTop, contentBottom);
        fireballModeButton.setX(labelX);
        fireballModeButton.setWidth(btnW);
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
        EditBox box = new EditBox(font, wlColX + WL_NUM_W, 0, wlW, WL_H, Component.empty());
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
        int wlContentTop = contentTop + WL_ROW_H;
        int wlContentBot = contentBottom - 20 - 4;
        for (int i = 0; i < whitelistRows.size(); i++) {
            EditBox box = whitelistRows.get(i);
            int rowY = wlContentTop + i * WL_ROW_H - wlScroll;
            box.setY(rowY);
            box.setX(wlColX + WL_NUM_W);
            box.setWidth(wlW);
            boolean vis = rowY >= wlContentTop && rowY + WL_H <= wlContentBot;
            box.setFocused(box.isFocused() && vis);
            box.active  = vis;
            box.visible = true;
        }
    }

    // ════════════════════════════════════════════
    // 鼠标滚轮
    // ════════════════════════════════════════════

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (my < contentTop || my > contentBottom) return super.mouseScrolled(mx, my, delta);

        // 右列
        if (mx >= wlColX) {
            int maxS = Math.max(0, wlTotalH() - wlVisibleH());
            wlScroll = (int) Math.max(0, Math.min(maxS, wlScroll - delta * WL_ROW_H));
            updateWlPositions();
            return true;
        }

        // 左列
        if (mx >= leftX && mx <= leftX + leftW) {
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

        // 分隔线
        g.fill(4, contentBottom, this.width - 4, contentBottom + 1, 0x88FFFFFF);

        // ── 左列标签（scissor 裁剪） ──
        g.enableScissor(leftX, contentTop, leftX + leftW, contentBottom);

        String[] labelKeys = {
            "gui.reflectshield.config.speed",
            "gui.reflectshield.config.duration",
            "gui.reflectshield.config.cooldown",
            "gui.reflectshield.config.width",
            "gui.reflectshield.config.height",
            "gui.reflectshield.config.depth",
            "gui.reflectshield.config.distance",
        };
        int ly = contentTop + 4 + 4 - leftScroll;
        for (String key : labelKeys) {
            if (ly + font.lineHeight >= contentTop && ly <= contentBottom)
                g.drawString(font, Component.translatable(key), labelX, ly, 0xE0E0E0);
            ly += ROW_H;
        }

        g.disableScissor();

        // 左列数值框（scissor 裁剪）
        g.enableScissor(leftX, contentTop, leftX + leftW, contentBottom);
        for (var w : this.renderables) {
            if (w == playerHitboxCheckbox || w == debugCheckbox
                    || w == modeButton || w == fireballModeButton) continue;
            if (whitelistRows.contains(w)) continue;
            if (w instanceof EditBox eb && eb.getX() == fieldX) {
                eb.render(g, mx, my, pt);
            }
        }
        g.disableScissor();

        // 左列 Checkbox 和 Button
        playerHitboxCheckbox.render(g, mx, my, pt);
        debugCheckbox.render(g, mx, my, pt);
        modeButton.render(g, mx, my, pt);
        fireballModeButton.render(g, mx, my, pt);

        // ── 右列（白名单） ──
        int wlContentTop = contentTop + WL_ROW_H;
        int wlContentBot = contentBottom - 20 - 4;

        g.drawString(font, Component.translatable("gui.reflectshield.config.whitelist"),
                wlColX + WL_NUM_W, contentTop + 4, 0xFFD700);

        g.enableScissor(wlColX, wlContentTop, wlColX + WL_NUM_W + wlW + SCROLLBAR_W + 2, wlContentBot);
        for (int i = 0; i < whitelistRows.size(); i++) {
            EditBox box = whitelistRows.get(i);
            int rowY = box.getY();
            if (rowY + WL_H < wlContentTop || rowY > wlContentBot) continue;
            g.drawString(font, String.valueOf(i + 1), wlColX, rowY + 4, 0x888888);
            box.render(g, mx, my, pt);
        }
        g.disableScissor();

        // 白名单滚动条
        int visH = wlVisibleH();
        int totH = wlTotalH();
        if (totH > visH && visH > 0) {
            int thumbH  = Math.max(10, visH * visH / totH);
            int thumbY  = wlContentTop + (int) ((long) wlScroll * (visH - thumbH) / (totH - visH));
            int scrollX = wlColX + WL_NUM_W + wlW + 2;
            g.fill(scrollX, wlContentTop, scrollX + 3, wlContentBot, 0x44FFFFFF);
            g.fill(scrollX, thumbY, scrollX + 3, thumbY + thumbH, 0xCCFFFFFF);
        }

        // 底部按钮及其他 widget
        for (var w : this.renderables) {
            if (w instanceof EditBox eb && eb.getX() == fieldX) continue;
            if (whitelistRows.contains(w)) continue;
            if (w == playerHitboxCheckbox || w == debugCheckbox
                    || w == modeButton || w == fireballModeButton) continue;
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
    public boolean isPauseScreen() { return true; }

    private EditBox makeField(int virtualY, String value, String filter) {
        EditBox box = addRenderableWidget(
                new EditBox(font, fieldX, contentTop + virtualY, fieldW, FIELD_H, Component.empty()));
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
