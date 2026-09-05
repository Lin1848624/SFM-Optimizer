package com.sfm_optimizer.transfer;

/** 把 total 尽量均分到 n 个桶，每桶不超过对应容量；结果 sum = min(total, sum(capacities))。 */
public final class EvenSplitMath {
    private EvenSplitMath() {}

    public static long[] evenSplit(long total, long[] capacities) {
        int n = capacities.length;
        long[] result = new long[n];
        if (n == 0 || total <= 0) return result;

        long[] cap = capacities.clone();
        boolean[] full = new boolean[n];
        long remaining = total;
        int active = n;

        while (remaining > 0 && active > 0) {
            long level = remaining / active;
            if (level == 0) {
                for (int i = 0; i < n && remaining > 0; i++) {
                    if (!full[i] && result[i] < cap[i]) {
                        result[i]++;
                        remaining--;
                    }
                }
                break;
            }
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                if (full[i]) continue;
                long space = cap[i] - result[i];
                long add = Math.min(level, space);
                if (add > 0) {
                    result[i] += add;
                    remaining -= add;
                    changed = true;
                }
                if (result[i] >= cap[i]) {
                    full[i] = true;
                    active--;
                }
            }
            if (!changed) break;
        }
        return result;
    }
}
