package com.reflectshield.common.reflect;

import com.reflectshield.common.config.ModConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 碰撞盒计算工具。服务端和客户端共用同一套算法，
 * 避免重复代码导致两端行为不一致。
 */
public final class ShieldAABB {

    private ShieldAABB() {}

    /**
     * 根据当前配置计算反弹盾碰撞盒。
     * <p>
     * 如果 SHIELD_USE_PLAYER_HITBOX 为 true，直接返回玩家自身 AABB。
     * 否则以视线方向为法线，在法平面内展开宽×高×深的 OBB，取其 AABB 包围盒。
     *
     * @param player 玩家实体
     * @param eye    眼睛位置（客户端可传插值后的位置）
     * @param look   归一化视线方向
     */
    public static AABB compute(Player player, Vec3 eye, Vec3 look) {
        if (ModConfig.SHIELD_USE_PLAYER_HITBOX.get()) {
            return player.getBoundingBox();
        }

        double W = ModConfig.SHIELD_WIDTH.get();
        double H = ModConfig.SHIELD_HEIGHT.get();
        double D = ModConfig.SHIELD_DEPTH.get();
        double dist = ModConfig.SHIELD_DISTANCE.get();

        Vec3 center = eye.add(look.scale(dist));

        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();
        if (right.lengthSqr() < 1e-6) {
            right = new Vec3(1, 0, 0);
        }
        Vec3 up = right.cross(look).normalize();

        double hw = W / 2.0;
        double hh = H / 2.0;
        double hd = D / 2.0;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int ri = -1; ri <= 1; ri += 2) {
            for (int ui = -1; ui <= 1; ui += 2) {
                for (int di = -1; di <= 1; di += 2) {
                    double vx = center.x + ri * hw * right.x + ui * hh * up.x + di * hd * look.x;
                    double vy = center.y + ri * hw * right.y + ui * hh * up.y + di * hd * look.y;
                    double vz = center.z + ri * hw * right.z + ui * hh * up.z + di * hd * look.z;
                    if (vx < minX) minX = vx;
                    if (vy < minY) minY = vy;
                    if (vz < minZ) minZ = vz;
                    if (vx > maxX) maxX = vx;
                    if (vy > maxY) maxY = vy;
                    if (vz > maxZ) maxZ = vz;
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * 计算 OBB 的 8 个顶点（客户端渲染用）。
     * <p>
     * 如果 SHIELD_USE_PLAYER_HITBOX 为 true，返回玩家自身 AABB 的 8 个顶点。
     */
    public static Vec3[] computeVertices(Player player, Vec3 eye, Vec3 look) {
        if (ModConfig.SHIELD_USE_PLAYER_HITBOX.get()) {
            AABB bb = player.getBoundingBox();
            return new Vec3[]{
                    new Vec3(bb.minX, bb.minY, bb.minZ), new Vec3(bb.minX, bb.minY, bb.maxZ),
                    new Vec3(bb.minX, bb.maxY, bb.minZ), new Vec3(bb.minX, bb.maxY, bb.maxZ),
                    new Vec3(bb.maxX, bb.minY, bb.minZ), new Vec3(bb.maxX, bb.minY, bb.maxZ),
                    new Vec3(bb.maxX, bb.maxY, bb.minZ), new Vec3(bb.maxX, bb.maxY, bb.maxZ),
            };
        }

        double W = ModConfig.SHIELD_WIDTH.get();
        double H = ModConfig.SHIELD_HEIGHT.get();
        double D = ModConfig.SHIELD_DEPTH.get();
        double dist = ModConfig.SHIELD_DISTANCE.get();

        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(look).normalize();

        Vec3 center = eye.add(look.scale(dist));
        Vec3 hwVec = right.scale(W / 2.0);
        Vec3 hhVec = up.scale(H / 2.0);
        Vec3 hdVec = look.scale(D / 2.0);

        Vec3[] v = new Vec3[8];
        int idx = 0;
        for (int ri = -1; ri <= 1; ri += 2)
            for (int ui = -1; ui <= 1; ui += 2)
                for (int di = -1; di <= 1; di += 2)
                    v[idx++] = center.add(hwVec.scale(ri)).add(hhVec.scale(ui)).add(hdVec.scale(di));
        return v;
    }
}
