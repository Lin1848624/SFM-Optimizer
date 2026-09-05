package com.sfm_optimizer.mixin;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.RoundRobin;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 轮询模式下不再逐 tick 轮到一个目标，而是返回全部候选位置，
 * 交由 OutputStatementMixin 做数学均分。
 */
@Mixin(RoundRobin.class)
public class RoundRobinMixin {
    @Inject(method = "getPositionsForLabels", at = @At("HEAD"), cancellable = true, remap = false)
    private void sfmopt$allPositions(
            List<Label> labels,
            LabelPositionHolder labelPositionHolder,
            CallbackInfoReturnable<ArrayList<Pair<Label, BlockPos>>> cir
    ) {
        RoundRobin self = (RoundRobin) (Object) this;
        if (!self.isEnabled()) return;

        ArrayList<Pair<Label, BlockPos>> positions = new ArrayList<>();
        for (Label label : labels) {
            for (BlockPos.MutableBlockPos pos : labelPositionHolder.getPositions(label.name()).blockPosIterator()) {
                positions.add(Pair.of(label, pos.immutable()));
            }
        }
        cir.setReturnValue(positions);
    }
}
