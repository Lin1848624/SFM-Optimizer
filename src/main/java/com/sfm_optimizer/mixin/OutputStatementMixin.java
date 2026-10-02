package com.sfm_optimizer.mixin;

import ca.teamdman.sfm.common.program.LimitedInputSlot;
import ca.teamdman.sfm.common.program.LimitedOutputSlot;
import ca.teamdman.sfm.common.program.LimitedOutputSlotObjectPool;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.LabelAccess;
import ca.teamdman.sfml.ast.OutputStatement;
import com.sfm_optimizer.SFMOptimizer;
import com.sfm_optimizer.config.SFMOptimizerConfig;
import com.sfm_optimizer.transfer.EvenSplitMath;
import com.sfm_optimizer.transfer.SlotKey;
import com.sfm_optimizer.transfer.TransferMemoryStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(OutputStatement.class)
public abstract class OutputStatementMixin {
    @Shadow(remap = false)
    public abstract LabelAccess labelAccess();

    @Shadow(remap = false)
    public abstract void gatherSlots(ProgramContext context, Consumer<LimitedOutputSlot<?, ?, ?>> slotConsumer);

    private static final TransferMemoryStore<SlotKey> MEMORY = new TransferMemoryStore<>();
    private static final int KIND_OUTPUT = 1;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void sfmopt$tick(ProgramContext context, CallbackInfo ci) {
        if (!SFMOptimizerConfig.ENABLE_EVEN_SPLIT.get()) return;
        if (!labelAccess().roundRobin().isEnabled()) return; // 非轮询走原生贪心
        if (!canRunEvenSplit(context)) return; // 模拟/检查器路径必须放行给原版
        runEvenSplit(context);
        ci.cancel();
    }

    /**
     * 判断当前上下文能否执行真实的均分传输。
     *
     * <p>SFM 的检查器（如 {@code IncompleteIOProgramLinter#gatherWarnings}）会用
     * {@code ProgramContext#createSimulationContext} 构造一个<strong>没有 manager</strong> 的上下文，
     * 再调用 {@code Program#tick} 来收集警告。原版 {@code OutputStatement#tick} 在这种上下文里
     * 会把语句交给 {@link SimulateExploreAllPathsProgramBehaviour} 后提前返回，
     * 而本 mixin 注入在 HEAD，若不放行就会真的执行传输，并在
     * {@code context.getManager().getLevel()} 处空指针（1.21.1 版对应崩溃见
     * crash-2026-10-02_10.20.00-server.txt，此修复与 1.1.1 分支同步）。
     */
    private static boolean canRunEvenSplit(ProgramContext context) {
        if (context.getManager() == null) return false;
        return !(context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void runEvenSplit(ProgramContext context) {
        long now = context.getManager().getLevel().getGameTime();

        ArrayDeque<LimitedInputSlot> inputs = new ArrayDeque<>();
        for (InputStatement in : context.getInputs()) {
            in.gatherSlots(context, inputs::add);
        }
        if (inputs.isEmpty()) return;

        ArrayDeque<LimitedOutputSlot> outputs = new ArrayDeque<>();
        gatherSlots(context, outputs::add);
        if (outputs.isEmpty()) {
            LimitedOutputSlotObjectPool.release(outputs);
            return;
        }

        Map<ResourceType, List<LimitedInputSlot>> inputsByType = new HashMap<>();
        for (LimitedInputSlot in : inputs) {
            if (!in.isDone()) {
                inputsByType.computeIfAbsent(in.type, k -> new ArrayList<>()).add(in);
            }
        }

        for (Map.Entry<ResourceType, List<LimitedInputSlot>> e : inputsByType.entrySet()) {
            ResourceType type = e.getKey();
            List<LimitedInputSlot> src = e.getValue();

            List<LimitedOutputSlot> dst = new ArrayList<>();
            for (LimitedOutputSlot o : outputs) {
                if (o.isDone() || !o.type.equals(type)) continue;
                if (SFMOptimizerConfig.ENABLE_SLOT_MEMORY.get()) {
                    SlotKey key = keyOf(o);
                    if (key != null && MEMORY.isAsleep(key, now)) continue;
                }
                dst.add(o);
            }
            if (dst.isEmpty()) continue;

            long total = 0;
            for (LimitedInputSlot in : src) total += type.getAmount(in.peekStackInSlot());

            long[] caps = new long[dst.size()];
            long sumCaps = 0;
            for (int i = 0; i < dst.size(); i++) {
                LimitedOutputSlot o = dst.get(i);
                long cur = type.getAmount(o.getStackInSlot());
                long max = type.getMaxStackSizeForSlot(o.handler, o.slot);
                caps[i] = Math.max(0, max - cur);
                sumCaps += caps[i];
            }

            long toMove = Math.min(total, sumCaps);
            long[] targets = EvenSplitMath.evenSplit(toMove, caps);

            for (int i = 0; i < dst.size(); i++) {
                long remaining = targets[i];
                LimitedOutputSlot o = dst.get(i);
                for (LimitedInputSlot in : src) {
                    if (remaining <= 0) break;
                    remaining -= moveUpTo(context, in, o, remaining);
                }

                SlotKey key = keyOf(o);
                if (key == null) continue;
                long after = type.getAmount(o.getStackInSlot());
                long space = type.getMaxStackSizeForSlot(o.handler, o.slot) - after;
                if (space <= 0) {
                    MEMORY.sleep(key, now, SFMOptimizerConfig.SLEEP_COOLDOWN_TICKS.get());
                } else {
                    MEMORY.wake(key);
                    if (SFMOptimizerConfig.ENABLE_SLOT_MEMORY.get()) MEMORY.rememberSlot(key, o.slot);
                }
            }
        }
        LimitedOutputSlotObjectPool.release(outputs);
    }

    /**
     * 为休眠/槽位记忆构建稳定键。
     * SFM 允许某些输出槽没有方向（例如标签指向 Manager 自身 / 内部 buffer），
     * 此时返回 null，调用方应跳过休眠与记忆逻辑（原生 moveTo 不依赖 direction）。
     */
    private static SlotKey keyOf(LimitedOutputSlot o) {
        if (o.direction == null || o.label == null) return null;
        return new SlotKey(o.label.name(), o.pos.asLong(), o.direction.get3DDataValue(), o.slot, KIND_OUTPUT);
    }

    /** 复刻 SFM moveTo 的核心逻辑，但增加 maxAmount 上限并返回实际移动量。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static long moveUpTo(ProgramContext context, LimitedInputSlot source, LimitedOutputSlot dest, long maxAmount) {
        if (!source.type.equals(dest.type)) return 0;
        ResourceType type = source.type;

        Object sourceStack = source.peekStackInSlot();
        if (type.isEmpty(sourceStack)) return 0;
        if (!dest.tracker.matchesStack(sourceStack)) return 0;

        long amount = type.getAmount(sourceStack);
        long promised = source.tracker.getRetentionObligationForSlot(type, sourceStack, source.pos, source.slot);
        amount -= promised;
        long remainingObligation = source.tracker.getRemainingRetentionObligation(type, sourceStack);
        long dedicating = Math.min(remainingObligation, amount);
        amount -= dedicating;
        if (dedicating > 0) {
            source.tracker.trackRetentionObligation(type, sourceStack, source.slot, source.pos, dedicating);
        }
        if (amount <= 0) {
            source.setDone();
            return 0;
        }

        Object potentialRemainder = dest.insert(sourceStack, true);
        long fits = type.getAmountDifference(sourceStack, potentialRemainder);
        if (fits <= 0) return 0;

        amount = Math.min(fits, amount);
        amount = Math.min(amount, dest.tracker.getMaxTransferable(type, sourceStack));
        amount = Math.min(amount, source.tracker.getMaxTransferable(type, sourceStack));
        amount = Math.min(amount, type.getMaxStackSize(sourceStack));
        amount = Math.min(amount, maxAmount);
        if (amount <= 0) return 0;

        Object extracted = source.extract(amount);
        if (type.isEmpty(extracted)) {
            source.setDone();
            return 0;
        }

        Object extractedRemainder = dest.insert(extracted, false);
        long moved = type.getAmountDifference(extracted, extractedRemainder);
        source.tracker.trackTransfer(type, extracted, moved);
        dest.tracker.trackTransfer(type, extracted, moved);

        if (!type.isEmpty(extractedRemainder)) {
            SFMOptimizer.LOGGER.error("sfm_optimizer: resource loss during even split, moved={} remainder={}", moved, extractedRemainder);
        }
        return moved;
    }
}
