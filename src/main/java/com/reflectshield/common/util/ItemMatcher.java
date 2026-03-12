package com.reflectshield.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 物品白名单匹配工具。
 *
 * <p>白名单字符串格式：
 * <ul>
 *   <li>{@code minecraft:arrow} — 精确 ID 匹配</li>
 *   <li>{@code minecraft:*_sword} — 通配符 ID 匹配（* 匹配任意字符，? 匹配单个字符）</li>
 *   <li>{@code minecraft:arrow{CustomModelData:1}} — ID + NBT 子集匹配</li>
 *   <li>{@code *_sword{Enchantments:[{}]}} — 通配符 + NBT 匹配</li>
 * </ul>
 *
 * <p>NBT 匹配为子集语义：白名单中指定的所有 NBT 键值对必须存在于物品 NBT 中，
 * 但物品可以拥有额外的 NBT 标签。
 *
 * <p>空白名单表示不限制（允许所有物品触发）。
 */
public class ItemMatcher {

    private static final Logger LOGGER = LogManager.getLogger();

    /** 编译后的匹配规则列表 */
    private static List<MatchRule> compiledRules = Collections.emptyList();

    // -----------------------------------------------------------------------
    // 公共 API
    // -----------------------------------------------------------------------

    /**
     * 从配置列表重新编译规则。配置更改时调用。
     *
     * @param whitelist 白名单字符串列表（来自 ModConfig.ITEM_WHITELIST）
     */
    public static synchronized void recompile(List<? extends String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) {
            compiledRules = Collections.emptyList();
            return;
        }

        List<MatchRule> rules = new ArrayList<>(whitelist.size());
        for (String entry : whitelist) {
            if (entry == null || entry.isBlank()) continue;
            MatchRule rule = parseRule(entry.trim());
            if (rule != null) {
                rules.add(rule);
            }
        }
        compiledRules = Collections.unmodifiableList(rules);
        LOGGER.debug("ReflectShield: Compiled {} item whitelist rules", rules.size());
    }

    /**
     * 检查指定物品栈是否匹配任意白名单规则。
     *
     * @param stack 待检查的物品栈
     * @return true = 允许触发，false = 不允许
     */
    public static boolean matches(ItemStack stack) {
        // 空白名单 = 不限制，允许所有物品
        if (compiledRules.isEmpty()) return true;
        if (stack == null || stack.isEmpty()) return false;

        String itemId = getItemId(stack);
        if (itemId == null) return false;

        for (MatchRule rule : compiledRules) {
            if (!rule.idPattern.matcher(itemId).matches()) continue;

            // ID 匹配，再检查 NBT（若有）
            if (rule.nbtFilter == null) return true;

            CompoundTag stackNbt = stack.getTag();
            if (stackNbt == null) continue; // 规则要求 NBT 但物品无 NBT
            if (nbtContains(stackNbt, rule.nbtFilter)) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // 内部实现
    // -----------------------------------------------------------------------

    /** 单条匹配规则 */
    private static class MatchRule {
        final String raw;
        final Pattern idPattern;
        final CompoundTag nbtFilter; // null = 不检查 NBT

        MatchRule(String raw, Pattern idPattern, CompoundTag nbtFilter) {
            this.raw = raw;
            this.idPattern = idPattern;
            this.nbtFilter = nbtFilter;
        }
    }

    /**
     * 解析一条白名单字符串为 MatchRule。
     * 格式：{@code id_pattern[{nbt}]}
     */
    private static MatchRule parseRule(String entry) {
        String idPart;
        CompoundTag nbtFilter = null;

        int braceIdx = entry.indexOf('{');
        if (braceIdx >= 0) {
            idPart = entry.substring(0, braceIdx).trim();
            String nbtPart = entry.substring(braceIdx).trim();
            try {
                nbtFilter = TagParser.parseTag(nbtPart);
            } catch (Exception e) {
                LOGGER.warn("ReflectShield: Failed to parse NBT in whitelist entry '{}': {}", entry, e.getMessage());
                return null;
            }
        } else {
            idPart = entry;
        }

        if (idPart.isEmpty()) {
            LOGGER.warn("ReflectShield: Empty item ID in whitelist entry '{}'", entry);
            return null;
        }

        Pattern idPattern = wildcardToPattern(idPart);
        return new MatchRule(entry, idPattern, nbtFilter);
    }

    /**
     * 将通配符字符串转换为正则表达式 Pattern。
     * {@code *} 匹配任意字符序列，{@code ?} 匹配单个字符。
     */
    private static Pattern wildcardToPattern(String wildcard) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < wildcard.length(); i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                // 转义正则特殊字符
                case '.', '+', '(', ')', '[', ']', '^', '$', '|', '\\', '{', '}' ->
                        sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString());
    }

    /**
     * 检查 stackNbt 是否包含 filter 中所有的键值对（递归子集匹配）。
     *
     * @param stackNbt 物品实际的 NBT
     * @param filter   白名单中要求的 NBT 子集
     * @return true = 包含所有要求的标签
     */
    private static boolean nbtContains(CompoundTag stackNbt, CompoundTag filter) {
        for (String key : filter.getAllKeys()) {
            if (!stackNbt.contains(key)) return false;

            Tag filterTag = filter.get(key);
            Tag stackTag = stackNbt.get(key);

            if (filterTag == null) continue;

            // 类型不同则不匹配
            if (stackTag == null || filterTag.getId() != stackTag.getId()) return false;

            // 递归匹配嵌套 CompoundTag
            if (filterTag instanceof CompoundTag filterCompound) {
                if (!(stackTag instanceof CompoundTag stackCompound)) return false;
                if (!nbtContains(stackCompound, filterCompound)) return false;
            } else {
                // 其他类型直接比较字符串表示（兼容 ListTag 等）
                if (!filterTag.getAsString().equals(stackTag.getAsString())) return false;
            }
        }
        return true;
    }

    /** 获取物品的 ResourceLocation 字符串，如 "minecraft:diamond_sword" */
    private static String getItemId(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? null : key.toString();
    }
}
