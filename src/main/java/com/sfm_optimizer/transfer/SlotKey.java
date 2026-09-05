package com.sfm_optimizer.transfer;

/** 槽位身份键：标签 + 位置 + 方向 + 槽索引 + 类别（输入/输出）。 */
public record SlotKey(String label, long pos, int direction, int slot, int kind) {}
