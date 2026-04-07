package com.reflectshield.compat.epicfight;

import yesman.epicfight.client.ClientEngine;

/**
 * 客户端 EF 状态查询。
 * <p>
 * 所有对 yesman.epicfight.client.* 的引用都隔离在此处。
 * ClientEventHandler 通过 ModList 检查后才会调用此类，
 * 保证没装 EF 时不会触发 ClassLoader 错误。
 */
public final class EfClientHandler {

    /** 由 PacketEfManaged 同步：当前玩家是否处于 EF 被动技能管理状态 */
    private static boolean managed = false;

    private EfClientHandler() {}

    public static void setManaged(boolean value) {
        managed = value;
    }

    public static boolean isManaged() {
        return managed;
    }

    /**
     * 判断当前本地玩家是否应走 EF 反弹路径（技能已学且处于战斗模式）。
     * <p>
     * 只有 managed=true 且 EF 战斗模式开启时返回 true，
     * 非战斗模式下返回 false，允许按键路径正常工作。
     */
    public static boolean isEfActiveForLocalPlayer() {
        return managed && ClientEngine.getInstance().isEpicFightMode();
    }
}
