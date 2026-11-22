package org.cn.xiaofan.fixdroopingleafdupe;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

/**
 * 垂滴叶结构判定的工具方法。
 *
 * <p>小型垂滴叶（{@link Material#SMALL_DRIPLEAF}）与大型垂滴叶
 * （{@link Material#BIG_DRIPLEAF}）的方块数据都实现了 {@link Bisected}，
 * 即都带有 {@code half} 属性；正常情况下上下两半必须成对存在：</p>
 * <ul>
 *     <li>{@code half=TOP} 的方块，其正下方必须是同种、且 {@code half=BOTTOM} 的方块；</li>
 *     <li>{@code half=BOTTOM} 的方块，其正上方必须是同种垂滴叶（BOTTOM 为茎的中间/底部，TOP 为顶端）。</li>
 * </ul>
 * 漏洞利用的「只有上半部分、没有下半部分」的畸形垂滴叶会违反上述规则。
 */
public final class DripleafUtil {

    private DripleafUtil() {
    }

    /**
     * 判断该方块类型是否为垂滴叶。
     */
    public static boolean isDripleaf(Material type) {
        return type == Material.SMALL_DRIPLEAF || type == Material.BIG_DRIPLEAF;
    }

    /**
     * 判断给定方块是否为「畸形垂滴叶」（上下半结构不完整）。
     *
     * @param block 待检测方块
     * @return 若该方块是畸形垂滴叶则返回 {@code true}
     */
    public static boolean isMalformed(Block block) {
        Material type = block.getType();
        if (!isDripleaf(type)) {
            return false;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Bisected self)) {
            return false;
        }

        if (self.getHalf() == Bisected.Half.TOP) {
            // 上半：下方必须是同种垂滴叶的下半
            Block below = block.getRelative(0, -1, 0);
            return !(below.getType() == type
                    && below.getBlockData() instanceof Bisected b
                    && b.getHalf() == Bisected.Half.BOTTOM);
        } else {
            // 下半：上方必须是同种垂滴叶（下半=茎的继续，上半=顶端）
            Block above = block.getRelative(0, 1, 0);
            return !(above.getType() == type && above.getBlockData() instanceof Bisected);
        }
    }

    /**
     * 清除畸形方块：若原本含水则保留水，否则置为空气，并触发方块更新以让结构自然修正。
     */
    public static void clear(Block block) {
        Material replacement = block.getBlockData() instanceof Waterlogged w && w.isWaterlogged()
                ? Material.WATER
                : Material.AIR;
        block.setType(replacement, true);
    }
}
