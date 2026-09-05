package com.sfm_optimizer.transfer;

import java.util.HashMap;
import java.util.Map;

/** 智能休眠与槽位记忆。按 Manager 键控的独立存储，不污染 SFM 的池化对象。 */
public final class TransferMemoryStore<K> {
    private final Map<K, Long> asleepUntil = new HashMap<>();
    private final Map<K, Integer> lastSlot = new HashMap<>();

    public boolean isAsleep(K key, long now) {
        Long until = asleepUntil.get(key);
        return until != null && now < until;
    }

    public void sleep(K key, long now, long cooldownTicks) {
        asleepUntil.put(key, now + cooldownTicks);
    }

    public void wake(K key) {
        asleepUntil.remove(key);
    }

    public int lastSlot(K key) {
        return lastSlot.getOrDefault(key, -1);
    }

    public void rememberSlot(K key, int slot) {
        lastSlot.put(key, slot);
    }

    public void clear() {
        asleepUntil.clear();
        lastSlot.clear();
    }
}
