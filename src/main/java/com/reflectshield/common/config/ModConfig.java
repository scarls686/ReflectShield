package com.reflectshield.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class ModConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    /** 反弹后速度倍率 */
    public static final ForgeConfigSpec.DoubleValue REFLECT_SPEED_MULTIPLIER;
    /** 碰撞盒存在时间（毫秒） */
    public static final ForgeConfigSpec.IntValue SHIELD_DURATION_MS;
    /** 冷却时间（毫秒） */
    public static final ForgeConfigSpec.IntValue SHIELD_COOLDOWN_MS;
    /** 碰撞盒宽度（格） */
    public static final ForgeConfigSpec.DoubleValue SHIELD_WIDTH;
    /** 碰撞盒高度（格） */
    public static final ForgeConfigSpec.DoubleValue SHIELD_HEIGHT;
    /** 碰撞盒深度（格，沿视线方向） */
    public static final ForgeConfigSpec.DoubleValue SHIELD_DEPTH;
    /** 碰撞盒距玩家眼睛的距离（格） */
    public static final ForgeConfigSpec.DoubleValue SHIELD_DISTANCE;
    /** 使用玩家自身碰撞盒作为反弹盾（true时忽略宽高深距离参数） */
    public static final ForgeConfigSpec.BooleanValue SHIELD_USE_PLAYER_HITBOX;
    /**
     * 反弹方向模式：
     * 0 = 准星方向（投掷物沿玩家瞄准方向飞出）
     * 1 = 物理反射（根据入射角度计算反射方向）
     */
    public static final ForgeConfigSpec.IntValue REFLECT_MODE;
    /**
     * 物品白名单（空列表表示不限制）
     * 支持通配符：minecraft:*_sword
     * 支持 NBT：minecraft:arrow{CustomModelData:1}
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_WHITELIST;
    /** 调试模式：显示碰撞盒线框 */
    public static final ForgeConfigSpec.BooleanValue DEBUG_SHOW_SHIELD;
    /**
     * 恶魂大火球处理模式：
     * 0 = 忽略（不反弹大火球，由原版处理）
     * 1 = 修正反弹（本模组反弹，xPower/yPower/zPower 指向反弹方向，使其持续飞行）
     * 2 = 接管（本模组完全接管，阻止原版拳击反弹）
     */
    public static final ForgeConfigSpec.IntValue FIREBALL_MODE;

    static {
        BUILDER.comment("ReflectShield Common Configuration");

        BUILDER.push("reflect");

        REFLECT_SPEED_MULTIPLIER = BUILDER
                .comment("Speed multiplier applied to reflected projectiles. / 反弹后投掷物的速度倍率。")
                .defineInRange("reflectSpeedMultiplier", 1.5, 0.1, 10.0);

        REFLECT_MODE = BUILDER
                .comment("Reflect direction mode: 0 = crosshair direction, 1 = physical reflection. / 反弹方向模式：0=准星方向，1=物理反射。")
                .defineInRange("reflectMode", 0, 0, 1);

        BUILDER.pop();
        BUILDER.push("shield");

        SHIELD_DURATION_MS = BUILDER
                .comment("Duration in milliseconds that the shield collision box exists after pressing the key. / 按键后碰撞盒存在的毫秒数。")
                .defineInRange("shieldDurationMs", 200, 50, 2000);

        SHIELD_COOLDOWN_MS = BUILDER
                .comment("Cooldown in milliseconds before the shield can be activated again. / 再次激活碰撞盒前的冷却毫秒数。")
                .defineInRange("shieldCooldownMs", 500, 0, 10000);

        SHIELD_WIDTH = BUILDER
                .comment("Width of the shield collision box in blocks. / 碰撞盒宽度（格）。")
                .defineInRange("shieldWidth", 1.5, 0.5, 5.0);

        SHIELD_HEIGHT = BUILDER
                .comment("Height of the shield collision box in blocks. / 碰撞盒高度（格）。")
                .defineInRange("shieldHeight", 1.5, 0.5, 5.0);

        SHIELD_DEPTH = BUILDER
                .comment("Depth of the shield collision box along the look direction in blocks. / 碰撞盒沿视线方向的深度（格）。")
                .defineInRange("shieldDepth", 0.3, 0.1, 2.0);

        SHIELD_DISTANCE = BUILDER
                .comment("Distance from the player's eyes to the center of the shield box in blocks. / 碰撞盒中心距玩家眼睛的距离（格）。")
                .defineInRange("shieldDistance", 1.2, 0.5, 5.0);

        SHIELD_USE_PLAYER_HITBOX = BUILDER
                .comment("Use the player's own bounding box as the shield instead of creating a separate box. / 使用玩家自身碰撞盒作为反弹盾，忽略宽高深距离参数。")
                .define("shieldUsePlayerHitbox", false);

        BUILDER.pop();
        BUILDER.push("debug");

        DEBUG_SHOW_SHIELD = BUILDER
                .comment("Show the shield collision box wireframe for debugging. / 显示碰撞盒线框（调试用）。")
                .define("debugShowShield", false);

        BUILDER.pop();
        BUILDER.push("whitelist");

        ITEM_WHITELIST = BUILDER
                .comment(
                        "Item whitelist. Only items matching entries can trigger the reflect shield.",
                        "Empty list = allow all items.",
                        "Supports wildcards: minecraft:*_sword",
                        "Supports NBT: minecraft:arrow{CustomModelData:1}",
                        "物品白名单。只有手持名单中的物品才能触发反弹。空列表=不限制。",
                        "支持通配符：minecraft:*_sword  支持NBT：minecraft:arrow{CustomModelData:1}"
                )
                .defineListAllowEmpty("itemWhitelist", List.of("minecraft:*_sword"), o -> o instanceof String);

        BUILDER.pop();
        BUILDER.push("fireball");

        FIREBALL_MODE = BUILDER
                .comment(
                        "How to handle ghast large fireballs (LargeFireball):",
                        "0 = Ignore   — skip large fireballs, let vanilla punch-reflect handle them",
                        "1 = Fix      — this mod reflects the fireball; sets xPower toward reflect dir so it keeps flying",
                        "2 = Takeover — same as Fix, but also cancels the vanilla punch-reflect via AttackEntityEvent",
                        "处理恶魂大火球的方式：",
                        "0=忽略（由原版处理） 1=修正（本模组反弹，xPower指向反弹方向）",
                        "2=接管（同1，并阻止原版拳击反弹）"
                )
                .defineInRange("fireballMode", 1, 0, 2);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
